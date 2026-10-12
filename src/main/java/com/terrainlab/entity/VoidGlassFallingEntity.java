package com.terrainlab.entity;

import com.terrainlab.mixin.FallingBlockEntityAccessor;
import com.terrainlab.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/** Falling-block entity for void glass: rises instead of falling and settles under the first ceiling it meets. */
public class VoidGlassFallingEntity extends FallingBlockEntity {

    // Upward acceleration per tick (blocks/tick^2).
    private static final double RISE_ACCEL = 0.04;
    private int riseTime = 0;

    public VoidGlassFallingEntity(EntityType<? extends VoidGlassFallingEntity> type, Level level) {
        super(type, level);
    }

    public static VoidGlassFallingEntity risingFall(Level level, BlockPos pos, BlockState state) {
        VoidGlassFallingEntity entity = new VoidGlassFallingEntity(ModEntities.VOID_GLASS_FALLING.get(), level);

        BlockState clean = state.hasProperty(BlockStateProperties.WATERLOGGED)
                ? state.setValue(BlockStateProperties.WATERLOGGED, false)
                : state;
        ((FallingBlockEntityAccessor) entity).terrainlab$setBlockState(clean);

        entity.blocksBuilding = true;
        entity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setStartPos(pos);

        level.setBlock(pos, state.getFluidState().createLegacyBlock(), 3);
        level.addFreshEntity(entity);
        return entity;
    }

    @Override
    public void tick() {
        BlockState blockState = this.getBlockState();
        if (blockState.isAir()) {
            this.discard();
            return;
        }

        Block block = blockState.getBlock();
        ++this.riseTime;

        this.setDeltaMovement(this.getDeltaMovement().add(0.0, RISE_ACCEL, 0.0));
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.applyEffectsFromBlocks();
        this.handlePortal();

        if (!(this.level() instanceof ServerLevel serverlevel)) {
            return;
        }
        if (!this.isAlive()) {
            return;
        }

        BlockPos pos = this.blockPosition();
        boolean ceilingBlocked = !FallingBlock.isFree(this.level().getBlockState(pos.above()));

        if (!ceilingBlocked) {
            // Give up after rising too long or reaching world height.
            if (this.riseTime > 100 && pos.getY() >= this.level().getMaxY() || this.riseTime > 600) {
                if (this.dropItem && (Boolean) serverlevel.getGameRules().get(GameRules.MOB_DROPS)) {
                    this.spawnAtLocation(serverlevel, block);
                }
                this.discard();
            }
            return;
        }

        // Hit a ceiling: try to settle here.
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.7, -0.5, 0.7));

        boolean canReplace = this.level().getBlockState(pos).canBeReplaced(
                new DirectionalPlaceContext(this.level(), pos, Direction.UP, ItemStack.EMPTY, Direction.DOWN));
        boolean canSurvive = blockState.canSurvive(this.level(), pos);

        if (canReplace && canSurvive) {
            if (this.level().setBlock(pos, blockState, 3)) {
                serverlevel.getChunkSource().chunkMap.sendToTrackingPlayers(
                        this, new ClientboundBlockUpdatePacket(pos, this.level().getBlockState(pos)));
                this.discard();
                if (block instanceof Fallable fallable) {
                    fallable.onLand(this.level(), pos, blockState, this.level().getBlockState(pos), this);
                }
            } else {
                this.discard();
                if (this.dropItem && (Boolean) serverlevel.getGameRules().get(GameRules.MOB_DROPS)) {
                    this.spawnAtLocation(serverlevel, block);
                }
            }
        } else {
            this.discard();
            if (this.dropItem && (Boolean) serverlevel.getGameRules().get(GameRules.MOB_DROPS)) {
                this.spawnAtLocation(serverlevel, block);
            }
        }
    }
}
