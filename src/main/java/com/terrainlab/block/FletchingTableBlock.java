package com.terrainlab.block;

import com.mojang.serialization.MapCodec;
import com.terrainlab.blockentity.FletchingTableBlockEntity;
import com.terrainlab.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/** Fletching table block backed by {@link FletchingTableBlockEntity}. */
public class FletchingTableBlock extends BaseEntityBlock {

    public static final MapCodec<FletchingTableBlock> CODEC = simpleCodec(FletchingTableBlock::new);

    public FletchingTableBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FletchingTableBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FletchingTableBlockEntity fletchingTable) {
                player.openMenu(fletchingTable, buf -> buf.writeBlockPos(pos));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
                                                                  BlockState state, BlockEntityType<T> type) {
        return null; // crafting happens on slot change
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state,
                                               ServerLevel level, BlockPos pos, boolean moved) {
        super.affectNeighborsAfterRemoval(state, level, pos, moved);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FletchingTableBlockEntity fletchingTable) {
            net.minecraft.world.Containers.dropContents(level, pos, fletchingTable);
        }
    }
}
