package com.terrainlab.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.List;

/**
 * A vanilla fossil set into the bed of a pool rather than buried 15-24 blocks down: the origin is the
 * bed block (see the placed feature), the fossil's base is sunk 1..{@code max_sink} blocks into it and
 * the rest rises into the water. Same templates and processors as vanilla's fossil feature.
 */
public record SubmergedFossilFeature(
        List<Identifier> fossilStructures,
        List<Identifier> overlayStructures,
        Holder<StructureProcessorList> fossilProcessors,
        Holder<StructureProcessorList> overlayProcessors,
        int maxEmptyCornersAllowed,
        int maxSink
) implements Feature {

    public static final MapCodec<SubmergedFossilFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ExtraCodecs.nonEmptyList(Identifier.CODEC.listOf()).fieldOf("fossil_structures").forGetter(SubmergedFossilFeature::fossilStructures),
            ExtraCodecs.nonEmptyList(Identifier.CODEC.listOf()).fieldOf("overlay_structures").forGetter(SubmergedFossilFeature::overlayStructures),
            StructureProcessorType.LIST_CODEC.fieldOf("fossil_processors").forGetter(SubmergedFossilFeature::fossilProcessors),
            StructureProcessorType.LIST_CODEC.fieldOf("overlay_processors").forGetter(SubmergedFossilFeature::overlayProcessors),
            Codec.intRange(0, 7).fieldOf("max_empty_corners_allowed").forGetter(SubmergedFossilFeature::maxEmptyCornersAllowed),
            Codec.intRange(0, 8).fieldOf("max_sink").forGetter(SubmergedFossilFeature::maxSink)
    ).apply(i, SubmergedFossilFeature::new));

    @Override
    public MapCodec<SubmergedFossilFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        Rotation rotation = Rotation.getRandom(random);
        int index = random.nextInt(fossilStructures.size());
        var manager = level.getLevel().getServer().getStructureTemplateManager();
        StructureTemplate base = manager.getOrCreate(fossilStructures.get(index));
        StructureTemplate overlay = manager.getOrCreate(overlayStructures.get(index));
        ChunkPos chunkPos = ChunkPos.containing(origin);
        BoundingBox limit = new BoundingBox(chunkPos.getMinBlockX() - 16, level.getMinY(), chunkPos.getMinBlockZ() - 16,
                chunkPos.getMaxBlockX() + 16, level.getMaxY(), chunkPos.getMaxBlockZ() + 16);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setBoundingBox(limit).setRandom(random);
        Vec3i size = base.getSize(rotation);
        BlockPos lowCorner = origin.offset(-size.getX() / 2, 0, -size.getZ() / 2);
        int targetY = origin.getY() - (maxSink <= 1 ? maxSink : 1 + random.nextInt(maxSink));
        BlockPos targetPos = base.getZeroPositionWithTransform(lowCorner.atY(targetY), Mirror.NONE, rotation);
        if (countEmptyCorners(level, base.getBoundingBox(settings, targetPos)) > maxEmptyCornersAllowed) {
            return false;
        }
        settings.clearProcessors();
        fossilProcessors.value().list().forEach(settings::addProcessor);
        base.placeInWorld(level, targetPos, targetPos, settings, random, 260);
        settings.clearProcessors();
        overlayProcessors.value().list().forEach(settings::addProcessor);
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
