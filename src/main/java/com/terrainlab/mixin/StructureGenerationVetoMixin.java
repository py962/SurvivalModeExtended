package com.terrainlab.mixin;

import com.terrainlab.TerrainLab;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/**
 * Rejects terrainlab trappers_lodge and pirates_cove starts (the outlaws hideout is buried and places
 * itself; see BuriedHideoutStructure) outside their allowed Y
 * range by returning {@link StructureStart#INVALID_START} from {@code Structure#generate}. Vetoing
 * here, before the start is recorded, keeps vetoed attempts invisible to /locate and spawn
 * overrides. StructureStartPlacementMixin applies the same rule earlier, in findValidGenerationPoint, so
 * StructureCheck agrees; this check remains as a backstop.
 */
@Mixin(Structure.class)
public abstract class StructureGenerationVetoMixin {

    @Inject(
            method = "generate",
            at = @At("RETURN"),
            cancellable = true
    )
    private void terrainlab$vetoLowStarts(
            Holder<Structure> structureHolder,
            ResourceKey<Level> dimension,
            RegistryAccess registryAccess,
            ChunkGenerator chunkGenerator,
            BiomeSource biomeSource,
            Climate.Sampler climateSampler,
            RandomState randomState,
            StructureTemplateManager structureTemplateManager,
            long seed,
            ChunkPos chunkPos,
            int references,
            LevelHeightAccessor heightAccessor,
            Predicate<Holder<Biome>> validBiome,
            CallbackInfoReturnable<StructureStart> cir
    ) {
        StructureStart result = cir.getReturnValue();
        if (result == null || result == StructureStart.INVALID_START) {
            return;
        }

        Structure structure = (Structure) (Object) this;
        Identifier id = registryAccess.lookupOrThrow(Registries.STRUCTURE).getKey(structure);
        if (id == null) {
            return;
        }

        BoundingBox box = result.getBoundingBox();

        // The lodge is judged by its main building (the jigsaw start piece, always first), not the
        // cellar 5 blocks beneath it.
        if (id.equals(Identifier.parse("terrainlab:trappers_lodge"))
                && !result.getPieces().isEmpty()
                && result.getPieces().get(0).getBoundingBox().minY() < 70) {
            // TerrainLab.LOGGER.info(
                    // "[TerrainLab] Vetoed trappers_lodge generation at Y {} before StructureStart was registered (minimum Y is 70).",
                    // box.minY()
            // );
            cir.setReturnValue(StructureStart.INVALID_START);
            return;
        }

        if (id.equals(Identifier.parse("terrainlab:pirates_cove")) && (box.minY() < 50 || box.maxY() > 75)) {
            // TerrainLab.LOGGER.info(
                    // "[TerrainLab] Vetoed pirates_cove generation with Y range {} to {} before StructureStart was registered (allowed Y range is 50-75).",
                    // box.minY(), box.maxY()
            // );
            cir.setReturnValue(StructureStart.INVALID_START);
        }
    }
}
