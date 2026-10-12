package com.terrainlab.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Frame material for the Redstone Portal, analogous to obsidian for the Nether portal. A closed
 * rectangle of it fills with {@link RedstonePortalBlock} once any frame block receives redstone power.
 * <p>
 * {@link #LIT} reflects whether this block itself is receiving redstone power, regardless of
 * portal completeness, and drives its glow and texture. Both LIT and portal activation are
 * handled by RedstonePortalHandler rather than by overrides here.
 */
public class FieldStoneBlock extends Block {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public FieldStoneBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
}
