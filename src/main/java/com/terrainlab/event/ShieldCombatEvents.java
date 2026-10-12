
package com.terrainlab.event;

import com.terrainlab.item.ShieldDefinitions;
import com.terrainlab.item.ShieldStats;
import com.terrainlab.registry.ModItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShieldCombatEvents {

    private ShieldCombatEvents() {}

    private static final long AUTO_BLOCK_COOLDOWN_TICKS = 20;

    private static final Map<UUID, Long> lastAutoBlockTick =
            new ConcurrentHashMap<>();

    private static final Map<UUID, Map<String, long[]>> magicWindows =
            new ConcurrentHashMap<>();

    private static Map<Item, ShieldStats> statsByItem;

    private static Map<Item, ShieldStats> statsByItem() {
        if (statsByItem == null) {
            Map<Item, ShieldStats> map = new IdentityHashMap<>();
            for (ShieldStats stats : ShieldDefinitions.ALL) {
                map.put(ModItems.SHIELD_ITEMS.get(stats.id()).get(), stats);
            }
            statsByItem = map;
        }
        return statsByItem;
    }

    private static ShieldStats statsFor(ItemStack stack) {
        return stack.isEmpty() ? null : statsFor(stack.getItem());
    }

    private static ShieldStats statsFor(Item item) {
        return statsByItem().get(item);
    }

    private static boolean isBlockingWithShield(LivingEntity entity) {
        return entity.isUsingItem() && statsFor(entity.getUseItem()) != null;
    }

    /**
     * Client rendering helper. Suppresses visible fire while a shield with
     * a positive fire-immunity rating is actively raised.
     *
     * This does not clear fire ticks or change actual fire damage.
     */
    public static boolean shouldSuppressBurningVisual(LivingEntity entity) {
        if (!isBlockingWithShield(entity)) {
            return false;
        }

        ShieldStats stats = statsFor(entity.getUseItem());
        return stats != null && stats.fireImmunityFraction() > 0.0f;
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        LivingEntity entity = event.getEntity();

        if (!entity.isUsingItem()) {
            return;
        }

        ShieldStats stats = statsFor(entity.getUseItem());
        if (stats == null) {
            return;
        }

        float multiplier = stats.knockbackMultiplier();
        if (multiplier != 1.0f) {
            event.setStrength(event.getStrength() * multiplier);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        DamageSource source = event.getSource();

        if (isForeignMagicDamage(source, entity)
                && isMagicWardActive(
                entity,
                "damage:" + source.typeHolder().getRegisteredName())) {
            event.setAmount(0.0f);
            return;
        }

        if (isBlockingWithShield(entity)) {
            applyFireImmunity(event, entity, source);
            return;
        }

        tryAutoBlock(event, entity, source);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onShieldBash(LivingShieldBlockEvent event) {
        if (!event.getBlocked() || event.getBlockedDamage() <= 0.0f) {
            return;
        }

        LivingEntity blocker = event.getEntity();
        if (blocker.level().isClientSide() || !isBlockingWithShield(blocker)) {
            return;
        }

        DamageSource source = event.getDamageSource();

        if (!(source.getEntity() instanceof LivingEntity attacker)
                || source.getDirectEntity() != attacker
                || attacker == blocker
                || attacker.distanceToSqr(blocker) > 36.0) {
            return;
        }

        ShieldStats stats = statsFor(blocker.getUseItem());
        if (stats == null) {
            return;
        }

        float strength = stats.bashStrength();
        if (strength <= 0.0f) {
            return;
        }

        attacker.knockback(
                strength,
                blocker.getX() - attacker.getX(),
                blocker.getZ() - attacker.getZ()
        );
        attacker.hurtMarked = true;
    }

    @SubscribeEvent
    public static void onPlayerTickFireImmunity(PlayerTickEvent.Post event) {
        LivingEntity player = event.getEntity();

        if (player.level().isClientSide()
                || player.getRemainingFireTicks() <= 0
                || !isBlockingWithShield(player)) {
            return;
        }

        ShieldStats stats = statsFor(player.getUseItem());
        if (stats != null && stats.fireImmunityFraction() >= 1.0f) {
            player.clearFire();
        }
    }

    private static void applyFireImmunity(
            LivingIncomingDamageEvent event,
            LivingEntity entity,
            DamageSource source) {

        if (!isFireDamage(source)) {
            return;
        }

        ShieldStats stats = statsFor(entity.getUseItem());
        if (stats == null) {
            return;
        }

        float immunity = stats.fireImmunityFraction();
        if (immunity <= 0.0f) {
            return;
        }

        event.setAmount(Math.max(0.0f, event.getAmount() * (1.0f - immunity)));

        if (immunity >= 1.0f) {
            entity.clearFire();
        }
    }

    private static void tryAutoBlock(
            LivingIncomingDamageEvent event,
            LivingEntity entity,
            DamageSource source) {

        if (source.is(DamageTypeTags.BYPASSES_SHIELD)) {
            return;
        }

        boolean physicalDamage = !isMagicDamageType(source)
                && (source.is(DamageTypeTags.IS_PROJECTILE)
                || source.is(DamageTypeTags.IS_PLAYER_ATTACK)
                || source.getEntity() instanceof LivingEntity
                || source.getDirectEntity() instanceof EvokerFangs);

        if (!physicalDamage || isOnAutoBlockCooldown(entity)) {
            return;
        }

        for (InteractionHand hand : InteractionHand.values()) {
            ShieldStats stats = statsFor(entity.getItemInHand(hand));
            if (stats == null) {
                continue;
            }

            float chance = stats.autoBlockChance();
            if (chance <= 0.0f) {
                continue;
            }

            if (entity.getRandom().nextFloat() < chance) {
                event.setAmount(0.0f);
                markAutoBlockUsed(entity);
                showAutoBlock(entity, hand);
                return;
            }
        }
    }

    private static void showAutoBlock(LivingEntity entity, InteractionHand hand) {
        entity.level().playSound(
                null,
                entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.SHIELD_BLOCK,
                entity.getSoundSource(),
                1.0F,
                0.8F + entity.getRandom().nextFloat() * 0.4F
        );

        if (!entity.level().isClientSide()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    entity,
                    new com.terrainlab.network.ShieldAutoBlockPacket(
                            entity.getId(),
                            hand == InteractionHand.MAIN_HAND
                    )
            );
        }
    }

    @SubscribeEvent
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }

        Entity source = event.getEffectSource();
        if (source == null || source == entity) {
            return;
        }

        if (isMagicWardActive(
                entity,
                "effect:" + event.getEffectInstance()
                        .getEffect().getRegisteredName())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    private static boolean isMagicWardActive(
            LivingEntity entity,
            String windowKey) {

        for (ItemStack held : new ItemStack[]{
                entity.getMainHandItem(),
                entity.getOffhandItem()
        }) {
            ShieldStats stats = statsFor(held);
            if (stats != null && stats.magicWardWhileHeld()) {
                return true;
            }
        }

        if (!entity.isUsingItem()) {
            return false;
        }

        ShieldStats raised = statsFor(entity.getUseItem());
        if (raised == null) {
            return false;
        }

        if (raised.magicFullWardWhileRaised()) {
            return true;
        }

        return raised.magicWindowWardWhileRaised()
                && inMagicWindow(entity, windowKey);
    }

    private static boolean inMagicWindow(LivingEntity entity, String key) {
        long now = entity.level().getGameTime();

        Map<String, long[]> windows = magicWindows.computeIfAbsent(
                entity.getUUID(),
                k -> new ConcurrentHashMap<>()
        );

        windows.values().removeIf(
                window -> now - window[1] > ShieldStats.MAGIC_2_SOURCE_RESET_TICKS
        );

        long[] window = windows.get(key);

        if (window == null) {
            window = new long[]{now, now};
            windows.put(key, window);
        } else {
            window[1] = now;
        }

        return now - window[0] < ShieldStats.MAGIC_2_WARD_TICKS;
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        List<Entity> affected = event.getAffectedEntities();
        affected.removeIf(
                entity -> entity instanceof LivingEntity living
                        && shouldExemptFromExplosion(living)
        );
    }

    private static boolean shouldExemptFromExplosion(LivingEntity entity) {
        if (!isBlockingWithShield(entity)) {
            return false;
        }

        ShieldStats stats = statsFor(entity.getUseItem());
        if (stats == null) {
            return false;
        }

        float chance = stats.blastExemptionChance();
        return chance > 0.0f && entity.getRandom().nextFloat() < chance;
    }

    private static boolean isMagicDamageType(DamageSource source) {
        return source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC);
    }

    private static boolean isForeignMagicDamage(
            DamageSource source,
            LivingEntity victim) {

        Entity direct = source.getDirectEntity();

        if (!isMagicDamageType(source)
                || direct == null
                || direct instanceof EvokerFangs) {
            return false;
        }

        return source.getEntity() != victim;
    }

    private static boolean isFireDamage(DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypes.FIREBALL)
                || source.is(DamageTypes.UNATTRIBUTED_FIREBALL);
    }

    private static boolean isOnAutoBlockCooldown(LivingEntity entity) {
        Long last = lastAutoBlockTick.get(entity.getUUID());

        return last != null
                && entity.level().getGameTime() - last < AUTO_BLOCK_COOLDOWN_TICKS;
    }

    private static void markAutoBlockUsed(LivingEntity entity) {
        lastAutoBlockTick.put(
                entity.getUUID(),
                entity.level().getGameTime()
        );
    }
}
