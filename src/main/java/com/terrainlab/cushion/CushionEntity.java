package com.terrainlab.cushion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Backport of 26.3's cushion: a sittable 1x0.25 decoration that rests on the block below it.
 * Saved data matches vanilla 26.3 ("block_pos", "color"), so in the 26.3 version this entity type is
 * an alias of minecraft:cushion and placed cushions carry over.
 */
public class CushionEntity extends BlockAttachedEntity {
    private static final DyeColor DEFAULT_COLOR = DyeColor.WHITE;
    private static final int CHECK_INTERVAL = 100;
    // 1.21.11 has no DyeColor data serializer: the colour is synced as its id.
    private static final EntityDataAccessor<Integer> DATA_COLOR =
            SynchedEntityData.defineId(CushionEntity.class, EntityDataSerializers.INT);

    private int ticksSinceFireCheck;

    public CushionEntity(EntityType<? extends CushionEntity> type, Level level) {
        super(type, level);
    }

    public DyeColor getColor() {
        return DyeColor.byId(this.entityData.get(DATA_COLOR));
    }

    public void setColor(DyeColor color) {
        this.entityData.set(DATA_COLOR, color.getId());
    }

    /** Anchors the cushion to the block it was placed on (BlockAttachedEntity#pos). */
    void setAnchor(BlockPos anchor) {
        this.pos = anchor;
    }

    @Override
    public void dropItem(ServerLevel level, @Nullable Entity causedBy) {
        this.playSound(SoundEvents.WOOL_BREAK, 1.0F, 1.0F);
        this.showBreakingParticles();
        if (level.getGameRules().get(GameRules.ENTITY_DROPS)
                && !(causedBy instanceof Player player && player.hasInfiniteMaterials())) {
            this.spawnAtLocation(level, this.getCushionItemStackWithData());
        }
    }

    private static boolean isBreakingDeniedFor(DamageSource source) {
        return source.getEntity() instanceof Player player && !player.mayBuild();
    }

    private boolean isBreakingDeniedAtPosFor(ServerLevel level, DamageSource source) {
        return source.getEntity() instanceof Player player && !level.mayInteract(player, this.pos);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return !isBreakingDeniedFor(source) && !this.isBreakingDeniedAtPosFor(level, source)
                && super.hurtServer(level, source, damage);
    }

    @Override
    public boolean hurtClient(DamageSource source) {
        return !isBreakingDeniedFor(source) && super.hurtClient(source);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() || this.isVehicle()) {
            return InteractionResult.PASS;
        } else if (!this.level().isClientSide() && player.startRiding(this)) {
            this.playSound(SoundEvents.WOOL_PLACE, 0.8F, 0.8F);
            return InteractionResult.SUCCESS_SERVER;
        } else {
            return InteractionResult.CONSUME;
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!this.level().isClientSide() && this.getRemovalReason() == null) {
            this.playSound(SoundEvents.WOOL_STEP, 1.0F, 1.2F);
        }
    }

    @Override
    public @Nullable ItemStack getPickResult() {
        return new ItemStack(Cushions.item(this.getColor()));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level && ++this.ticksSinceFireCheck >= CHECK_INTERVAL) {
            this.ticksSinceFireCheck = 0;
            this.destroyIfInFire(level);
        }
    }

    /** Burns away in fire or lava, as vanilla's does. */
    public void destroyIfInFire(ServerLevel level) {
        if (this.isRemoved()) return;
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(this.getBoundingBox().minX, this.getBoundingBox().minY, this.getBoundingBox().minZ),
                BlockPos.containing(this.getBoundingBox().maxX - 1.0E-7, this.getBoundingBox().maxY - 1.0E-7, this.getBoundingBox().maxZ - 1.0E-7))) {
            BlockState state = level.getBlockState(p);
            if (state.is(BlockTags.FIRE) || state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
                this.hurtServer(level, this.damageSources().inFire(), 1.0F);
                return;
            }
        }
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightningBolt) {
        if (!this.isRemoved()) {
            this.kill(level);
            this.dropItem(level, lightningBolt);
        }
    }

    @Override
    public void setPos(double x, double y, double z) {
        this.setPosRaw(x, y, z);
        super.setPos(x, y, z);
    }

    private void showBreakingParticles() {
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, Cushions.wool(this.getColor()).defaultBlockState()),
                    this.getX(), this.getY(0.6666666666666666), this.getZ(), 10,
                    this.getBbWidth() / 4.0F, this.getBbHeight() / 4.0F, this.getBbWidth() / 4.0F, 0.05);
        }
    }

    public static boolean canBePlacedAt(Level level, AABB box) {
        return wouldSurviveAt(level, box) && !isAnchorBuried(level, box);
    }

    public static boolean wouldSurviveAt(Level level, AABB box) {
        return hasAnchorBelow(level, box) && !isCoveredBySuffocatingBlocks(level, box);
    }

    private static boolean hasAnchorBelow(Level level, AABB box) {
        AABB anchorBox = new AABB(box.minX, box.minY - 0.015625, box.minZ,
                Math.nextDown(box.maxX), box.minY, Math.nextDown(box.maxZ));
        AABB search = anchorBox.expandTowards(0.0, -0.125, 0.0);
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(search.minX, search.minY, search.minZ),
                BlockPos.containing(search.maxX, search.maxY, search.maxZ))) {
            VoxelShape shape = level.getBlockState(p).getShape(level, p);
            if (!shape.isEmpty() && shape.bounds().move(p).intersects(anchorBox)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAnchorBuried(Level level, AABB box) {
        AABB restingSlice = new AABB(box.minX, box.minY, box.minZ, box.maxX, box.minY + 0.015625, box.maxZ).deflate(1.0E-7);
        VoxelShape exposed = Shapes.create(restingSlice);
        for (VoxelShape collider : level.getBlockCollisions(null, restingSlice)) {
            exposed = Shapes.join(exposed, collider, BooleanOp.ONLY_FIRST);
            if (exposed.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCoveredBySuffocatingBlocks(Level level, AABB box) {
        AABB inner = box.deflate(1.0E-7);
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(inner.minX, inner.minY, inner.minZ),
                BlockPos.containing(inner.maxX, inner.maxY, inner.maxZ))) {
            if (!level.getBlockState(p).isSuffocating(level, p)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean survives() {
        return wouldSurviveAt(this.level(), this.getBoundingBox());
    }

    @Override
    protected void recalculateBoundingBox() {
        this.setBoundingBox(this.makeBoundingBox());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_COLOR, DEFAULT_COLOR.getId());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("color", DyeColor.CODEC, this.getColor());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setColor(input.read("color", DyeColor.CODEC).orElse(DEFAULT_COLOR));
    }

    private ItemStack getCushionItemStackWithData() {
        ItemStack stack = new ItemStack(Cushions.item(this.getColor()));
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, this.getCustomName());
        return stack;
    }

}
