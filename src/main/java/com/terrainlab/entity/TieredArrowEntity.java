package com.terrainlab.entity;

import com.terrainlab.registry.ModEntities;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Arrow carrying an optional potion tier (tipped, splash or lingering on impact) and a fletching
 * feather type that alters flight: owl (fast, no gravity), parrot (fires a delayed second arrow),
 * vulture (homes toward targets) and goose (fast through water).
 */
public class TieredArrowEntity extends AbstractArrow {

    public enum PotionTier {
        NONE,
        NORMAL,
        SPLASH,
        LINGERING
    }

    public enum FeatherType {
        NORMAL,
        OWL,
        PARROT,
        VULTURE,
        GOOSE
    }

    private static final int PARROT_SECOND_ARROW_DELAY = 5; // ticks
    private static final double PARROT_SECOND_ARROW_OFFSET = 0.15; // blocks, sideways

    private static final double VULTURE_HOMING_RANGE = 16.0; // blocks
    private static final double VULTURE_HOMING_STRENGTH = 0.4;
    private static final float OWL_VELOCITY_MULTIPLIER = 2.0F;
    private static final double GOOSE_WATER_ENTRY_VELOCITY_MULTIPLIER = 2.0;

    private static final EntityDataAccessor<Integer> DATA_FEATHER_TYPE =
            SynchedEntityData.defineId(TieredArrowEntity.class, EntityDataSerializers.INT);

    private PotionTier potionTier = PotionTier.NONE;
    private ItemStack potionSourceStack = ItemStack.EMPTY;
    private ItemStack ammoStack = ItemStack.EMPTY;
    private ItemStack weaponStack = ItemStack.EMPTY;

    private boolean parrotSecondArrowPending;
    private int parrotSecondArrowTimer;
    private Vec3 parrotSecondArrowStartPosition;
    private Vec3 parrotSecondArrowVelocity;

    public TieredArrowEntity(EntityType<? extends TieredArrowEntity> type, Level level) {
        super(type, level);
    }

    public TieredArrowEntity(
            Level level,
            LivingEntity shooter,
            ItemStack pickupStack,
            ItemStack weaponStack
    ) {
        super(ModEntities.TIERED_ARROW.get(), shooter, level, pickupStack, weaponStack);
        this.ammoStack = pickupStack.copyWithCount(1);

        if (weaponStack != null) {
            this.weaponStack = weaponStack.copy();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FEATHER_TYPE, FeatherType.NORMAL.ordinal());
    }

    public void setPotionTier(PotionTier tier, ItemStack potionSourceStack) {
        this.potionTier = tier;
        this.potionSourceStack = potionSourceStack.copy();
    }

    public void setFeatherType(FeatherType type) {
        this.entityData.set(DATA_FEATHER_TYPE, type.ordinal());
    }

    public FeatherType getFeatherType() {
        return FeatherType.values()[this.entityData.get(DATA_FEATHER_TYPE)];
    }

    @Override
    protected double getDefaultGravity() {
        if (this.getFeatherType() == FeatherType.OWL) {
            return 0.0;
        }

        if (this.getFeatherType() == FeatherType.GOOSE && this.isInWater()) {
            return 0.0;
        }

        return super.getDefaultGravity();
    }

    @Override
    public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
        float actualVelocity = this.getFeatherType() == FeatherType.OWL
                ? velocity * OWL_VELOCITY_MULTIPLIER
                : velocity;

        super.shoot(x, y, z, actualVelocity, inaccuracy);

