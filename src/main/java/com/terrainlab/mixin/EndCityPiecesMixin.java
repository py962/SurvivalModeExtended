package com.terrainlab.mixin;

import net.minecraft.world.level.levelgen.structure.structures.EndCityPieces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Raises End City recursion depth from vanilla's 8 to 14, giving larger cities. */
@Mixin(EndCityPieces.class)
public class EndCityPiecesMixin {

    @ModifyConstant(method = "recursiveChildren", constant = @Constant(intValue = 8))
    private static int terrainlab$increaseMaxDepth(int vanillaMaxDepth) {
        return 14;
    }
}
