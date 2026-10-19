package com.terrainlab.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.terrainlab.worldgen.HardcoreVillageRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/**
 * Ultra-hardcore villages (see {@link HardcoreVillageRules}): rejects ~3 in 4 village start positions
 * and hands JigsawPlacement#addPieces a zombie-only copy of the start pool, so the town centre (and,
 * through its own jigsaws, the houses, streets and zombie villagers) is always the abandoned variant.
 * A village whose start pool has no zombie elements does not generate. findGenerationPoint backs both
 * Structure#generate and StructureCheck, so /locate and explorer maps agree with what generates.
 */
@Mixin(JigsawStructure.class)
public abstract class JigsawStructureHardcoreVillageMixin {

    @WrapOperation(
            method = "findGenerationPoint",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/structure/pools/JigsawPlacement;addPieces(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;Lnet/minecraft/core/Holder;Ljava/util/Optional;ILnet/minecraft/core/BlockPos;ZLjava/util/Optional;Lnet/minecraft/world/level/levelgen/structure/structures/JigsawStructure$MaxDistance;Lnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/pools/DimensionPadding;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;)Ljava/util/Optional;"
            )
    )
    private Optional<Structure.GenerationStub> terrainlab$hardcoreVillage(
            Structure.GenerationContext context,
            Holder<StructureTemplatePool> startPool,
            Optional<Identifier> startJigsaw,
            int maxDepth,
            BlockPos position,
            boolean doExpansionHack,
            Optional<Heightmap.Types> projectStartToHeightmap,
            JigsawStructure.MaxDistance maxDistanceFromCenter,
            PoolAliasLookup poolAliasLookup,
            DimensionPadding dimensionPadding,
            LiquidSettings liquidSettings,
            Operation<Optional<Structure.GenerationStub>> original
    ) {
        if (!HardcoreVillageRules.isActive()
                || !HardcoreVillageRules.isVillage((Structure) (Object) this, startPool, context.registryAccess())) {
            return original.call(context, startPool, startJigsaw, maxDepth, position, doExpansionHack,
                    projectStartToHeightmap, maxDistanceFromCenter, poolAliasLookup, dimensionPadding, liquidSettings);
        }

        if (!HardcoreVillageRules.keepVillagePosition(context.seed(), context.chunkPos())) {
            return Optional.empty();
        }

        // Resolve the start pool exactly as addPieces does (pool aliases), then restrict it to zombie elements.
        Registry<StructureTemplatePool> pools = context.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        StructureTemplatePool centerPool = startPool.unwrapKey()
                .flatMap(key -> pools.getOptional(poolAliasLookup.lookup(key)))
                .orElse(startPool.value());
        Optional<StructureTemplatePool> zombiePool = HardcoreVillageRules.zombieStartPool(centerPool);
        if (zombiePool.isEmpty()) {
            return Optional.empty(); // no abandoned variant: this village type does not generate in hardcore
        }

        // An unregistered (direct) holder makes addPieces use this pool as-is for the start element.
        return original.call(context, Holder.direct(zombiePool.get()), startJigsaw, maxDepth, position,
                doExpansionHack, projectStartToHeightmap, maxDistanceFromCenter, poolAliasLookup, dimensionPadding,
                liquidSettings);
    }
}
