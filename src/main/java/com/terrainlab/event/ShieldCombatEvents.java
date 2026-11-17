package com.terrainlab.event;

import com.terrainlab.TerrainLab;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.level.ExplosionKnockbackEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wood-shield behaviour the blocks_attacks component cannot express: knockback from blocked
 * attacks, thorns, autoblock, magic ward, fire immunity, evoker fangs, and the lightness
 * energy drain hook used by PlayerEnergySystem.
 */
public final class ShieldCombatEvents {

    private ShieldCombatEvents() {}

    private static final long AUTO_BLOCK_COOLDOWN_TICKS = 20;

    /** Net push from a fully blocked melee hit in vanilla 1.21.11 (block pull, then hit knockback). */
    private static final double BLOCKED_MELEE_PUSH = 0.15;

    /** Push from a blocked projectile in vanilla 1.21.11. */
    private static final double BLOCKED_PROJECTILE_PUSH = 0.4;

    /** Vertical hop from a blocked hit on the ground in vanilla. */
    private static final double BLOCKED_HOP = 0.4;

    private static final Map<UUID, Long> lastAutoBlockTick =
            new ConcurrentHashMap<>();

    /** Defender -> game time of its last blocked hit; vanilla's knockback that tick is replaced. */
    private static final Map<LivingEntity, Long> lastBlockTick =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Victim -> last non-magic hit that dealt damage; effects that hit carries are not warded. */
    private static final Map<LivingEntity, LandedHit> lastLandedHit =
            Collections.synchronizedMap(new WeakHashMap<>());

    private record LandedHit(long tick, Entity cause, Entity direct) {}

    private static boolean reapplyingEffect;

    private static volatile Map<Item, ShieldStats> statsByItem;

