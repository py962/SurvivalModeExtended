
package com.terrainlab.block;

import com.mojang.serialization.MapCodec;
import com.terrainlab.blockentity.CelestialConduitBlockEntity;
import com.terrainlab.celestial.CelestialPairing;
import com.terrainlab.celestial.CelestialVariant;
import com.terrainlab.item.CelestialAmuletItem;
import com.terrainlab.registry.ModBlockEntities;
import com.terrainlab.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

import javax.annotation.Nullable;
import java.util.UUID;

public class CelestialConduitBlock extends BaseEntityBlock {

    public static final MapCodec<CelestialConduitBlock> CODEC =
            simpleCodec(properties ->
                    new CelestialConduitBlock(CelestialVariant.LIFE, properties));

    /**
     * Drives the block's light level: dim while inactive, bright when active.
     */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private final CelestialVariant variant;

    public CelestialConduitBlock(
            CelestialVariant variant,
            BlockBehaviour.Properties properties
    ) {
        super(properties);
        this.variant = variant;
        registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
    }

    public CelestialVariant variant() {
        return variant;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder
    ) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    @Override
    public MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CelestialConduitBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        if (level.isClientSide()) {
            return createTickerHelper(
                    type,
                    ModBlockEntities.CELESTIAL_CONDUIT_BE.get(),
                    CelestialConduitBlockEntity::clientTick
            );
        }

        return createTickerHelper(
                type,
                ModBlockEntities.CELESTIAL_CONDUIT_BE.get(),
                CelestialConduitBlockEntity::tick
        );
    }

    /**
     * Right-click a conduit with a matching Celestial Amulet in the main hand
     * to pair the amulet with this conduit.
     *
     * The conduit retains its existing pair ID. If it has no pair ID, one is
     * generated. Updating the amulet's component replaces only that amulet's
     * previous pairing, allowing multiple amulets to share this conduit.
     */
    @Override
    protected InteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        // Pairing specifically requires the amulet to be in the main hand.
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        ItemStack mainHandStack = player.getMainHandItem();

        if (!(mainHandStack.getItem() instanceof CelestialAmuletItem)) {
            return InteractionResult.PASS;
        }

        // Client acknowledges the interaction; the server performs the change.
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(level instanceof ServerLevel serverLevel)
                || !(level.getBlockEntity(pos) instanceof CelestialConduitBlockEntity conduit)) {
            return InteractionResult.FAIL;
        }

        CelestialPairing.pair(serverLevel, pos, player, mainHandStack, variant, conduit.pairId(), newId -> {
            conduit.setPairId(newId);
            level.sendBlockUpdated(pos, state, state, 3);
        });
        return InteractionResult.SUCCESS;
    }

    /**
     * Copies an existing pair UUID from a placed conduit item, if present.
     */
    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (level.isClientSide()) {
            return;
        }

        UUID pairId = stack.get(ModDataComponents.CELESTIAL_PAIR_ID.get());

        if (pairId == null) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (blockEntity instanceof CelestialConduitBlockEntity conduit) {
            conduit.setPairId(pairId);
        }
    }
}
