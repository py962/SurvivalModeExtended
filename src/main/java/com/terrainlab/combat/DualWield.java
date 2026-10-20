package com.terrainlab.combat;

import com.terrainlab.TerrainLab;
import com.terrainlab.mixin.LivingEntityAccessor;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Dual wielding: a melee weapon (tag {@code #terrainlab:enchantable/quickstrike_group}) in both
 * hands gives the off hand its own attack cooldown, so the two hands can strike alternately.
 *
 * <p>The main hand is untouched vanilla (left-click, {@code attackStrengthTicker}). The off hand
 * strikes on right-click (see {@code OffhandStrikeInputHandler} and {@code OffhandStrikePacket})
 * and keeps its own ticker in {@link State}, which both logical sides advance every player tick;
 * the client's copy only predicts the HUD indicator, the server's is authoritative.
 *
 * <p>Server strike: the two hand stacks and their hand-slot attribute modifiers are swapped in
 * place for the duration of one vanilla {@link Player#attack} (or a spear stab), with the off-hand
 * ticker substituted for the main-hand one. Everything vanilla and other handlers key off the
 * "weapon used" (attack damage/speed, enchantments, crits, knockback, Cleave, durability) therefore
 * sees the off-hand weapon. The swap never leaves the packet handler, so no equipment change,
 * inventory sync or attribute difference is visible to anyone afterwards. Sweeps are vetoed.
 *
 * <p>Swap trick: swapping hands (F), or switching to a different weapon in either hand, resets
 * both hands' charge.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class DualWield {

    /** Melee weapons that can be dual wielded: swords, axes and spears (shared with Quickstrike). */
    public static final TagKey<Item> MELEE_WEAPONS = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "enchantable/quickstrike_group"));

    /** Vanilla's server-side attack range leeway ({@code handleAttack} uses 3.0). */
    private static final double SERVER_RANGE_BUFFER = 3.0;
    /** Vanilla's server-side minimum-charge tolerance in ticks ({@code cannotAttackWithItem(item, 5)}). */
    private static final int SERVER_CHARGE_TOLERANCE = 5;

    /** Debug logging for the strike flow (client input + server handling). Turn off once tested. */
    public static final boolean DEBUG = true;

    private DualWield() {}

    public static void debug(String msg, Object... args) {
        if (DEBUG) {
            TerrainLab.LOGGER.info("[DUAL_WIELD] " + msg, args);
        }
    }

    /** Per-player off-hand combat state; one per logical side, transient. */
    public static final class State {
        /** Ticks since the last off-hand strike or reset (off-hand twin of attackStrengthTicker). */
        int ticker;
        ItemStack lastMain = ItemStack.EMPTY;
        ItemStack lastOff = ItemStack.EMPTY;
        /** True only while a server-side off-hand strike is executing. */
        boolean striking;

        public int ticker() {
            return ticker;
        }
    }

    public static State state(Player player) {
        return player.getData(ModAttachments.DUAL_WIELD.get());
    }

    // ---------------------------------------------------------------------------------------------
    // Predicates (both sides)
    // ---------------------------------------------------------------------------------------------

    public static boolean isMeleeWeapon(ItemStack stack) {
        return !stack.isEmpty() && stack.is(MELEE_WEAPONS);
    }

    public static boolean isDualWielding(Player player) {
        return isMeleeWeapon(player.getMainHandItem()) && isMeleeWeapon(player.getOffhandItem());
    }

    /**
     * Prediction (HUD only) of whether the main-hand item has its own right-click use, which wins
     * over the off hand exactly as it wins over an off-hand shield (e.g. a spear's charge). The
     * actual decision is vanilla's hand order in startUseItem; see OffhandStrikeInputHandler.
     */
    public static boolean mainHandTakesRightClick(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.isEmpty()) {
            return false;
        }
        var equippable = main.get(DataComponents.EQUIPPABLE);
        return main.getUseAnimation() != ItemUseAnimation.NONE
                || main.getUseDuration(player) > 0
                || main.has(DataComponents.KINETIC_WEAPON)
                || main.has(DataComponents.BLOCKS_ATTACKS)
                || main.has(DataComponents.CONSUMABLE)
                || (equippable != null && equippable.swappable());
    }

    /**
     * Whether the off hand may strike at all (dual wielding). Hand precedence is not part of this:
     * the client only reaches the off-hand pass when the main hand's use did not succeed, the same
     * point an off-hand shield would be raised, and the server rejects strikes while an item is in use.
     */
    public static boolean canOffhandStrike(Player player) {
        return !player.isSpectator()
                && player.isAlive()
                && isDualWielding(player)
                && !player.getOffhandItem().isBroken();
    }

    /**
     * Whether {@code target} can take an off-hand strike: a living thing (or part of one) that a
     * left-click could attack, other than the player, and not a player that PvP rules protect.
     * Villagers, pets, mounts and the like need no special case: the strike only fires where vanilla
     * would use the off-hand item, i.e. after the main hand's interaction with them did not succeed.
     */
    public static boolean isStrikeTarget(Player player, Entity target) {
        Entity entity = target instanceof PartEntity<?> part ? part.getParent() : target;
        if (!(entity instanceof LivingEntity living) || entity == player || !living.isAlive()) {
            return false;
        }
        if (!target.isAttackable() || target.skipAttackInteraction(player)) {
            return false;
        }
        if (entity instanceof Player other && !player.canHarmPlayer(other)) {
            return false;
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Off-hand charge (both sides)
    // ---------------------------------------------------------------------------------------------

    /**
     * Ticks for a full off-hand charge: the attack speed the player would have with the two hand
     * stacks swapped (so the off-hand weapon's own speed, Quickstrike included, plus every
     * non-hand modifier such as Haste or Mining Fatigue).
     */
    public static float offhandAttackDelay(Player player) {
        AttributeInstance live = player.getAttribute(Attributes.ATTACK_SPEED);
        if (live == null) {
            return player.getCurrentItemAttackStrengthDelay();
        }
        AttributeInstance copy = new AttributeInstance(Attributes.ATTACK_SPEED, instance -> {});
        copy.replaceFrom(live);
        ModifierSwap.apply(attr -> attr.is(Attributes.ATTACK_SPEED) ? copy : null,
                player.getMainHandItem(), player.getOffhandItem());
        return (float) (1.0 / copy.getValue() * 20.0);
    }

    /** Off-hand twin of {@link Player#getAttackStrengthScale}. */
    public static float offhandStrengthScale(Player player, float partialTick) {
        return Mth.clamp((state(player).ticker + partialTick) / offhandAttackDelay(player), 0.0F, 1.0F);
    }

    /** Off-hand twin of {@link Player#cannotAttackWithItem}: the weapon's minimum charge (spears need full). */
    public static boolean offhandBelowMinimumCharge(Player player, int toleranceTicks) {
        float required = player.getOffhandItem().getOrDefault(DataComponents.MINIMUM_ATTACK_CHARGE, 0.0F);
        if (required <= 0.0F) {
            return false;
        }
        return (state(player).ticker + toleranceTicks) / offhandAttackDelay(player) < required;
    }

    /** Off-hand strike/swing performed: the off-hand ticker restarts. */
    public static void resetOffhand(Player player) {
        state(player).ticker = 0;
    }

    private static void resetBoth(Player player) {
        state(player).ticker = 0;
        // resetOnly...: leave the item-swap (hand lowering) ticker to vanilla.
        player.resetOnlyAttackStrengthTicker();
    }

    /** "Switched to a different weapon": item type or enchantments differ. Durability wear does not count. */
    private static boolean isDifferentWeapon(ItemStack a, ItemStack b) {
        return !ItemStack.isSameItem(a, b)
                || !a.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
                        .equals(b.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
    }

    // ---------------------------------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------------------------------

    /**
     * Advances the off-hand ticker and applies the anti swap-trick resets, on both sides (after
     * vanilla's own main-hand check in Player.tick, so client prediction matches the server).
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        State state = state(player);
        state.ticker++;

        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        boolean mainChanged = !ItemStack.matches(state.lastMain, main);
        boolean offChanged = !ItemStack.matches(state.lastOff, off);
        if (!mainChanged && !offChanged) {
            return;
        }

        boolean weaponInvolved = isMeleeWeapon(main) || isMeleeWeapon(off)
                || isMeleeWeapon(state.lastMain) || isMeleeWeapon(state.lastOff);
        boolean swapped = mainChanged && offChanged
                && ItemStack.matches(main, state.lastOff) && ItemStack.matches(off, state.lastMain);

        if (swapped) {
            if (weaponInvolved) {
                debug("{}: hands swapped -> both charges reset", side(player));
                resetBoth(player);
            }
        } else {
            if (offChanged && isDifferentWeapon(state.lastOff, off)) {
                debug("{}: off hand {} -> {}, off-hand charge reset", side(player), state.lastOff, off);
                state.ticker = 0;
                if (isMeleeWeapon(state.lastOff) || isMeleeWeapon(off)) {
                    player.resetOnlyAttackStrengthTicker();
                }
            }
            if (mainChanged && isDifferentWeapon(state.lastMain, main)) {
                if (isMeleeWeapon(state.lastMain) || isMeleeWeapon(main)) {
                    debug("{}: main hand {} -> {}, both charges reset", side(player), state.lastMain, main);
                    resetBoth(player);
                }
            }
        }

        if (mainChanged) {
            state.lastMain = main.copy();
        }
        if (offChanged) {
            state.lastOff = off.copy();
        }
    }

    /** Server: pressing swap-hands with a weapon in either hand resets both charges immediately. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSwapHands(LivingSwapItemsEvent.Hands event) {
        if (event.isCanceled() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (isMeleeWeapon(event.getItemSwappedToMainHand()) || isMeleeWeapon(event.getItemSwappedToOffHand())) {
            debug("server: swap-hands key -> both charges reset");
            resetBoth(player);
        }
    }

    private static String side(Player player) {
        return player.level().isClientSide() ? "client" : "server";
    }

    /** Sweeps are a main-hand-only move. */
    @SubscribeEvent
    public static void onSweep(SweepAttackEvent event) {
        if (isOffhandStriking(event.getEntity())) {
            if (event.isSweeping()) {
                debug("server: off-hand strike sweep suppressed");
            }
            event.setSweeping(false);
        }
    }

    /** True while a server-side off-hand strike is running (the hands are swapped during it). */
    public static boolean isOffhandStriking(Player player) {
        return state(player).striking;
    }

    // ---------------------------------------------------------------------------------------------
    // Server strike
    // ---------------------------------------------------------------------------------------------

    /**
     * Handles one off-hand strike request. {@code entityId < 0} is a swing at nothing. Every
     * accepted request restarts the off-hand ticker (the client already did), like a vanilla miss.
     */
    public static void handleStrike(ServerPlayer player, int entityId) {
        if (player.isSpectator() || !player.isAlive() || player.isUsingItem()) {
            debug("server: ignored, spectator/dead/using item ({})", player.getScoreboardName());
            return;
        }
        if (!canOffhandStrike(player)) {
            debug("server: ignored, cannot off-hand strike main={} off={} dual={}",
                    player.getMainHandItem(), player.getOffhandItem(), isDualWielding(player));
            return;
        }
        ServerLevel level = player.level();
        ItemStack off = player.getOffhandItem();
        if (!off.isItemEnabled(level.enabledFeatures())) {
            debug("server: ignored, off-hand item not enabled {}", off);
            return;
        }
        player.resetLastActionTime();

        if (offhandBelowMinimumCharge(player, SERVER_CHARGE_TOLERANCE)) {
            debug("server: ignored, below minimum charge ticker={} delay={}", state(player).ticker,
                    offhandAttackDelay(player));
            return; // Like vanilla: an uncharged spear jab is simply ignored.
        }

        float charge = offhandStrengthScale(player, 0.5F);
        PiercingWeapon piercing = off.get(DataComponents.PIERCING_WEAPON);
        if (piercing != null) {
            debug("server: spear jab with {} charge={}", off, charge);
            SwingAnimationRef swing = new SwingAnimationRef(off);
            runAsMainHand(player, () -> stab(player, piercing));
            swing.play(player);
            return;
        }

        Entity target = entityId >= 0 ? level.getEntityOrPart(entityId) : null;
        SwingAnimationRef swing = new SwingAnimationRef(off);
        if (target == null) {
            debug("server: miss (entityId={}) charge={}", entityId, charge);
            resetOffhand(player);
        } else if (!level.getWorldBorder().isWithinBounds(target.blockPosition())) {
            debug("server: rejected {}, outside world border", target);
            resetOffhand(player);
        } else if (!player.isWithinAttackRange(off, target.getBoundingBox(), SERVER_RANGE_BUFFER)) {
            debug("server: rejected {}, out of range", target);
            resetOffhand(player);
        } else if (!hasClearPath(player, target)) {
            debug("server: rejected {}, no line of sight", target);
            resetOffhand(player);
        } else if (!isStrikeTarget(player, target)) {
            debug("server: rejected {}, not a strike target (dead/unattackable/PvP)", target);
            resetOffhand(player);
        } else {
            float healthBefore = target instanceof LivingEntity living ? living.getHealth() : -1.0F;
            runAsMainHand(player, () -> player.attack(target));
            float healthAfter = target instanceof LivingEntity living ? living.getHealth() : -1.0F;
            debug("server: struck {} with {} charge={} health {} -> {}", target, off, charge, healthBefore, healthAfter);
        }
        swing.play(player);
    }

    /** The off-hand swing, broadcast to everyone tracking (the striker swung locally already). */
    private record SwingAnimationRef(net.minecraft.world.item.component.SwingAnimation animation) {
        SwingAnimationRef(ItemStack weapon) {
            this(weapon.getAttackAnimation());
        }

        void play(ServerPlayer player) {
            player.swing(InteractionHand.OFF_HAND, animation, false);
        }
    }

    /**
     * Line of sight from the eyes to the target's eyes, center or nearest point. Vanilla's attack
     * handler has no such check; this only rejects strikes clearly through a solid wall.
     */
    private static boolean hasClearPath(Player player, Entity target) {
        Vec3 eye = player.getEyePosition();
        AABB box = target.getBoundingBox();
        Vec3 nearest = new Vec3(
                Mth.clamp(eye.x, box.minX, box.maxX),
                Mth.clamp(eye.y, box.minY, box.maxY),
                Mth.clamp(eye.z, box.minZ, box.maxZ));
        for (Vec3 point : new Vec3[] {target.getEyePosition(), box.getCenter(), nearest}) {
            HitResult hit = player.level().clip(
                    new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }

    /** Spear jab with the (swapped-in) off-hand spear: vanilla PiercingWeapon.attack minus its main-hand swing. */
    private static void stab(ServerPlayer player, PiercingWeapon piercing) {
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        AttackRange range = player.getAttackRangeWith(player.getMainHandItem());
        boolean hitSomething = false;
        Collection<EntityHitResult> hits = ProjectileUtil.getHitEntitiesAlong(
                        player, range, e -> PiercingWeapon.canHitEntity(player, e), ClipContext.Block.COLLIDER)
                .map(blockHit -> List.<EntityHitResult>of(), entityHits -> entityHits);
        for (EntityHitResult hit : hits) {
            hitSomething |= player.stabAttack(EquipmentSlot.MAINHAND, hit.getEntity(), damage, true,
                    piercing.dealsKnockback(), piercing.dismounts());
        }
        player.onAttack();
        player.postPiercingAttack();
        if (hitSomething) {
            piercing.makeHitSound(player);
        }
        piercing.makeSound(player);
    }

    /**
     * Runs {@code action} with the off-hand weapon in the main hand: hand stacks swapped directly in
     * the inventory (no equip sounds, events or equipment packets), their hand-slot attribute
     * modifiers swapped, and the off-hand ticker standing in for attackStrengthTicker. Everything
     * is restored before returning; the off-hand ticker ends at 0.
     */
    private static void runAsMainHand(ServerPlayer player, Runnable action) {
        State state = state(player);
        Inventory inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        ItemStack main = inventory.getSelectedItem();
        ItemStack off = inventory.getItem(Inventory.SLOT_OFFHAND);
        LivingEntityAccessor ticker = (LivingEntityAccessor) player;
        int mainTicker = ticker.terrainlab$getAttackStrengthTicker();

        ModifierSwap swap = ModifierSwap.apply(player::getAttribute, main, off);
        inventory.setItem(selected, off);
        inventory.setItem(Inventory.SLOT_OFFHAND, main);
        ticker.terrainlab$setAttackStrengthTicker(state.ticker);
        state.striking = true;
        try {
            action.run();
        } finally {
            state.striking = false;
            ItemStack offAfter = inventory.getItem(selected);           // the off-hand weapon (maybe broken/empty)
            ItemStack mainAfter = inventory.getItem(Inventory.SLOT_OFFHAND);
            inventory.setItem(selected, mainAfter);
            inventory.setItem(Inventory.SLOT_OFFHAND, offAfter);
            swap.revert();
            ticker.terrainlab$setAttackStrengthTicker(mainTicker);
            state.ticker = 0;
        }
    }

    /**
     * Swaps the hand-slot attribute modifiers of two stacks on a set of attribute instances: removes
     * main@MAINHAND and off@OFFHAND, adds off@MAINHAND and main@OFFHAND (what a real hand swap would
     * end with once vanilla's equipment tracking caught up). Ids already taken by something else are
     * left alone, and {@link #revert} restores exactly what was changed.
     */
    private static final class ModifierSwap {
        private record Removed(AttributeInstance instance, AttributeModifier modifier, boolean permanent) {}
        private record Added(AttributeInstance instance, Identifier id) {}

        private final List<Removed> removed = new ArrayList<>();
        private final List<Added> added = new ArrayList<>();

        static ModifierSwap apply(Function<Holder<Attribute>, AttributeInstance> lookup, ItemStack main, ItemStack off) {
            ModifierSwap swap = new ModifierSwap();
            swap.removeAll(lookup, main, EquipmentSlot.MAINHAND);
            swap.removeAll(lookup, off, EquipmentSlot.OFFHAND);
            swap.addAll(lookup, off, EquipmentSlot.MAINHAND);
            swap.addAll(lookup, main, EquipmentSlot.OFFHAND);
            return swap;
        }

        private void removeAll(Function<Holder<Attribute>, AttributeInstance> lookup, ItemStack stack, EquipmentSlot slot) {
            if (stack.isEmpty() || stack.isBroken()) {
                return;
            }
            stack.forEachModifier(slot, (attribute, modifier) -> {
                AttributeInstance instance = lookup.apply(attribute);
                if (instance == null) {
                    return;
                }
                AttributeModifier existing = instance.getModifier(modifier.id());
                if (existing == null) {
                    return;
                }
                boolean permanent = instance.getPermanentModifiers().contains(existing);
                instance.removeModifier(modifier.id());
                removed.add(new Removed(instance, existing, permanent));
            });
        }

        private void addAll(Function<Holder<Attribute>, AttributeInstance> lookup, ItemStack stack, EquipmentSlot slot) {
            if (stack.isEmpty() || stack.isBroken()) {
                return;
            }
            stack.forEachModifier(slot, (attribute, modifier) -> {
                AttributeInstance instance = lookup.apply(attribute);
                if (instance == null || instance.hasModifier(modifier.id())) {
                    return;
                }
                instance.addTransientModifier(modifier);
                added.add(new Added(instance, modifier.id()));
            });
        }

        /**
         * Undoes {@link #apply}. If the off-hand weapon broke during the strike, vanilla already
         * stripped its modifiers; removing a missing id is a no-op.
         */
        void revert() {
            for (Added a : added) {
                a.instance().removeModifier(a.id());
            }
            for (Removed r : removed) {
                if (r.instance().hasModifier(r.modifier().id())) {
                    continue;
                }
                if (r.permanent()) {
                    r.instance().addPermanentModifier(r.modifier());
                } else {
                    r.instance().addTransientModifier(r.modifier());
                }
            }
        }
    }
}
