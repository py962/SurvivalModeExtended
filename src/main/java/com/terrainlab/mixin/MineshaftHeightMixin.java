package com.terrainlab.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Places normal (non-mesa) mineshafts with their centre at a random Y in [MIN_Y, MAX_Y] instead of
 * below sea level, by redirecting moveBelowSeaLevel in generatePiecesAndAdjust.
 */
@Mixin(MineshaftStructure.class)
public abstract class MineshaftHeightMixin {

    private static final int MIN_Y = 100;
    private static final int MAX_Y = 200;

    @Redirect(method = "generatePiecesAndAdjust", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePiecesBuilder;moveBelowSeaLevel(IILnet/minecraft/util/RandomSource;I)I"))
    private int terrainlab$moveMineshaftHigh(StructurePiecesBuilder builder, int seaLevel, int minY, RandomSource random, int spread) {
        BlockPos center = builder.getBoundingBox().getCenter();
        int targetY = Mth.randomBetweenInclusive(random, MIN_Y, MAX_Y);
        int offset = targetY - center.getY();
        builder.offsetPiecesVertically(offset);
        return offset;
    }
}
