package com.terrainlab.worldgen.poplar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terrainlab.registry.ModFeatures;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;

/**
 * Backport of 26.3's poplar foliage: a tall column of rhombus-shaped leaf rows, narrowing at the top and
 * bottom, with occasional side holes and short log spars where the crown is widest. Same codec fields as
 * vanilla's minecraft:poplar_foliage_placer.
 */
public class PoplarFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<PoplarFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(
            i -> foliagePlacerParts(i)
                    .and(i.group(
                            IntProvider.codec(5, 16).fieldOf("height").forGetter(p -> p.height),
                            Codec.floatRange(0.0F, 1.0F).fieldOf("side_hole_chance").forGetter(p -> p.sideHoleChance)))
                    .apply(i, PoplarFoliagePlacer::new));

    private final IntProvider height;
    private final float sideHoleChance;

    public PoplarFoliagePlacer(IntProvider radius, IntProvider offset, IntProvider height, float sideHoleChance) {
        super(radius, offset);
        this.height = height;
        this.sideHoleChance = sideHoleChance;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return ModFeatures.POPLAR_FOLIAGE_PLACER.get();
    }

    @Override
    protected void createFoliage(LevelSimulatedReader level, FoliagePlacer.FoliageSetter setter, RandomSource random,
                                 TreeConfiguration config, int treeHeight, FoliagePlacer.FoliageAttachment attachment,
                                 int foliageHeight, int leafRadius, int offset) {
        boolean doubleTrunk = attachment.doubleTrunk();
        BlockPos origin = attachment.pos().above(offset);
        int radius = leafRadius + attachment.radiusOffset() - 1;
        boolean flip = random.nextBoolean();
        int top = foliageHeight; // 26.3 adds a per-attachment height offset; 1.21.11 attachments have none (0)

        this.row(level, setter, random, config, origin, radius - 2, top - 1, doubleTrunk, top, flip);
        this.row(level, setter, random, config, origin, radius - 1, top - 2, doubleTrunk, top, flip);
        this.row(level, setter, random, config, origin, radius - 1, top - 3, doubleTrunk, top, flip);
        for (int y = top - 4; y >= 1; y--) {
            this.row(level, setter, random, config, origin, radius, y, doubleTrunk, top, flip);
        }
        this.replaceLeavesWithLog(level, setter, config, random, origin, radius, top - 4, doubleTrunk, top, flip);
        this.row(level, setter, random, config, origin, radius - 1, 0, doubleTrunk, top, flip);
        this.row(level, setter, random, config, origin, Mth.clamp(radius - 2, 1, 2), -1, doubleTrunk, top, flip);
    }

    private void replaceLeavesWithLog(LevelSimulatedReader level, FoliagePlacer.FoliageSetter setter, TreeConfiguration config,
                                      RandomSource random, BlockPos origin, int radius, int y, boolean doubleTrunk,
                                      int foliageHeight, boolean flip) {
        int extra = doubleTrunk ? 1 : 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius + extra; dx++) {
            for (int dz = -radius; dz <= radius + extra; dz++) {
                int absDz = Mth.abs(dz);
                int absDx = Mth.abs(dx);
                if (withinRhombus(radius, absDx, absDz, cornersToCut(dx, dz, radius, partialRow(foliageHeight, y), flip), 2)
                        && (absDz == 0 && radius - absDx >= 4 || absDx == 0 && radius - absDz >= 4)) {
                    pos.setWithOffset(origin, dx, y, dz);
                    tryPlaceLog(level, setter, random, config, pos, PoplarTrunkPlacer.sideways(
                            Direction.fromAxisAndDirection(absDz == 0 ? Direction.Axis.X : Direction.Axis.Z, Direction.AxisDirection.POSITIVE)));
                }
            }
        }
    }

    private static void tryPlaceLog(LevelSimulatedReader level, FoliagePlacer.FoliageSetter setter, RandomSource random,
                                    TreeConfiguration config, BlockPos pos, Function<BlockState, BlockState> modifier) {
        if (level.isStateAtPosition(pos, state -> state.equals(config.foliageProvider.getState(random, pos)))) {
            setter.set(pos, modifier.apply(config.trunkProvider.getState(random, pos)));
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config) {
        return this.height.sample(random);
    }

    private void row(LevelSimulatedReader level, FoliagePlacer.FoliageSetter setter, RandomSource random, TreeConfiguration config,
                     BlockPos origin, int radius, int y, boolean doubleTrunk, int foliageHeight, boolean flip) {
        int extra = doubleTrunk ? 1 : 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius + extra; dx++) {
            for (int dz = -radius; dz <= radius + extra; dz++) {
                if (!this.skip(random, dx, y, dz, radius, foliageHeight, flip)) {
                    pos.setWithOffset(origin, dx, y, dz);
                    tryPlaceLeaf(level, setter, random, config, pos);
                }
            }
        }
    }

    private boolean skip(RandomSource random, int dx, int y, int dz, int radius, int foliageHeight, boolean flip) {
        boolean partial = partialRow(foliageHeight, y);
        int corners = cornersToCut(dx, dz, radius, partial, flip);
        int absDx = Mth.abs(dx);
        int absDz = Mth.abs(dz);
        if (partial && (absDx == radius || absDz == radius)) {
            return true;
        }
        int sideHole = random.nextFloat() <= this.sideHoleChance ? 1 : 0;
        return !withinRhombus(radius, absDx, absDz, corners, sideHole);
    }

    @Override
    protected boolean shouldSkipLocationSigned(RandomSource random, int dx, int y, int dz, int radius, boolean doubleTrunk) {
        throw new IllegalStateException("Overridden method needs more context");
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int radius, boolean doubleTrunk) {
        throw new IllegalStateException("Overridden method needs more context");
    }

    private int cornersToCut(int dx, int dz, int radius, boolean partial, boolean flip) {
        boolean smallCorner = flip ? (dx > 0 && dz > 0 || dz < 0 && dx < 0) : (dx > 0 && dz < 0 || dz > 0 && dx < 0);
        return smallCorner ? radius - 1 : (partial ? radius + 1 : radius);
    }

    private static boolean withinRhombus(int radius, int absDx, int absDz, int corners, int extraRemoval) {
        return absDx + absDz <= radius * 2 - (corners + extraRemoval);
    }

    private static boolean partialRow(int foliageHeight, int y) {
        return foliageHeight - 1 == y || foliageHeight - 2 == y;
    }
}
