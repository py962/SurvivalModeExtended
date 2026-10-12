package com.terrainlab.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Invulnerable stone golem that, when fed gravel, turns away from the player and
 * tunnels straight ahead for up to {@link #MAX_TUNNEL_BLOCKS} blocks.
 */
public class GraniteGolemEntity extends AbstractGolem {

    private static final EntityDataAccessor<Integer> STATE =
            SynchedEntityData.defineId(GraniteGolemEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> EYES_GLOWING =
            SynchedEntityData.defineId(GraniteGolemEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int STATE_IDLE = 0;
    public static final int STATE_ACTIVATING = 1;
    public static final int STATE_TUNNELLING = 2;

    private int activatingTicks = 0;
    private int blocksTunnelled = 0;
    private static final int ACTIVATION_DELAY = 60; // ticks
    private static final int MAX_TUNNEL_BLOCKS = 100;
    private static final int TUNNEL_TICKS_PER_BLOCK = 10;
    private int tunnelTickCounter = 0;

    public GraniteGolemEntity(EntityType<? extends AbstractGolem> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        // No goals — all behaviour is tick-based
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, STATE_IDLE);
        builder.define(EYES_GLOWING, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 9999.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 15.0)
                .add(Attributes.STEP_HEIGHT, 2.0);
    }

    public int getGolemState() {
        return this.entityData.get(STATE);
    }

    public void setGolemState(int state) {
        this.entityData.set(STATE, state);
    }

    public boolean isEyesRed() {
        return this.entityData.get(EYES_GLOWING);
    }

    public void setEyesRed(boolean glowing) {
        this.entityData.set(EYES_GLOWING, glowing);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return true;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!level().isClientSide() && getGolemState() == STATE_IDLE) {
            if (player.getItemInHand(hand).is(Items.GRAVEL)) {
                // Face away from player
                Vec3 toPlayer = player.position().subtract(this.position());
                float yaw = (float)(Math.atan2(toPlayer.z, toPlayer.x)
                        * (180.0 / Math.PI)) - 90.0f;
                this.setYRot(yaw + 180.0f);
                this.yHeadRot = this.getYRot();
                this.yBodyRot = this.getYRot();

                if (!player.isCreative()) {
                    player.getItemInHand(hand).shrink(1);
                }

                setGolemState(STATE_ACTIVATING);
                setEyesRed(true);
                activatingTicks = 0;
                blocksTunnelled = 0;
                tunnelTickCounter = 0;

                this.playSound(SoundEvents.IRON_GOLEM_REPAIR, 1.0f, 0.8f);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        switch (getGolemState()) {
            case STATE_ACTIVATING -> {
                activatingTicks++;
                if (activatingTicks % 5 == 0) {
                    this.playSound(SoundEvents.IRON_GOLEM_STEP, 0.5f, 0.7f);
                }
                if (activatingTicks >= ACTIVATION_DELAY) {
                    setGolemState(STATE_TUNNELLING);
                    activatingTicks = 0;
                }
            }
            case STATE_TUNNELLING -> {
                tunnelTickCounter++;
                if (tunnelTickCounter >= TUNNEL_TICKS_PER_BLOCK) {
                    tunnelTickCounter = 0;
                    tunnelStep();
                }
            }
        }
    }

    private void tunnelStep() {
        if (blocksTunnelled >= MAX_TUNNEL_BLOCKS) {
            setGolemState(STATE_IDLE);
            setEyesRed(false);
            this.playSound(SoundEvents.IRON_GOLEM_DEATH, 0.5f, 1.2f);
            return;
        }

        Direction facing = Direction.fromYRot(this.getYRot());
        BlockPos feet = this.blockPosition();
        BlockPos targetFeet = feet.relative(facing);
        BlockPos targetHead = targetFeet.above();

        // Stop at blocks that must not be removed (bedrock, portal frames, containers, spawners).
        if (!isTunnellable(targetFeet) || !isTunnellable(targetHead) || !isTunnellable(targetHead.above())) {
            setGolemState(STATE_IDLE);
            setEyesRed(false);
            this.playSound(SoundEvents.IRON_GOLEM_DEATH, 0.5f, 1.2f);
            return;
        }

        // Clear 1 wide, 2 tall tunnel ahead plus extra above
        clearBlock(targetFeet);
        clearBlock(targetHead);
        clearBlock(targetHead.above());

        // Horizontal step only; gravity in super.tick() handles Y.
        this.setPos(
                targetFeet.getX() + 0.5,
                this.getY(),
                targetFeet.getZ() + 0.5
        );

        Vec3 current = this.getDeltaMovement();
        this.setDeltaMovement(0, current.y, 0);

        blocksTunnelled++;
        this.playSound(SoundEvents.IRON_GOLEM_ATTACK, 0.8f, 0.9f);
    }

    private boolean isTunnellable(BlockPos pos) {
        var state = level().getBlockState(pos);
        return state.isAir() || (state.getDestroySpeed(level(), pos) >= 0.0F && !state.hasBlockEntity());
    }

    private void clearBlock(BlockPos pos) {
        if (!level().isClientSide()) {
            level().destroyBlock(pos, false);
        }
    }
}
