package com.terrainlab.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.entity.EntityTypes;

/** Blocks natural monster spawns other than piglins inside bastion housing-unit pieces. */
@Mixin(NaturalSpawner.class)
public abstract class BastionHousingSpawnMixin {

    private static final String HOUSING_PREFIX = "bastion/units/";

    @Inject(method = "isValidSpawnPostitionForType", at = @At("HEAD"), cancellable = true)
    private static void terrainlab$restrictHousingSpawns(
            ServerLevel level,
            MobCategory category,
            StructureManager structureManager,
            ChunkGenerator generator,
            MobSpawnSettings.SpawnerData spawnerData,
            BlockPos.MutableBlockPos pos,
            double distSq,
            CallbackInfoReturnable<Boolean> cir) {

        if (category != MobCategory.MONSTER) return;
        if (spawnerData.type() == EntityTypes.PIGLIN) return;

        Structure bastion = level.registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                .getValue(BuiltinStructures.BASTION_REMNANT);
        if (bastion == null) return;

        StructureStart start = structureManager.getStructureAt(pos, bastion);
        if (!start.isValid()) return;

        for (StructurePiece piece : start.getPieces()) {
            if (!piece.getBoundingBox().isInside(pos)) continue;
            if (!(piece instanceof PoolElementStructurePiece poolPiece)) continue;

            StructurePoolElement element = poolPiece.getElement();
            if (!(element instanceof SinglePoolElement single)) continue;

            Identifier location = single.getTemplateLocation();
            if (location.getPath().startsWith(HOUSING_PREFIX)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
