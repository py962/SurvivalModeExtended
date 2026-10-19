
package com.terrainlab.entity;

import com.terrainlab.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Elephant: a rideable Camel variant whose howdah lets the rider sleep on
 * its back via a temporary bed.
 */
public class ElephantEntity extends Camel {

    private static final boolean DEBUG_LOGGING = false;

    private static final float REMOUNT_Y_OFFSET = 4.0F;
    private static final double MOUNTED_SWIM_MIN_RISE = 0.04D;
    // Height of the temporary howdah bed above the elephant's feet. Fixed (not the hitbox height) so
    // the bed stays one block above the howdah, exactly where it was with the old 4.0-tall hitbox.
    private static final int TEMP_BED_HEIGHT = 4;

    // Babies are this fraction of the adult hitbox and model (see ModEntities / ElephantRenderer).
    public static final float BABY_SCALE = 0.45F;
    // Blocks of head and back kept above water when swimming (scaled by age); see getFluidJumpThreshold.
    private static final double SWIM_HEAD_ALLOWANCE = 0.4D;

    // Dash charge: mobs the elephant runs into during a dash are knocked away and hurt, once per dash.
    private static final float CHARGE_DAMAGE = 6.0F;
    private static final double CHARGE_KNOCKBACK = 2.0D;
    // Server ticks after the dash starts during which hits count, on top of the dash itself.
    private static final int CHARGE_WINDOW_TICKS = 15;

    private int chargeTicks = 0;
    private final Set<UUID> chargeHits = new HashSet<>();

    private BlockPos tempBedPos = null;
    private BlockPos tempBedFootPos = null;
    private boolean isSleepingOnElephant = false;
    private UUID sleepingPlayerUUID = null;

    // Temporary bed left behind by a previous session.
    private BlockPos staleBedPos = null;
    private BlockPos staleBedFootPos = null;

    // Position and orientation held while the rider sleeps.
    private double sleepingX;
    private double sleepingY;
    private double sleepingZ;
    private float sleepingYaw;
    private float sleepingPitch;
    private float sleepingBodyYaw;
    private float sleepingHeadYaw;

    public ElephantEntity(EntityType<? extends Camel> entityType, Level level) {
        super(entityType, level);
    }

    // Vanilla Camel brain, but breeding with elephants instead of camels.
    @Override
    protected Brain<?> makeBrain(com.mojang.serialization.Dynamic<?> dynamic) {
        return ElephantAi.makeBrain(this.brainProvider().makeBrain(dynamic));
    }

