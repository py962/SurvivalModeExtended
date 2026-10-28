package com.terrainlab.block;

import com.mojang.serialization.MapCodec;
import com.terrainlab.blockentity.PotionCauldronBlockEntity;
import com.terrainlab.event.PotionCauldronInteractions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Cauldron holding 1-3 poured potions (LEVEL = potion count); the potions themselves live in
 * {@link PotionCauldronBlockEntity}. Not a {@link LayeredCauldronBlock}, so rain and dripstone
 * never fill it, and entities on fire are not extinguished by it.
 * Drops a plain cauldron (loot table); the potions are lost.
 */
public class PotionCauldronBlock extends AbstractCauldronBlock implements EntityBlock {

    public static final IntegerProperty LEVEL = LayeredCauldronBlock.LEVEL;
    public static final MapCodec<PotionCauldronBlock> CODEC = simpleCodec(PotionCauldronBlock::new);

    public PotionCauldronBlock(BlockBehaviour.Properties properties) {
        super(properties, PotionCauldronInteractions.POTION_CAULDRON);
        this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, 1));
    }

    @Override
    protected MapCodec<PotionCauldronBlock> codec() {
        return CODEC;
    }

    @Override
    public boolean isFull(BlockState state) {
        return state.getValue(LEVEL) == LayeredCauldronBlock.MAX_FILL_LEVEL;
    }

    @Override
    protected double getContentHeight(BlockState state) {
        return (6.0 + state.getValue(LEVEL) * 3.0) / 16.0;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return state.getValue(LEVEL);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(Items.CAULDRON);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PotionCauldronBlockEntity(pos, state);
    }
}
