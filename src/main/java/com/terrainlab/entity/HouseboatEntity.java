package com.terrainlab.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Two-seat boat with a moving overhead light, a temporary bed for sleeping aboard,
 * and special handling while it is carried on a lead by a Happy Ghast.
 */
public class HouseboatEntity extends Boat {

    private static final double SLEEP_SPEED_THRESHOLD = 0.01;

    private BlockPos tempBedPos = null;
    private BlockPos tempBedFootPos = null;
    private boolean isSleepingInBoat = false;
    private BlockPos lastLightPos = null;
    private UUID sleepingPlayerUUID = null;

    // Last tick's rider, used to re-seat a player who is dismounted mid-air while
    // suspended if HouseboatDismountHandler's mount-event cancel is bypassed.
    private UUID lastRiderUUID = null;

    public HouseboatEntity(EntityType<? extends Boat> type, Level level, Supplier<Item> dropItem) {
        super(type, level, dropItem);
    }

    @Override
    protected double rideHeight(EntityDimensions dimensions) {
        return 0.2;
    }

    @Override
    protected int getMaxPassengers() {
        return 2;
    }

    @Override
    protected float getSinglePassengerXOffset() {
        return 0.3F;
    }

    // Full override: AbstractBoat ignores getSinglePassengerXOffset() with two passengers.
    // Pilot (index 0) sits centred and forward; the passenger sits to the side, slightly behind.
    @Override
    protected Vec3 getPassengerAttachmentPoint(net.minecraft.world.entity.Entity entity,
                                               EntityDimensions dimensions, float scale) {
        float forwardOffset = this.getSinglePassengerXOffset();
        float sideOffset = 0.0F;
        if (this.getPassengers().size() > 1) {
            int i = this.getPassengers().indexOf(entity);
            if (i == 0) {
                forwardOffset = 0.3F;
                sideOffset = 0.0F;
            } else {
                forwardOffset = 0.1F;
                sideOffset = 0.3F;
            }
            if (entity instanceof net.minecraft.world.entity.animal.Animal) {
                forwardOffset += 0.2F;
            }
        }
        return new Vec3(sideOffset, this.rideHeight(dimensions), forwardOffset).yRot(-this.getYRot() * (float) (Math.PI / 180.0));
    }

    // Damage is scaled to 10% so stray hits don't break the boat. Creative attacks skip
    // vanilla's instant-destroy branch and use the same scaled accumulation curve.
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean isCreativeAttack = source.getEntity() instanceof Player player
                && player.getAbilities().instabuild;

        if (isCreativeAttack) {
            this.setDamage(this.getDamage() + amount * 1.0F);
            this.markHurt();

            if (this.getDamage() > 40.0F) {
                this.destroy(level, source);
            }
            return true;
        }

