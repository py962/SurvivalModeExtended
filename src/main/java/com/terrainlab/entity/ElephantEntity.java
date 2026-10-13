
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
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

import java.util.UUID;

/**
 * Elephant: a rideable Camel variant whose howdah lets the rider sleep on
 * its back via a temporary bed.
 */
public class ElephantEntity extends Camel {

    private static final boolean DEBUG_LOGGING = false;

    private static final float REMOUNT_Y_OFFSET = 4.0F;
    private static final double MOUNTED_SWIM_MIN_RISE = 0.04D;
    private static final int TEMP_BED_HEIGHT = 4;

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

    @Override
    public float getPickRadius() {
        return 3.0F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.ELEPHANT_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.ELEPHANT_HURT.get();
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

        if (this.isVehicle() && this.isInWater() && !this.onGround()) {
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

        // Clean up any temporary bed saved by a previous session.
        if (staleBedPos != null || staleBedFootPos != null) {
            for (BlockPos stale : new BlockPos[]{staleBedPos, staleBedFootPos}) {
                if (stale != null && level().getBlockState(stale).is(Blocks.BED.red())) {
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
            player.sendOverlayMessage(Component.literal("Someone is already sleeping."));
            return;
        }

        // Sleeping in water or lava is not allowed.
        if (this.isInWater() || this.isInLava()) {
            player.sendOverlayMessage(Component.literal("The elephant must be out of the water to sleep."));
            return;
        }

        if (!isStationary()) {
            player.sendOverlayMessage(Component.literal("The elephant must be still to sleep."));
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
            player.sendOverlayMessage(Component.literal("You cannot sleep in this dimension."));
            return;
        }

        long dayTime = level.getOverworldClockTime() % 24000;
        boolean isNight = dayTime >= 12000 && dayTime < 24000;

        if (!isNight && !level.isThundering()) {
            player.sendOverlayMessage(Component.literal("You can only sleep at night."));
            return;
        }

        // Place the temporary bed directly above the elephant.
        BlockPos footPos = BlockPos.containing(
                this.getX(),
                this.getY() + this.getBbHeight() + 0.1D,
                this.getZ()
        );

        Direction bedFacing = this.getDirection();
        BlockPos headPos = footPos.relative(bedFacing);

        // Do not overwrite existing blocks or fluids.
        if (!isFreeForTempBed(level, footPos)
                || !isFreeForTempBed(level, headPos)) {
            player.sendOverlayMessage(Component.literal("There is not enough room above the elephant to sleep."));
            return;
        }

        BlockState footState = Blocks.BED.red().defaultBlockState()
                .setValue(BedBlock.FACING, bedFacing)
                .setValue(BedBlock.PART, BedPart.FOOT)
                .setValue(BedBlock.OCCUPIED, false);

        BlockState headState = Blocks.BED.red().defaultBlockState()
                .setValue(BedBlock.FACING, bedFacing)
                .setValue(BedBlock.PART, BedPart.HEAD)
                .setValue(BedBlock.OCCUPIED, false);

        level.setBlock(footPos, footState, 3);
        level.setBlock(headPos, headState, 3);

        tempBedPos = headPos;
        tempBedFootPos = footPos;

        net.minecraft.world.level.block.AbstractBedBlock bedBlock =
                (net.minecraft.world.level.block.AbstractBedBlock) headState.getBlock();
        var result = player.startSleepInBed(bedBlock, headState, bedBlock.getBedRule(level, headPos), headPos);

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

            player.sendOverlayMessage(Component.literal(
                            "Cannot sleep here: " + result.left().get()
                    ));
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
            if (level().getBlockState(tempBedPos).is(Blocks.BED.red())) {
                level().setBlock(
                        tempBedPos,
                        Blocks.AIR.defaultBlockState(),
                        3
                );
            }

            tempBedPos = null;
        }

        if (tempBedFootPos != null) {
            if (level().getBlockState(tempBedFootPos).is(Blocks.BED.red())) {
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
