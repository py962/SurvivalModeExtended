package com.terrainlab.hearth;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.CampfireBlock;

/** Brick-ringed campfire; the evening gathering point for tier-3+ villages. */
public class HearthBlock extends CampfireBlock {

    public static final MapCodec<HearthBlock> CODEC = simpleCodec(HearthBlock::new);

    public HearthBlock(Properties properties) {
        super(true, 1, properties);
    }

    @Override
    @SuppressWarnings("unchecked")
    public MapCodec<CampfireBlock> codec() {
        return (MapCodec<CampfireBlock>) (MapCodec<?>) CODEC;
    }
}
