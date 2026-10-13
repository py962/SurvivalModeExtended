package com.terrainlab.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Column;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;

/**
 * Underwater magnetite ore, adapted from vanilla UnderwaterMagmaFeature. Positions need solid support
 * below; SURFACE_EXPOSURE_CHANCE of them skip vanilla's requirement that all four horizontal neighbours
 * be solid, producing exposed outcrops on the sea floor. Never placed above Y=0.
 */
public record MagnetiteOreFeature(int floorSearchRange, int placementRadiusAroundFloor, float placementProbabilityPerValidPosition)
        implements Feature {

    // Same config fields/JSON keys as vanilla's underwater magma feature (formerly UnderwaterMagmaConfiguration).
    public static final MapCodec<MagnetiteOreFeature> CODEC = RecordCodecBuilder.mapCodec(
            i -> i.group(
                            Codec.intRange(0, 512).fieldOf("floor_search_range").forGetter(MagnetiteOreFeature::floorSearchRange),
                            Codec.intRange(0, 64).fieldOf("placement_radius_around_floor").forGetter(MagnetiteOreFeature::placementRadiusAroundFloor),
                            Codec.floatRange(0.0F, 1.0F)
                                    .fieldOf("placement_probability_per_valid_position")
                                    .forGetter(MagnetiteOreFeature::placementProbabilityPerValidPosition)
                    )
                    .apply(i, MagnetiteOreFeature::new)
    );

    // Per valid position, chance to skip the horizontal enclosure check.
    private static final float SURFACE_EXPOSURE_CHANCE = 0.25F;

    @Override
    public MapCodec<MagnetiteOreFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {

        OptionalInt floorY = getFloorY(level, origin, this.floorSearchRange);
        if (floorY.isEmpty()) {
            return false;
        }

        BlockPos floorPos = origin.atY(floorY.getAsInt());
        Vec3i radius = new Vec3i(
                this.placementRadiusAroundFloor,
                this.placementRadiusAroundFloor,
                this.placementRadiusAroundFloor
        );
        BoundingBox box = BoundingBox.fromCorners(floorPos.subtract(radius), floorPos.offset(radius));

        return BlockPos.betweenClosedStream(box)
                .filter(pos -> pos.getY() <= 0)
                .filter(pos -> random.nextFloat() < this.placementProbabilityPerValidPosition)
                .filter(pos -> this.isValidPlacement(level, pos, random))
                .mapToInt(pos -> {
                    level.setBlock(pos, com.terrainlab.registry.ModBlocks.MAGNETITE_ORE.get().defaultBlockState(), 2);
                    return 1;
                })
                .sum() > 0;
    }

    private static OptionalInt getFloorY(WorldGenLevel level, BlockPos pos, int floorSearchRange) {
        Predicate<BlockState> isWater = state -> state.is(Blocks.WATER);
        Predicate<BlockState> isNotWater = state -> !state.is(Blocks.WATER);
        Optional<Column> column = Column.scan(level, pos, floorSearchRange, isWater, isNotWater);
        return column.map(Column::getFloor).orElseGet(OptionalInt::empty);
    }

    private boolean isValidPlacement(WorldGenLevel level, BlockPos pos, RandomSource random) {
        // Solid block with solid support below, as in vanilla.
        if (isWaterOrAir(level.getBlockState(pos)) || this.isVisibleFromOutside(level, pos.below(), Direction.UP)) {
            return false;
        }

        // Exposed outcrop: skip the enclosure check.
        if (random.nextFloat() < SURFACE_EXPOSURE_CHANCE) {
            return true;
        }

        // Otherwise all four horizontal neighbours must be solid.
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (this.isVisibleFromOutside(level, pos.relative(direction), direction.getOpposite())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWaterOrAir(BlockState state) {
        return state.is(Blocks.WATER) || state.isAir();
    }

    private boolean isVisibleFromOutside(LevelAccessor level, BlockPos pos, Direction direction) {
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getFaceOcclusionShape(direction);
        return shape == Shapes.empty() || !Block.isShapeFullBlock(shape);
    }
}