    private static Map<Item, ShieldStats> statsByItem() {
        if (statsByItem == null) {
            synchronized (ShieldCombatEvents.class) {
                if (statsByItem == null) {
                    Map<Item, ShieldStats> map = new IdentityHashMap<>();

                    for (ShieldStats stats : ShieldDefinitions.ALL) {
                        var deferredItem = ModItems.SHIELD_ITEMS.get(stats.id());

                        if (deferredItem == null) {
                            TerrainLab.LOGGER.error(
                                    "Missing shield item registration for ShieldStats ID: {}",
                                    stats.id()
                            );
                            continue;
                        }

                        Item item = deferredItem.get();
                        map.put(item, stats);
                    }

                    statsByItem = map;
                }
            }
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

    private static ShieldStats raisedStats(LivingEntity entity) {
        return entity.isUsingItem() ? statsFor(entity.getUseItem()) : null;
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

    /** Vanilla's knockback from a blocked hit is replaced by the push applied in onShieldBlock. */
    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        LivingEntity entity = event.getEntity();
        Long tick = lastBlockTick.get(entity);

        if (tick != null && tick == entity.level().getGameTime()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        DamageSource source = event.getSource();

        if (source.getDirectEntity() instanceof EvokerFangs fangs) {
            if (tryBlockFangs(entity, fangs)) {
                event.setCanceled(true);
                return;
            }
        } else {
            float warded = magicDamageWard(entity, source);
            if (warded > 0.0f) {
                event.setAmount(event.getAmount() * (1.0f - warded));
                if (warded >= 1.0f) {
                    return;
                }
            }
        }

        if (isBlockingWithShield(entity)) {
            applyFireImmunity(event, entity, source);
            return;
        }

        // A crouching melee attacker slips past autoblock as well as a raised shield.
        if (ShieldBypass.isCrouchBypass(source, entity)) {
            return;
        }

        tryAutoBlock(event, entity, source);
    }

    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        DamageSource source = event.getSource();
        if (event.getHealthDamage() <= 0.0f || isMagicDamageType(source)) {
            return;
        }

        LivingEntity entity = event.getEntity();
        lastLandedHit.put(entity, new LandedHit(
                entity.level().getGameTime(),
                source.getEntity(),
                source.getDirectEntity()
        ));
    }

    /**
     * A raised shield's block replaces vanilla's knockback with a push scaled by the knockback rating,
     * and a blocked melee hit deals thorns damage and shoves the attacker. Autoblocks never reach this
     * event; crouch-bypassed hits are already unblocked by {@link ShieldBypass} at HIGHEST priority.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (!event.getBlocked() || event.getBlockedDamage() <= 0.0f) {
            return;
        }

        LivingEntity blocker = event.getEntity();
        if (blocker.level().isClientSide() || !isBlockingWithShield(blocker)) {
            return;
        }

        DamageSource source = event.getDamageSource();

        // Explosion push is handled in onExplosionKnockback.
        if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypes.THORNS)) {
            return;
        }

        ShieldStats stats = statsFor(blocker.getUseItem());
        if (stats == null) {
            return;
        }

        pushBlocker(blocker, source, stats);
        lastBlockTick.put(blocker, blocker.level().getGameTime());

        if (!(source.getEntity() instanceof LivingEntity attacker)
                || source.getDirectEntity() != attacker
                || attacker == blocker
                || attacker.distanceToSqr(blocker) > 36.0) {
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
                blocker.getZ() - attacker.getZ(),
                attacker.damageSources().mobAttack(blocker),
                event.getBlockedDamage()
        );
        attacker.needsSync = true;
    }

    /** Vanilla 1.21.11's blocked-hit push, scaled by the knockback rating. */
    private static void pushBlocker(LivingEntity blocker, DamageSource source, ShieldStats stats) {
        float scale = stats.knockbackMultiplier();
        if (scale <= 0.0f || source.is(DamageTypeTags.NO_KNOCKBACK)) {
            return;
        }

        double xd;
        double zd;
        double push;

        if (source.getDirectEntity() instanceof Projectile projectile) {
            Vec3 motion = projectile.getDeltaMovement();
            xd = -motion.x;
            zd = -motion.z;
            push = BLOCKED_PROJECTILE_PUSH;
        } else if (source.getSourcePosition() != null) {
            xd = source.getSourcePosition().x - blocker.getX();
            zd = source.getSourcePosition().z - blocker.getZ();
            push = BLOCKED_MELEE_PUSH;
        } else {
            return;
        }

        blocker.knockback(push * scale, xd, zd, source, 0.0f);

        if (blocker.onGround()) {
            double resistance = blocker.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            double hop = Math.min(BLOCKED_HOP, BLOCKED_HOP * scale) * (1.0 - resistance);
            if (hop > 0.0) {
                Vec3 movement = blocker.getDeltaMovement();
                blocker.setDeltaMovement(movement.x, hop, movement.z);
            }
        }
        blocker.needsSync = true;
    }

    /** Evoker fangs are a physical attack: a raised shield blocks them within its cover. */
    private static boolean tryBlockFangs(LivingEntity entity, EvokerFangs fangs) {
        if (!entity.isBlocking()) {
            return false;
        }

        ShieldStats stats = statsFor(entity.getUseItem());
        if (stats == null || !covers(entity, stats, fangs.position())) {
            return false;
        }

        entity.getUseItem().hurtAndBreak(
                ShieldStats.ITEM_DAMAGE_PER_BLOCK,
                entity,
                entity.getUsedItemHand()
        );
        entity.level().playSound(
                null,
                entity.getX(),
                entity.getY(),
                entity.getZ(),
                SoundEvents.SHIELD_BLOCK,
                entity.getSoundSource(),
                1.0F,
                0.8F + entity.getRandom().nextFloat() * 0.4F
        );
        return true;
    }

    /**
     * Extra energy drained this tick by the shield the player is raising.
     * Returns 0 when not raising a wood shield.
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

    /** Ticks of Slow Falling granted per refresh while the bamboo shield is raised. */
    private static final int PARACHUTE_DURATION_TICKS = 5;

    /**
     * Bamboo shield only: while raised it acts as a parachute, giving Slow Falling.
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
                PARACHUTE_DURATION_TICKS,
                0,
                true,
                false,
                true
        ));
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

        if (source.is(DamageTypeTags.BYPASSES_SHIELD)
                && !(source.getDirectEntity() instanceof EvokerFangs)) {
            return;
        }

        boolean physicalDamage = source.getDirectEntity() instanceof EvokerFangs
                || (!isMagicDamageType(source)
                && (source.is(DamageTypeTags.IS_PROJECTILE)
                || source.is(DamageTypeTags.IS_PLAYER_ATTACK)
                || source.getEntity() instanceof LivingEntity));

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
                entity.getX(),
                entity.getY(),
                entity.getZ(),
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

    /**
     * Effects from potions, clouds, dragon's breath and auras are warded by the Magic rating. An
     * effect carried by a hit that landed (a bite, a blade, an arrow) is not.
     */
    @SubscribeEvent
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        if (reapplyingEffect || !(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }

        Entity source = event.getEffectSource();
        if (source == null || source == entity || landedHitFrom(entity, source)) {
            return;
        }

        MobEffectInstance effect = event.getEffectInstance();
        float warded = potionWard(entity);
        if (warded <= 0.0f || (warded < 1.0f && effect.isInfiniteDuration())) {
            return;
        }

        event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);

        int duration = Math.round(effect.getDuration() * (1.0f - warded));
        if (duration <= 0) {
            return;
        }

        reapplyingEffect = true;
        try {
            entity.addEffect(new MobEffectInstance(
                    effect.getEffect(),
                    duration,
                    effect.getAmplifier(),
                    effect.isAmbient(),
                    effect.isVisible(),
                    effect.showIcon()
            ), source);
        } finally {
            reapplyingEffect = false;
        }
    }

    private static boolean landedHitFrom(LivingEntity entity, Entity source) {
        LandedHit hit = lastLandedHit.get(entity);
        return hit != null
                && hit.tick() == entity.level().getGameTime()
                && (hit.cause() == source || hit.direct() == source);
    }

    /**
     * Share of magic damage warded: guardian lasers within cover while raised, and potions, clouds
     * and dragon's breath as {@link #potionWard}.
     */
    private static float magicDamageWard(LivingEntity entity, DamageSource source) {
        Entity direct = source.getDirectEntity();

        if (direct instanceof Guardian guardian && isMagicDamageType(source)) {
            ShieldStats raised = raisedStats(entity);
            return raised != null && covers(entity, raised, guardian.position())
                    ? raised.magicWardFraction()
                    : 0.0f;
        }

        boolean potion = source.is(DamageTypes.DRAGON_BREATH)
                || (isMagicDamageType(source)
                && (direct instanceof AbstractThrownPotion || direct instanceof AreaEffectCloud));

        return potion && source.getEntity() != entity ? potionWard(entity) : 0.0f;
    }

    /** Magic 2-4 ward part or all of it while raised; Magic 4 wards all of it while held. */
    private static float potionWard(LivingEntity entity) {
        for (ItemStack held : new ItemStack[]{
                entity.getMainHandItem(),
                entity.getOffhandItem()
        }) {
            ShieldStats stats = statsFor(held);
            if (stats != null && stats.magicWardWhileLowered()) {
                return 1.0f;
            }
        }

        ShieldStats raised = raisedStats(entity);
        return raised == null ? 0.0f : raised.magicWardFraction();
    }

    /** Knockback rating scales the push of an explosion the raised shield covers. */
    @SubscribeEvent
    public static void onExplosionKnockback(ExplosionKnockbackEvent event) {
        if (!(event.getAffectedEntity() instanceof LivingEntity living)
                || !living.isBlocking()
                || !isBlockingWithShield(living)) {
            return;
        }

        ShieldStats stats = statsFor(living.getUseItem());
        if (stats == null || !covers(living, stats, event.getExplosion().center())) {
            return;
        }

        event.setKnockbackVelocity(event.getKnockbackVelocity().scale(stats.knockbackMultiplier()));
    }

    /**
     * Scale on a direct push from pos (the dragon's wing swipe): the knockback rating while raised and
     * within cover, otherwise 1.
     */
    public static double pushScale(LivingEntity entity, Vec3 pos) {
        if (!entity.isBlocking()) {
            return 1.0;
        }

        ShieldStats stats = statsFor(entity.getUseItem());
        return stats != null && covers(entity, stats, pos) ? stats.knockbackMultiplier() : 1.0;
    }

    /**
     * Whether a raised shield's cover includes pos, measuring the horizontal angle from the head's
     * facing as vanilla's shield blocking does.
     */
    private static boolean covers(LivingEntity entity, ShieldStats stats, Vec3 pos) {
        float halfAngle = stats.coverHalfAngleDegrees();
        if (halfAngle >= 180.0f) {
            return true;
        }
        if (pos == null) {
            return false;
        }

        double dx = pos.x - entity.getX();
        double dz = pos.z - entity.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4) {
            return true;
        }

        double yaw = Math.toRadians(entity.getYHeadRot());
        double facingX = -Math.sin(yaw);
        double facingZ = Math.cos(yaw);
        double dot = (dx * facingX + dz * facingZ) / length;
        double angleDegrees = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));

        return angleDegrees <= halfAngle;
    }

    private static boolean isMagicDamageType(DamageSource source) {
        return source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC);
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
