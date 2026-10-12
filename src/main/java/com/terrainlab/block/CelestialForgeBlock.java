package com.terrainlab.block;

import com.terrainlab.blockentity.CelestialForgeBlockEntity;
import com.terrainlab.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import com.mojang.serialization.MapCodec;

import javax.annotation.Nullable;

/**
 * Celestial Forge block. Its gem slot opens only while an aurora is active and the top is open to
 * the sky, and not while a ritual is under way; see {@link CelestialForgeBlockEntity}.
 */
public class CelestialForgeBlock extends BaseEntityBlock {

    public static final MapCodec<CelestialForgeBlock> CODEC = simpleCodec(CelestialForgeBlock::new);

    /** True while the forge is usable (aurora active and open to sky); selects the top-face model. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public CelestialForgeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    @Override
    public MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CelestialForgeBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            // Silently unhandled while not ready.
            if (!CelestialForgeBlockEntity.canOpen(level, pos)) {
                return InteractionResult.PASS;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof CelestialForgeBlockEntity forge
                    && forge.phase() == CelestialForgeBlockEntity.Phase.IDLE) {
                player.openMenu(forge, buf -> buf.writeBlockPos(pos));
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    com.terrainlab.advancement.ModAdvancements.award(serverPlayer, "celestial_forge");
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return createTickerHelper(type, ModBlockEntities.CELESTIAL_FORGE_BE.get(), CelestialForgeBlockEntity::clientTick);
        }
        return createTickerHelper(type, ModBlockEntities.CELESTIAL_FORGE_BE.get(), CelestialForgeBlockEntity::serverTick);
    }
}