    /**
     * Camel hard-codes camel sizes for sitting and babies. The elephant model has no sitting pose
     * (it stays standing when the camel AI "sits" it), so sitting keeps the standing box; babies
     * are the adult box scaled by {@link #BABY_SCALE}.
     */
    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        EntityDimensions adult = this.getType().getDimensions();
        return this.isBaby() ? adult.scale(BABY_SCALE) : adult;
    }

    @Override
    public float getAgeScale() {
        return this.isBaby() ? BABY_SCALE : 1.0F;
    }

    /**
     * Floats once water is deeper than just below the eyes, so a swimming elephant shows only its
     * head and the top of its back (adult: 3.4 - 0.4 = 3.0 blocks; the rider sits at 4.0) and wades
     * through anything shallower.
     */
    @Override
    public double getFluidJumpThreshold() {
        return Math.max(0.4D, this.getEyeHeight() - SWIM_HEAD_ALLOWANCE * this.getAgeScale());
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isBaby() ? ModSounds.ELEPHANT_BABY_AMBIENT.get() : ModSounds.ELEPHANT_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return this.isBaby() ? ModSounds.ELEPHANT_BABY_HURT.get() : ModSounds.ELEPHANT_HURT.get();
    }

    // Trumpet when the dash (charge) starts; played by Camel#handleStartJump.
    @Override
    protected SoundEvent getDashingSound() {
        return ModSounds.ELEPHANT_TRUMPET.get();
    }

    @Override
    public void handleStartJump(int jumpScale) {
        super.handleStartJump(jumpScale);
        this.chargeTicks = CHARGE_WINDOW_TICKS;
        this.chargeHits.clear();
    }

    // Food: sugar cane and melon slices heal and grow babies; hay bales also breed.

    @Override
    public boolean isFood(ItemStack itemStack) {
        return itemStack.is(Items.SUGAR_CANE) || itemStack.is(Items.MELON_SLICE) || itemStack.is(Items.HAY_BLOCK);
    }

    @Override
    protected boolean handleEating(Player player, ItemStack itemStack) {
        if (!this.isFood(itemStack)) {
            return false;
        }

        boolean hay = itemStack.is(Items.HAY_BLOCK);
        float heal = hay ? 10.0F : 2.0F;
        int ageUpSeconds = hay ? 90 : 10;

        boolean couldHeal = this.getHealth() < this.getMaxHealth();
        if (couldHeal) {
            this.heal(heal);
        }

        boolean couldSetInLove = hay && this.isTamed() && this.getAge() == 0 && this.canFallInLove();
        if (couldSetInLove) {
            this.setInLove(player);
        }

        boolean couldAgeUp = this.isBaby();
        if (couldAgeUp) {
            this.level().addParticle(ParticleTypes.HAPPY_VILLAGER, this.getRandomX(1.0), this.getRandomY() + 0.5, this.getRandomZ(1.0), 0.0, 0.0, 0.0);
            if (!this.level().isClientSide()) {
                this.ageUp(ageUpSeconds);
            }
        }

        if (!couldHeal && !couldSetInLove && !couldAgeUp) {
            return false;
        }

        if (!this.isSilent()) {
            SoundEvent eatingSound = this.getEatingSound();
            if (eatingSound != null) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), eatingSound, this.getSoundSource(),
                        1.0F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
            }
        }

        this.gameEvent(GameEvent.EAT);
        return true;
    }

    @Override
    public boolean canMate(Animal partner) {
        return partner != this && partner instanceof ElephantEntity && super.canMate(partner);
    }

    @Override
    public @Nullable Camel getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return com.terrainlab.registry.ModEntities.ELEPHANT.get().create(level, EntitySpawnReason.BREEDING);
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.ELEPHANT_DEATH.get();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Camel.createAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.ATTACK_DAMAGE, 20.0)
                .add(Attributes.ATTACK_KNOCKBACK, 2.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public static boolean checkElephantSpawnRules(
            EntityType<ElephantEntity> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {
        return level.getBlockState(pos.below())
                .is(net.minecraft.tags.BlockTags.ANIMALS_SPAWNABLE_ON);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(
            Entity entity,
            EntityDimensions dimensions,
            float scale) {
        float forwardDistance = 1.5F * scale;
        float yawRad = this.getYRot() * (float) (Math.PI / 180.0);

        double forwardX = -net.minecraft.util.Mth.sin(yawRad) * forwardDistance;
        double forwardZ = net.minecraft.util.Mth.cos(yawRad) * forwardDistance;

        return new Vec3(
                forwardX,
                REMOUNT_Y_OFFSET * scale,
                forwardZ
        );
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (isSleepingOnElephant) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }

        super.travel(travelVector);

        // Ridden float: rise while the water is deeper than the swim threshold, so the elephant swims
        // with its head and back out instead of walking the riverbed; shallower water is waded.
        if (this.isVehicle() && this.isInWater()
                && this.getFluidHeight(FluidTags.WATER) > this.getFluidJumpThreshold()) {
            Vec3 delta = this.getDeltaMovement();

            if (delta.y < MOUNTED_SWIM_MIN_RISE) {
                this.setDeltaMovement(
                        delta.x,
                        MOUNTED_SWIM_MIN_RISE,
                        delta.z
                );
            }
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide()) {
            return;
        }

        if (this.chargeTicks > 0 || this.isDashing()) {
            this.applyChargeHits((ServerLevel) this.level());
        }
        if (this.chargeTicks > 0) {
            this.chargeTicks--;
        }

        // Clean up any temporary bed saved by a previous session.
        if (staleBedPos != null || staleBedFootPos != null) {
            for (BlockPos stale : new BlockPos[]{staleBedPos, staleBedFootPos}) {
                if (stale != null && level().getBlockState(stale).is(Blocks.RED_BED)) {
                    level().setBlock(
                            stale,
                            Blocks.AIR.defaultBlockState(),
                            3
                    );
                }
            }

            staleBedPos = null;
            staleBedFootPos = null;
        }

        // Keep the elephant's position and orientation fixed while the rider sleeps.
        if (isSleepingOnElephant) {
            this.getNavigation().stop();

            this.setPos(sleepingX, sleepingY, sleepingZ);
            this.setDeltaMovement(Vec3.ZERO);
            this.setYRot(sleepingYaw);
            this.setXRot(sleepingPitch);
            this.setYBodyRot(sleepingBodyYaw);
            this.setYHeadRot(sleepingHeadYaw);
        }

        // Remove the temporary bed when the player stops sleeping.
        if (tempBedPos != null && isSleepingOnElephant) {
            boolean anySleeping = false;

            for (Player player : level().getEntitiesOfClass(
                    Player.class,
                    new AABB(tempBedPos).inflate(3))) {
                if (player.isSleeping()) {
                    anySleeping = true;
                    break;
                }
            }

            if (!anySleeping) {
                removeTempBed();
                isSleepingOnElephant = false;

                if (sleepingPlayerUUID != null
                        && level() instanceof ServerLevel serverLevel) {
                    Entity sleeper = serverLevel.getEntity(sleepingPlayerUUID);

                    if (sleeper instanceof ServerPlayer serverPlayer) {
                        serverPlayer.snapTo(
                                this.getX(),
                                this.getY() + REMOUNT_Y_OFFSET,
                                this.getZ(),
                                serverPlayer.getYRot(),
                                serverPlayer.getXRot()
                        );

                        serverPlayer.startRiding(this, true, false);
                    }

                    sleepingPlayerUUID = null;
                }
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);

        if (tempBedPos != null) {
            output.putLong("TempBedHead", tempBedPos.asLong());
        }

        if (tempBedFootPos != null) {
            output.putLong("TempBedFoot", tempBedFootPos.asLong());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);

        long head = input.getLongOr("TempBedHead", Long.MIN_VALUE);
        long foot = input.getLongOr("TempBedFoot", Long.MIN_VALUE);

        staleBedPos = head == Long.MIN_VALUE ? null : BlockPos.of(head);
        staleBedFootPos = foot == Long.MIN_VALUE ? null : BlockPos.of(foot);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            removeTempBed();
        }

        super.remove(reason);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        boolean emptyHand = player.getItemInHand(hand).isEmpty();

        boolean howdahEquipped = this.getItemBySlot(EquipmentSlot.SADDLE)
                .is(com.terrainlab.registry.ModItems.HOWDAH.get());

        boolean isVehicle = this.isVehicle();
        boolean passengerMatch = this.getPassengers().contains(player);

        boolean wantsToSleep = emptyHand
                && howdahEquipped
                && isVehicle
                && passengerMatch;

        if (wantsToSleep) {
            if (!this.level().isClientSide()
                    && player instanceof ServerPlayer serverPlayer) {
                attemptHowdahSleep(serverPlayer);
            }

            return InteractionResult.SUCCESS;
        }

        return super.mobInteract(player, hand);
    }

    /**
     * Dash charge: every living entity the elephant touches during the dash is knocked away from it
     * and hurt once. Skips the elephant's own passengers, the rider's pets, armor stands, spectators
     * and creative players, and players the rider is not allowed to harm (PvP off or same team).
     */
    private void applyChargeHits(ServerLevel level) {
        Entity rider = this.getControllingPassenger();
        AABB hitBox = this.getBoundingBox().inflate(0.5D, 0.0D, 0.5D);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, hitBox)) {
            if (target == this || !target.isAlive() || target instanceof ArmorStand) continue;
            if (target.getRootVehicle() == this.getRootVehicle()) continue; // passengers, incl. the rider
            if (this.chargeHits.contains(target.getUUID())) continue;
            if (target instanceof OwnableEntity pet && rider != null && pet.getOwner() == rider) continue;
            if (target instanceof Player player) {
                if (player.isSpectator() || player.isCreative()) continue;
                if (rider instanceof Player riderPlayer && !riderPlayer.canHarmPlayer(player)) continue;
            }

            this.chargeHits.add(target.getUUID());
            DamageSource source = this.damageSources().mobAttack(this);
            target.hurtServer(level, source, CHARGE_DAMAGE);
            target.knockback(CHARGE_KNOCKBACK, this.getX() - target.getX(), this.getZ() - target.getZ());
        }
    }

    /**
     * Server-side entry point for a howdah sleep request.
     */
    public void attemptHowdahSleep(ServerPlayer player) {
        boolean howdahEquipped = this.getItemBySlot(EquipmentSlot.SADDLE)
                .is(com.terrainlab.registry.ModItems.HOWDAH.get());

        boolean isVehicle = this.isVehicle();
        boolean passengerMatch = this.getPassengers().contains(player);

        if (!howdahEquipped || !isVehicle || !passengerMatch) {
            return;
        }

        if (isSleepingOnElephant) {
            player.displayClientMessage(
                    Component.literal("Someone is already sleeping."),
                    true
            );
            return;
        }

        // Sleeping in water or lava is not allowed.
        if (this.isInWater() || this.isInLava()) {
            player.displayClientMessage(
                    Component.literal("The elephant must be out of the water to sleep."),
                    true
            );
            return;
        }

        if (!isStationary()) {
            player.displayClientMessage(
                    Component.literal("The elephant must be still to sleep."),
                    true
            );
            return;
        }

        trySleepPlayer(player);
    }

    private boolean isStationary() {
        Vec3 movement = this.getDeltaMovement();

        return Math.abs(movement.x) < 0.01
                && Math.abs(movement.z) < 0.01
                && Math.abs(movement.y) < 0.01;
    }

    private void trySleepPlayer(ServerPlayer player) {
        Level level = player.level();

        if (level.dimension().equals(Level.NETHER)
                || level.dimension().equals(Level.END)) {
            player.displayClientMessage(
                    Component.literal("You cannot sleep in this dimension."),
                    true
            );
            return;
        }

        long dayTime = level.getDayTime() % 24000;
        boolean isNight = dayTime >= 12000 && dayTime < 24000;

        if (!isNight && !level.isThundering()) {
            player.displayClientMessage(
                    Component.literal("You can only sleep at night."),
                    true
            );
            return;
        }

        // Place the temporary bed directly above the elephant (fixed height, independent of the hitbox).
        BlockPos footPos = BlockPos.containing(
                this.getX(),
                this.getY() + TEMP_BED_HEIGHT + 0.1D,
                this.getZ()
        );

        Direction bedFacing = this.getDirection();
        BlockPos headPos = footPos.relative(bedFacing);

        // Do not overwrite existing blocks or fluids.
        if (!isFreeForTempBed(level, footPos)
                || !isFreeForTempBed(level, headPos)) {
            player.displayClientMessage(
                    Component.literal("There is not enough room above the elephant to sleep."),
                    true
            );
            return;
        }

        BlockState footState = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, bedFacing)
                .setValue(BedBlock.PART, BedPart.FOOT)
                .setValue(BedBlock.OCCUPIED, false);

        BlockState headState = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, bedFacing)
                .setValue(BedBlock.PART, BedPart.HEAD)
                .setValue(BedBlock.OCCUPIED, false);

        level.setBlock(footPos, footState, 3);
        level.setBlock(headPos, headState, 3);

        tempBedPos = headPos;
        tempBedFootPos = footPos;

        var result = player.startSleepInBed(headPos);

        // Preserve the existing fallback for the NOT_SAFE sleep result.
        if (result.left().isPresent()
                && result.left().get() == Player.BedSleepingProblem.NOT_SAFE) {
            level.setBlock(
                    headPos,
                    headState.setValue(BedBlock.OCCUPIED, true),
                    3
            );

            player.startSleeping(headPos);

            result = com.mojang.datafixers.util.Either.right(
                    net.minecraft.util.Unit.INSTANCE
            );
        }

        if (result.left().isPresent()) {
            removeTempBed();
            sleepingPlayerUUID = null;

            player.displayClientMessage(
                    Component.literal(
                            "Cannot sleep here: " + result.left().get()
                    ),
                    true
            );
        } else {
            isSleepingOnElephant = true;
            sleepingPlayerUUID = player.getUUID();

            // Capture the final elephant transform used throughout sleep.
            sleepingX = this.getX();
            sleepingY = this.getY();
            sleepingZ = this.getZ();
            sleepingYaw = this.getYRot();
            sleepingPitch = this.getXRot();
            sleepingBodyYaw = this.yBodyRot;
            sleepingHeadYaw = this.yHeadRot;

            if (level instanceof ServerLevel serverLevel) {
                serverLevel.updateSleepingPlayerList();
            }
        }
    }

    /**
     * The temporary bed may replace air or replaceable plants, but never
     * fluids or non-replaceable blocks.
     */
    private static boolean isFreeForTempBed(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        return state.isAir()
                || (state.canBeReplaced() && state.getFluidState().isEmpty());
    }

    private void removeTempBed() {
        if (tempBedPos != null) {
            if (level().getBlockState(tempBedPos).is(Blocks.RED_BED)) {
                level().setBlock(
                        tempBedPos,
                        Blocks.AIR.defaultBlockState(),
                        3
                );
            }

            tempBedPos = null;
        }

        if (tempBedFootPos != null) {
            if (level().getBlockState(tempBedFootPos).is(Blocks.RED_BED)) {
                level().setBlock(
                        tempBedFootPos,
                        Blocks.AIR.defaultBlockState(),
                        3
                );
            }

            tempBedFootPos = null;
        }
    }
}
