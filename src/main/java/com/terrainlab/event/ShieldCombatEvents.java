
package com.terrainlab.event;

import com.terrainlab.item.ShieldDefinitions;
import com.terrainlab.item.ShieldStats;
import com.terrainlab.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.level.ExplosionKnockbackEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wood-shield behaviour the {@code blocks_attacks} component cannot express: knockback taken and
 * dealt, thorns, autoblock, magic ward, fire immunity, directional blast coverage, and the
 * lightness energy drain hook used by PlayerEnergySystem.
 */
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

    /**
     * A raised shield that blocks a melee hit shoves the attacker (knockback rating) and deals thorns
     * damage (thorns rating). Autoblocks never reach this event, so they trigger neither.
     */
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

        // Explosions (a creeper is its own direct source) and thorns (two thorns shields facing each
        // other would otherwise retaliate forever) are not melee hits.
        if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypes.THORNS)) {
            return;
        }

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

        float thorns = stats.thornsDamage();
        if (thorns > 0.0f && blocker.level() instanceof ServerLevel serverLevel) {
            attacker.hurtServer(serverLevel, blocker.damageSources().thorns(blocker), thorns);
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

    /**
     * Extra energy drained this tick by the shield the player is raising (lightness 0-1); 0 when not
     * raising a wood shield. Called from PlayerEnergySystem#tick next to the sprint drain.
     */
    public static float raisedShieldEnergyDrain(ServerPlayer player) {
        if (!player.isUsingItem()) {
            return 0.0f;
        }
        ShieldStats stats = statsFor(player.getUseItem());
        return stats == null ? 0.0f : stats.raisedEnergyDrainPerTick();
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

    /** Ticks of Slow Falling granted per refresh; renewed every tick while the shield is raised. */
    private static final int PARACHUTE_DURATION_TICKS = 5;

    /**
     * Bamboo shield only: while raised it acts as a parachute, giving Slow Falling. The effect is
     * hidden (no particles) and lapses a few ticks after the shield is lowered.
     */
    @SubscribeEvent
    public static void onPlayerTickBambooParachute(PlayerTickEvent.Post event) {
        LivingEntity player = event.getEntity();
        if (player.level().isClientSide()
                || !player.isBlocking()
                || !player.getUseItem().is(ModItems.SHIELD_ITEMS.get("bamboo_shield").get())) {
            return;
        }
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.SLOW_FALLING,
                PARACHUTE_DURATION_TICKS, 0, true, false, true));
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

    /**
     * Explosion damage against a raised wood shield is blocked by blast coverage instead of the
     * blocks_attacks angle (which follows the knockback rating): fully blocked when the explosion
     * lies within {@link ShieldStats#blastCoverageDegrees()} of the facing direction (blast 4: from
     * anywhere, even with no known position), otherwise not blocked at all. Blast 2 therefore matches
     * vanilla's 90 degrees. Runs before {@link #onShieldBash}.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onShieldBlockExplosion(LivingShieldBlockEvent event) {
        DamageSource source = event.getDamageSource();
        if (!source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.BYPASSES_SHIELD)) {
            return;
        }

        LivingEntity blocker = event.getEntity();
        if (blocker.level().isClientSide() || !isBlockingWithShield(blocker)) {
            return;
        }

        ShieldStats stats = statsFor(blocker.getUseItem());
        if (stats == null) {
            return;
        }

        if (blastCovers(blocker, stats, source.getSourcePosition())) {
            event.setBlocked(true);
            event.setBlockedDamage(event.getDamageContainer().getNewDamage());
        } else {
            event.setBlocked(false);
            event.setBlockedDamage(0.0f);
        }
    }

    /** Blast 3+: a raised shield covering the explosion also cancels its knockback. */
    @SubscribeEvent
    public static void onExplosionKnockback(ExplosionKnockbackEvent event) {
        if (!(event.getAffectedEntity() instanceof LivingEntity living)
                || !living.isBlocking()
                || !isBlockingWithShield(living)) {
            return;
        }

        ShieldStats stats = statsFor(living.getUseItem());
        if (stats == null || !stats.blastBlocksKnockback()) {
            return;
        }

        if (blastCovers(living, stats, event.getExplosion().center())) {
            event.setKnockbackVelocity(Vec3.ZERO);
        }
    }

    /**
     * Whether a raised shield covers an explosion at {@code center}, measuring the horizontal angle
     * from the head's facing as vanilla's shield blocking does. A null position counts as behind.
     */
    private static boolean blastCovers(LivingEntity entity, ShieldStats stats, Vec3 center) {
        if (stats.blastBlocksAllDirections()) {
            return true;
        }
        float coverage = stats.blastCoverageDegrees();
        if (coverage <= 0.0f || center == null) {
            return false;
        }

        double dx = center.x - entity.getX();
        double dz = center.z - entity.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);

        double angleDegrees;
        if (length < 1.0E-4) {
            // Centred on the player: vanilla's zero vector gives a 90 degree angle.
            angleDegrees = 90.0;
        } else {
            double yaw = Math.toRadians(entity.getYHeadRot());
            double facingX = -Math.sin(yaw);
            double facingZ = Math.cos(yaw);
            double dot = (dx * facingX + dz * facingZ) / length;
            angleDegrees = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
        }
        return angleDegrees <= coverage;
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
