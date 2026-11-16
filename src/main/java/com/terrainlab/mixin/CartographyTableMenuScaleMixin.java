package com.terrainlab.mixin;

import net.minecraft.world.inventory.CartographyTableMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Paper zooms a map out one more level than vanilla (scale 5: 4096 blocks across). */
@Mixin(CartographyTableMenu.class)
public abstract class CartographyTableMenuScaleMixin {

    @ModifyConstant(method = "lambda$setupResultSlot$0", constant = @Constant(intValue = 4), require = 1)
    private int terrainlab$maxScale(int vanillaMax) {
        return 5;
    }
}
