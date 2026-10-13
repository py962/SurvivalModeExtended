package com.terrainlab.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import com.terrainlab.registry.ModStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.EndCityPieces;
import net.minecraft.world.level.levelgen.structure.structures.EndCityStructure;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

/** End city that may generate on lower terrain: minimum start Y 40 instead of vanilla's 60. */
public class RelaxedEndCityStructure extends EndCityStructure {

    public static final MapCodec<RelaxedEndCityStructure> CODEC =
            simpleCodec(RelaxedEndCityStructure::new);

    public RelaxedEndCityStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(
            Structure.GenerationContext context) {
        Rotation rotation = Rotation.getRandom(context.random());
        // 26.x replaced getLowestYIn5by5BoxOffset7Blocks with getLowestYIn5by5Box(x, z); offset 7 into the chunk as before.
        BlockPos blockpos = this.getLowestYIn5by5Box(context, context.chunkPos().getBlockX(7), context.chunkPos().getBlockZ(7), rotation);
        return blockpos.getY() < 40 ? Optional.empty()
                : Optional.of(new Structure.GenerationStub(blockpos,
                (builder) -> this.generatePieces(builder, blockpos, rotation, context)));
    }

    private void generatePieces(StructurePiecesBuilder builder, BlockPos pos,
                                Rotation rotation, Structure.GenerationContext context) {
        List<StructurePiece> list = Lists.newArrayList();
        EndCityPieces.startHouseTower(
                context.structureTemplateManager(), pos, rotation, list, context.random());
        Objects.requireNonNull(builder);
        list.forEach(builder::addPiece);
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.RELAXED_END_CITY.get();
    }
}
