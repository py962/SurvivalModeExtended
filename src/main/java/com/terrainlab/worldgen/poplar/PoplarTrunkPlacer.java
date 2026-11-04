package com.terrainlab.worldgen.poplar;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terrainlab.registry.ModFeatures;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;

/**
 * Backport of 26.3's poplar trunk: a straight trunk with 1-4 one-block side branches, one per horizontal
 * direction, a few blocks below the top, where the foliage starts. Same codec fields as vanilla's
 * minecraft:poplar_trunk_placer, so the 26.3 tree definitions carry over unchanged.
 */
public class PoplarTrunkPlacer extends TrunkPlacer {
    public static final MapCodec<PoplarTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
            i -> trunkPlacerParts(i)
                    .and(i.group(
                            IntProvider.codec(0, 8).fieldOf("trunk_height_above_branches").forGetter(t -> t.trunkHeightAboveBranches),
                            IntProvider.codec(1, 4).fieldOf("branch_amount").forGetter(t -> t.branchAmount)))
                    .apply(i, PoplarTrunkPlacer::new));

    private final IntProvider trunkHeightAboveBranches;
    private final IntProvider branchAmount;

    public PoplarTrunkPlacer(int baseHeight, int heightRandA, int heightRandB,
                             IntProvider trunkHeightAboveBranches, IntProvider branchAmount) {
        super(baseHeight, heightRandA, heightRandB);
        this.trunkHeightAboveBranches = trunkHeightAboveBranches;
        this.branchAmount = branchAmount;
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return ModFeatures.POPLAR_TRUNK_PLACER.get();
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> trunkSetter,
                                                            RandomSource random, int treeHeight, BlockPos origin, TreeConfiguration config) {
        setDirtAt(level, trunkSetter, random, origin.below(), config);
        int upToFoliageBranches = treeHeight - this.trunkHeightAboveBranches.sample(random);

        for (int y = 0; y < treeHeight; y++) {
            this.placeLog(level, trunkSetter, random, origin.above(y), config);
            List<Direction> directions = shuffledHorizontal(random);
            if (upToFoliageBranches - 1 == y) {
                int branches = this.branchAmount.sample(random);
                for (int x = 0; x < branches; x++) {
                    Direction direction = directions.get(x);
                    this.placeLog(level, trunkSetter, random, origin.above(y).relative(direction, 1), config, sideways(direction));
                }
            }
        }
        return List.of(new FoliagePlacer.FoliageAttachment(origin.above(upToFoliageBranches), 0, false));
    }

    static Function<BlockState, BlockState> sideways(Direction direction) {
        return state -> state.trySetValue(RotatedPillarBlock.AXIS, direction.getAxis());
    }

    private static List<Direction> shuffledHorizontal(RandomSource random) {
        return Direction.allShuffled(random).stream()
                .filter(direction -> !direction.getAxis().isVertical())
                .collect(Collectors.toList());
    }
}
