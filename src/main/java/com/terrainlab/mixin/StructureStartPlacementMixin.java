package com.terrainlab.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Rejects the generation point of selected TerrainLab structures whose bounding box falls outside their
 * allowed Y range, so no StructureStart (or reference) is created for them. Runs for both real generation
 * and StructureCheck, keeping /locate and explorer maps consistent with what generates.
 */
@Mixin(Structure.class)
public abstract class StructureStartPlacementMixin {

    @Unique
    private static final Identifier TERRAINLAB$TRAPPERS_LODGE = Identifier.parse("terrainlab:trappers_lodge");
    @Unique
    private static final Identifier TERRAINLAB$PIRATES_COVE = Identifier.parse("terrainlab:pirates_cove");

    @Inject(
            method = "findValidGenerationPoint",
            at = @At("RETURN"),
            cancellable = true
    )
    private void terrainlab$restrictStructureY(
            Structure.GenerationContext context,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir
    ) {
        Optional<Structure.GenerationStub> result = cir.getReturnValue();
        if (result == null || result.isEmpty()) {
            return;
        }

        Structure structure = (Structure) (Object) this;
        Registry<Structure> registry = context.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Identifier id = registry.getKey(structure);

        if (id == null
                || !(id.equals(TERRAINLAB$TRAPPERS_LODGE)
                || id.equals(TERRAINLAB$PIRATES_COVE))) {
            return;
        }

        // Builds the pieces once and hands the built builder on, so Structure#generate does not rebuild them.
        Structure.GenerationStub stub = result.get();
        StructurePiecesBuilder builder = stub.getPiecesBuilder();
        if (builder.isEmpty()) {
            return; // Structure#generate already rejects a start without pieces
        }

        // Same box as StructureStart#getBoundingBox.
        BoundingBox structureBox = structure.adjustBoundingBox(builder.getBoundingBox());

        // The lodge is judged by its main building (the jigsaw start piece, always first), not the
        // cellar 5 blocks beneath it.
        BoundingBox lodgeBox = builder.build().pieces().get(0).getBoundingBox();

        if (terrainlab$isOutsideAllowedY(id, structureBox, lodgeBox)) {
            cir.setReturnValue(Optional.empty());
            return;
        }

        cir.setReturnValue(Optional.of(new Structure.GenerationStub(stub.position(), Either.right(builder))));
    }

    @Unique
    private static boolean terrainlab$isOutsideAllowedY(Identifier id, BoundingBox structureBox,
                                                        BoundingBox startPieceBox) {
        if (id.equals(TERRAINLAB$TRAPPERS_LODGE)) {
            return startPieceBox.minY() < 70; // lodge floor; the cellar may go lower
        }
        if (id.equals(TERRAINLAB$PIRATES_COVE)) {
            return structureBox.minY() < 50 || structureBox.maxY() > 75;
        }
        return false;
    }
}