        return super.hurtServer(level, source, amount * 0.1F);
    }

    // Pick radius also affects attack raycasts; larger values made swings at nearby
    // blocks hit the boat instead.
    @Override
    public float getPickRadius() {
        return 1.2F;
    }

    // getControllingPassenger() is deliberately not overridden: AbstractBoat.tick() treats
    // a null controller as "nobody driving" and would auto-seat nearby mobs.

    // While suspended, the server owns the boat's position so HappyGhastMixin's snapTo()
    // calls take effect instead of being overridden by the rider's client.
    @Override
    public boolean isClientAuthoritative() {
        if (isSuspendedFromGhast()) return false;
        return super.isClientAuthoritative();
    }

    // Client half of the above: makes the rider's client apply server position updates.
    // This also stops vanilla's paddle packet; HouseboatClientPaddleSyncMixin resends it.
    @Override
    protected boolean isLocalClientAuthoritative() {
        if (isSuspendedFromGhast()) return false;
        return super.isLocalClientAuthoritative();
    }

    // No lead spring physics while suspended; the lead is visual only.
    @Override
    public void closeRangeLeashBehaviour(Entity entity) {
        if (isSuspendedFromGhast()) return;
        super.closeRangeLeashBehaviour(entity);
    }

    @Override
    public boolean checkElasticInteractions(Entity entity,
                                            Leashable.LeashData leashData) {
        if (isSuspendedFromGhast()) {
            leashData.angularMomentum = 0;
            return false;
        }
        return super.checkElasticInteractions(entity, leashData);
    }

    public boolean isSuspendedFromGhast() {
        Leashable.LeashData leashData = getLeashData();
        if (leashData == null) return false;
        if (leashData.leashHolder == null) return false;
        return leashData.leashHolder instanceof HappyGhast;
    }

    // Set by HappyGhastMixin when descent stops. Not derived from onGround(), which
    // Entity#move recomputes every tick and can flicker false, blocking dismount.
    private boolean terrainlab$restingOnSurface = false;

    public void terrainlab$setRestingOnSurface(boolean resting) {
        this.terrainlab$restingOnSurface = resting;
    }

    public boolean isInAir() {
        if (terrainlab$restingOnSurface) return false;
        return !this.onGround() &&
                !this.isInWater() &&
                !this.isInLava();
    }

    @Override
    public void leashTooFarBehaviour() {
        if (isSuspendedFromGhast()) return;
        super.leashTooFarBehaviour();
    }

    @Override
    public double leashSnapDistance() {
        if (isSuspendedFromGhast()) return Double.MAX_VALUE;
        return super.leashSnapDistance();
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide()) return;

        // Re-seat a rider who left the boat mid-air while suspended (see lastRiderUUID).
        Player currentRider = getFirstPassenger() instanceof Player p ? p : null;
        if (lastRiderUUID != null && currentRider == null
                && isSuspendedFromGhast() && isInAir()
                && level() instanceof ServerLevel serverLevel) {
            Entity maybeRider = serverLevel.getEntity(lastRiderUUID);
            // Riding something else means they climbed onto the ghast: the intended mid-air exit.
            if (maybeRider instanceof ServerPlayer sp && sp.isAlive() && !sp.isRemoved()
                    && !sp.isPassenger()) {
                sp.startRiding(this, true, false);
                currentRider = sp;
            }
        }
        lastRiderUUID = currentRider != null ? currentRider.getUUID() : null;

        updateLight();

        if (isSleepingInBoat) {
            this.setDeltaMovement(Vec3.ZERO);
        }

        if (tempBedPos != null && isSleepingInBoat) {
            boolean anySleeping = false;
            for (Player p : level().getEntitiesOfClass(Player.class,
                    new AABB(tempBedPos).inflate(3))) {
                if (p.isSleeping()) {
                    anySleeping = true;
                    break;
                }
            }

            if (!anySleeping) {
                removeTempBed();
                isSleepingInBoat = false;

                if (sleepingPlayerUUID != null && level() instanceof ServerLevel serverLevel) {
                    Entity sleeper = serverLevel.getEntity(sleepingPlayerUUID);
                    if (sleeper instanceof ServerPlayer sp) {
                        sp.snapTo(this.getX(),
                                this.getY() + rideHeight(getDimensions(getPose())),
                                this.getZ(), sp.getYRot(), sp.getXRot());
                        sp.startRiding(this, true, false);
                    }
                    sleepingPlayerUUID = null;
                }
            }
        }

        if (tempBedPos != null && !isSleepingInBoat) {
            removeTempBed();
        }
    }

    private void updateLight() {
        BlockPos currentPos = this.blockPosition().above(3);

        if (!isFreeForLight(currentPos)) {
            currentPos = this.blockPosition().above(2);
            if (!isFreeForLight(currentPos)) {
                currentPos = null;
            }
        }

        if (currentPos != null && currentPos.equals(lastLightPos)) return;

        if (lastLightPos != null) {
            BlockState existing = level().getBlockState(lastLightPos);
            if (existing.is(Blocks.LIGHT)) {
                level().setBlock(lastLightPos, Blocks.AIR.defaultBlockState(), 3);
            }
        }

        if (currentPos == null) {
            lastLightPos = null;
            return;
        }

        BlockState lightState = Blocks.LIGHT.defaultBlockState()
                .setValue(LightBlock.LEVEL, 15)
                .setValue(BlockStateProperties.WATERLOGGED, false);
        level().setBlock(currentPos, lightState, 3);
        lastLightPos = currentPos;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide() && lastLightPos != null) {
            if (level().getBlockState(lastLightPos).is(Blocks.LIGHT)) {
                level().setBlock(lastLightPos, Blocks.AIR.defaultBlockState(), 3);
            }
            lastLightPos = null;
        }
        removeTempBed();
        super.remove(reason);
    }

    // Empty-hand click while already aboard sleeps; shift is reserved for descent/dismount.
    // Empty-hand click from outside mounts; any other click goes to vanilla.
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        boolean alreadyRiding = this.getPassengers().contains(player);
        boolean emptyHand = player.getItemInHand(hand).isEmpty();

        if (alreadyRiding && emptyHand) {
            if (!level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
                if (isSleepingInBoat) {
                    serverPlayer.displayClientMessage(
                            Component.literal("Someone is already sleeping."), true);
                } else {
                    if (isSuspendedFromGhast() || isStationary()) {
                        trySleepPlayer(serverPlayer);
                    } else {
                        serverPlayer.displayClientMessage(
                                Component.literal("The boat must be still to sleep."), true);
                    }
                }
            }
            return InteractionResult.CONSUME;
        }

        if (!emptyHand) {
            return super.interact(player, hand);
        }

        if (!level().isClientSide() && !player.startRiding(this)) {
            return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    private boolean isStationary() {
        Vec3 movement = this.getDeltaMovement();
        return Math.abs(movement.x) < SLEEP_SPEED_THRESHOLD
                && Math.abs(movement.z) < SLEEP_SPEED_THRESHOLD;
    }

    private void trySleepPlayer(ServerPlayer player) {
        Level level = player.level();
        BlockPos boatPos = this.blockPosition();

        if (level.dimension().equals(Level.NETHER) ||
                level.dimension().equals(Level.END)) {
            player.displayClientMessage(
                    Component.literal("You cannot sleep in this dimension."), true);
            return;
        }

        if (!com.terrainlab.season.SolarTime.isBedTime(level)) {
            player.displayClientMessage(
                    Component.literal("You can only sleep at night."), true);
            return;
        }

        BlockPos footPos = boatPos.above();
        BlockPos headPos = boatPos.above().relative(Direction.NORTH);

        if (!isFreeForTempBed(footPos) || !isFreeForTempBed(headPos)) {
            player.displayClientMessage(
                    Component.literal("There is no room to sleep here."), true);
            return;
        }

        BlockState footState = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.NORTH)
                .setValue(BedBlock.PART, BedPart.FOOT)
                .setValue(BedBlock.OCCUPIED, false);

        BlockState headState = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.NORTH)
                .setValue(BedBlock.PART, BedPart.HEAD)
                .setValue(BedBlock.OCCUPIED, false);

        level.setBlock(footPos, footState, 3);
        level.setBlock(headPos, headState, 3);

        tempBedPos = headPos;
        tempBedFootPos = footPos;

        player.snapTo(headPos.getX() + 0.5, headPos.getY(),
                headPos.getZ() + 0.5, player.getYRot(), player.getXRot());

        var result = player.startSleepInBed(headPos);

        if (result.left().isPresent() &&
                result.left().get() == Player.BedSleepingProblem.NOT_SAFE) {
            level.setBlock(headPos,
                    headState.setValue(BedBlock.OCCUPIED, true), 3);
            player.startSleeping(headPos);
            result = com.mojang.datafixers.util.Either.right(
                    net.minecraft.util.Unit.INSTANCE);
        }

        if (result.left().isPresent()) {
            removeTempBed();
            sleepingPlayerUUID = null;
            player.displayClientMessage(
                    Component.literal("Cannot sleep here: " +
                            result.left().get()), true);
        } else {
            isSleepingInBoat = true;
            sleepingPlayerUUID = player.getUUID();
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.updateSleepingPlayerList();
            }
        }
    }

    /** Air, or this boat's own light from the previous tick. */
    private boolean isFreeForLight(BlockPos pos) {
        BlockState state = level().getBlockState(pos);
        return state.isAir() || state.is(Blocks.LIGHT);
    }

    /** The temporary bed may only replace air or replaceable plants, never fluids or built blocks. */
    private boolean isFreeForTempBed(BlockPos pos) {
        BlockState state = level().getBlockState(pos);
        return state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty());
    }

    private void removeTempBed() {
        if (tempBedPos != null) {
            if (level().getBlockState(tempBedPos).is(Blocks.RED_BED)) {
                level().setBlock(tempBedPos, Blocks.AIR.defaultBlockState(), 3);
            }
            tempBedPos = null;
        }
        if (tempBedFootPos != null) {
            if (level().getBlockState(tempBedFootPos).is(Blocks.RED_BED)) {
                level().setBlock(tempBedFootPos, Blocks.AIR.defaultBlockState(), 3);
            }
            tempBedFootPos = null;
        }
    }
}
