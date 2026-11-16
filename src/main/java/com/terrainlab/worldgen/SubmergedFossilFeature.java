package com.terrainlab.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.FossilFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * A vanilla fossil set into the bed of a pool rather than buried 15-24 blocks down: the origin is the
 * bed block (see the placed feature), the fossil's base is sunk 1-{@link #MAX_SINK} blocks into it and
 * the rest rises into the water. Uses vanilla's fossil configuration (templates, processors).
 */
public class SubmergedFossilFeature extends Feature<FossilFeatureConfiguration> {

    private static final int MAX_SINK = 2;

    public SubmergedFossilFeature(Codec<FossilFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<FossilFeatureConfiguration> context) {
        RandomSource random = context.random();
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        FossilFeatureConfiguration config = context.config();
        Rotation rotation = Rotation.getRandom(random);
        int index = random.nextInt(config.fossilStructures.size());
        var manager = level.getLevel().getServer().getStructureManager();
        StructureTemplate base = manager.getOrCreate(config.fossilStructures.get(index));
        StructureTemplate overlay = manager.getOrCreate(config.overlayStructures.get(index));
        ChunkPos chunkPos = new ChunkPos(origin);
        BoundingBox limit = new BoundingBox(chunkPos.getMinBlockX() - 16, level.getMinY(), chunkPos.getMinBlockZ() - 16,
                chunkPos.getMaxBlockX() + 16, level.getMaxY(), chunkPos.getMaxBlockZ() + 16);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setBoundingBox(limit).setRandom(random);
        Vec3i size = base.getSize(rotation);
        BlockPos lowCorner = origin.offset(-size.getX() / 2, 0, -size.getZ() / 2);
        int targetY = origin.getY() - 1 - random.nextInt(MAX_SINK);
        BlockPos targetPos = base.getZeroPositionWithTransform(lowCorner.atY(targetY), Mirror.NONE, rotation);
        if (countEmptyCorners(level, base.getBoundingBox(settings, targetPos)) > config.maxEmptyCornersAllowed) {
            return false;
        }
        settings.clearProcessors();
        config.fossilProcessors.value().list().forEach(settings::addProcessor);
        base.placeInWorld(level, targetPos, targetPos, settings, random, 260);
        settings.clearProcessors();
        config.overlayProcessors.value().list().forEach(settings::addProcessor);
        overlay.placeInWorld(level, targetPos, targetPos, settings, random, 260);
        return true;
    }

    private static int countEmptyCorners(WorldGenLevel level, BoundingBox box) {
        int[] count = {0};
        box.forAllCorners(pos -> {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.is(Blocks.LAVA) || state.is(Blocks.WATER)) {
                count[0]++;
            }
        });
        return count[0];
    }
}