        if (this.getFeatherType() == FeatherType.PARROT
                && this.level() instanceof ServerLevel
                && this.getOwner() instanceof ServerPlayer player) {

            boolean infinity = false;

            if (!this.weaponStack.isEmpty()) {
                Holder<Enchantment> infinityHolder =
                        this.level().registryAccess()
                                .lookupOrThrow(Registries.ENCHANTMENT)
                                .getOrThrow(Enchantments.INFINITY);

                infinity = this.weaponStack.getEnchantmentLevel(infinityHolder) > 0;
            }

            if (player.isCreative()
                    || infinity
                    || consumeOneMatchingAmmo(player.getInventory())) {

                this.parrotSecondArrowStartPosition = this.position();
                this.parrotSecondArrowVelocity = this.getDeltaMovement();
                this.parrotSecondArrowPending = true;
                this.parrotSecondArrowTimer = 0;
            }
        }
    }

    /** Shrinks the first inventory stack matching the fired ammo by one; returns false if none is found. */
    private boolean consumeOneMatchingAmmo(Inventory inventory) {
        if (this.ammoStack.isEmpty()) {
            return false;
        }

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);

            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, this.ammoStack)) {
                stack.shrink(1);
                inventory.setChanged();
                return true;
            }
        }

        return false;
    }

    @Override
    protected float getWaterInertia() {
        return this.getFeatherType() == FeatherType.GOOSE
                ? 1.0F
                : super.getWaterInertia();
    }

    @Override
    public void tick() {
        if (this.parrotSecondArrowPending
                && this.level() instanceof ServerLevel serverLevel) {

            this.parrotSecondArrowTimer++;

            if (this.parrotSecondArrowTimer >= PARROT_SECOND_ARROW_DELAY) {
                this.parrotSecondArrowPending = false;
                spawnParrotSecondArrow(serverLevel);
            }
        }

        boolean wasInWaterBeforeTick = this.isInWater();

        super.tick();

        if (this.getFeatherType() == FeatherType.GOOSE
                && !wasInWaterBeforeTick
                && this.isInWater()
                && !this.isInGround()) {

            Vec3 boosted = this.getDeltaMovement()
                    .scale(GOOSE_WATER_ENTRY_VELOCITY_MULTIPLIER);

            this.setDeltaMovement(boosted);
        }

        if (this.getFeatherType() == FeatherType.VULTURE
                && !this.isInGround()) {

            applyVultureHoming(this.level());
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        // An arrow destroyed before the delay elapses still releases its paid-for follow-up.
        if (reason.shouldDestroy()
                && this.parrotSecondArrowPending
                && this.level() instanceof ServerLevel serverLevel) {

            this.parrotSecondArrowPending = false;
            spawnParrotSecondArrow(serverLevel);
        }

        super.remove(reason);
    }

    private void spawnParrotSecondArrow(ServerLevel serverLevel) {
        if (this.parrotSecondArrowStartPosition == null
                || this.parrotSecondArrowVelocity == null) {
            return;
        }

        TieredArrowEntity secondArrow = new TieredArrowEntity(
                ModEntities.TIERED_ARROW.get(),
                serverLevel
        );

        Vec3 velocity = this.parrotSecondArrowVelocity;

        double horizontalLength = Math.sqrt(
                velocity.x * velocity.x
                        + velocity.z * velocity.z
        );

        Vec3 sideOffset;

        if (horizontalLength > 1.0E-6) {
            sideOffset = new Vec3(
                    -velocity.z / horizontalLength,
                    0.0,
                    velocity.x / horizontalLength
            ).scale(PARROT_SECOND_ARROW_OFFSET);
        } else {
            sideOffset = new Vec3(
                    PARROT_SECOND_ARROW_OFFSET,
                    0.0,
                    0.0
            );
        }

        Vec3 startPosition = this.parrotSecondArrowStartPosition.add(sideOffset);

        secondArrow.setPos(
                startPosition.x,
                startPosition.y,
                startPosition.z
        );

        secondArrow.setDeltaMovement(velocity);

        secondArrow.setYRot(this.getYRot());
        secondArrow.setXRot(this.getXRot());
        secondArrow.yRotO = this.yRotO;
        secondArrow.xRotO = this.xRotO;

        secondArrow.pickup = this.pickup;

        secondArrow.setFeatherType(FeatherType.PARROT);

        if (this.getOwner() != null) {
            secondArrow.setOwner(this.getOwner());
        }

        if (this.potionTier != PotionTier.NONE) {
            secondArrow.setPotionTier(
                    this.potionTier,
                    this.potionSourceStack
            );
        }

        serverLevel.addFreshEntity(secondArrow);
    }

    /** Steers toward the living entity closest to the current flight line, keeping speed constant. */
    private void applyVultureHoming(Level level) {
        Vec3 arrowPos = this.position();
        Vec3 velocity = this.getDeltaMovement();
        double speed = velocity.length();

        if (speed < 1.0E-4) {
            return;
        }

        Vec3 heading = velocity.scale(1.0 / speed);

        LivingEntity target = null;
        double bestPerpDistSq = Double.MAX_VALUE;

        for (LivingEntity candidate : level.getEntitiesOfClass(
                LivingEntity.class,
                this.getBoundingBox().inflate(VULTURE_HOMING_RANGE),
                e -> e.isAlive()
                        && e != this.getOwner()
                        && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand)
        )) {
            Vec3 toCandidate =
                    candidate.getEyePosition().subtract(arrowPos);

            double forwardDist = toCandidate.dot(heading);

            if (forwardDist <= 0.0
                    || forwardDist > VULTURE_HOMING_RANGE) {
                continue;
            }

            double perpDistSq =
                    toCandidate.lengthSqr()
                            - forwardDist * forwardDist;

            if (perpDistSq
                    > VULTURE_HOMING_RANGE * VULTURE_HOMING_RANGE) {
                continue;
            }

            if (perpDistSq < bestPerpDistSq) {
                bestPerpDistSq = perpDistSq;
                target = candidate;
            }
        }

        if (target == null) {
            return;
        }

        Vec3 toTarget =
                target.getEyePosition()
                        .subtract(arrowPos)
                        .normalize();

        Vec3 nudged =
                velocity.add(toTarget.scale(VULTURE_HOMING_STRENGTH))
                        .normalize()
                        .scale(speed);

        this.setDeltaMovement(nudged);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);

        if (this.potionTier == PotionTier.NORMAL
                && this.level() instanceof ServerLevel) {

            if (result.getEntity() instanceof LivingEntity target) {
                PotionContents contents = this.potionSourceStack.getOrDefault(
                        DataComponents.POTION_CONTENTS,
                        PotionContents.EMPTY
                );

                contents.applyToLivingEntity(target, 1.0F);

                if (target instanceof AbstractPiglin piglin
                        && com.terrainlab.compat.PiglinWardUtil.isAwkwardPotion(
                        this.potionSourceStack)) {

                    com.terrainlab.compat.PiglinWardUtil.applyWard(piglin);
                }
            }
        }

        deliverAreaEffect(result);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        deliverAreaEffect(result);
    }

    private void deliverAreaEffect(HitResult result) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (this.potionTier != PotionTier.SPLASH
                && this.potionTier != PotionTier.LINGERING) {
            return;
        }

        Vec3 pos = result.getLocation();

        if (this.potionTier == PotionTier.SPLASH) {
            ThrownSplashPotion splash = new ThrownSplashPotion(
                    serverLevel,
                    pos.x,
                    pos.y,
                    pos.z,
                    this.potionSourceStack
            );

            splash.onHitAsPotion(
                    serverLevel,
                    this.potionSourceStack,
                    result
            );
        } else {
            ThrownLingeringPotion lingering = new ThrownLingeringPotion(
                    serverLevel,
                    pos.x,
                    pos.y,
                    pos.z,
                    this.potionSourceStack
            );

            lingering.onHitAsPotion(
                    serverLevel,
                    this.potionSourceStack,
                    result
            );
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("PotionTier", this.potionTier.name());
        output.store("PotionSource", ItemStack.OPTIONAL_CODEC, this.potionSourceStack);
        output.store("Ammo", ItemStack.OPTIONAL_CODEC, this.ammoStack);
        output.store("TieredWeapon", ItemStack.OPTIONAL_CODEC, this.weaponStack);
        output.putString("FeatherType", this.getFeatherType().name());

        if (this.parrotSecondArrowPending
                && this.parrotSecondArrowStartPosition != null
                && this.parrotSecondArrowVelocity != null) {

            output.putInt("ParrotSecondArrowTimer", this.parrotSecondArrowTimer);
            output.store("ParrotSecondArrowStart", Vec3.CODEC, this.parrotSecondArrowStartPosition);
            output.store("ParrotSecondArrowVelocity", Vec3.CODEC, this.parrotSecondArrowVelocity);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.potionTier = parseEnum(PotionTier.class, input.getStringOr("PotionTier", ""), PotionTier.NONE);
        this.potionSourceStack = input.read("PotionSource", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        this.ammoStack = input.read("Ammo", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        this.weaponStack = input.read("TieredWeapon", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        this.setFeatherType(parseEnum(FeatherType.class, input.getStringOr("FeatherType", ""), FeatherType.NORMAL));

        this.parrotSecondArrowStartPosition = input.read("ParrotSecondArrowStart", Vec3.CODEC).orElse(null);
        this.parrotSecondArrowVelocity = input.read("ParrotSecondArrowVelocity", Vec3.CODEC).orElse(null);
        this.parrotSecondArrowPending = this.parrotSecondArrowStartPosition != null
                && this.parrotSecondArrowVelocity != null;
        this.parrotSecondArrowTimer = input.getIntOr("ParrotSecondArrowTimer", 0);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.ARROW);
    }
}
