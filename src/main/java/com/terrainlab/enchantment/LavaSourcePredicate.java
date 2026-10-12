package com.terrainlab.enchantment;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terrainlab.registry.ModBlockPredicateTypes;
import net.minecraft.core.Vec3i;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicateType;
import net.minecraft.world.level.levelgen.blockpredicates.StateTestingPredicate;

/**
 * Block predicate {@code terrainlab:lava_source}: matches lava source blocks only. Used by the
 * Frost Walker strider-saddle effect so obsidian never forms from flowing lava, which vanilla's
 * {@code matching_fluids} predicate cannot distinguish.
 */
public class LavaSourcePredicate extends StateTestingPredicate {

    public static final MapCodec<LavaSourcePredicate> CODEC = RecordCodecBuilder.mapCodec(
            instance -> stateTestingCodec(instance).apply(instance, LavaSourcePredicate::new)
    );

    public LavaSourcePredicate(Vec3i offset) {
        super(offset);
    }

    @Override
    protected boolean test(BlockState state) {
        return state.getFluidState().is(FluidTags.LAVA) && state.getFluidState().isSource();
    }

    @Override
    public BlockPredicateType<?> type() {
        return ModBlockPredicateTypes.LAVA_SOURCE.get();
    }
}
