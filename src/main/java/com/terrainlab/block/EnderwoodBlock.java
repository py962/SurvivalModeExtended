package com.terrainlab.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class EnderwoodBlock extends Block {

    public EnderwoodBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
    @Override
    public com.mojang.serialization.MapCodec<? extends Block> codec() {
        return simpleCodec(EnderwoodBlock::new);
    }
}
