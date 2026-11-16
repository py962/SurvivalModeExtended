package com.terrainlab.mixin;

import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** No "can't zoom" cross until the map reaches scale 5. */
@Mixin(CartographyTableScreen.class)
public abstract class CartographyTableScreenScaleMixin {

    @ModifyConstant(method = "extractBackground", constant = @Constant(intValue = 4), require = 1)
    private int terrainlab$maxScale(int vanillaMax) {
        return 5;
    }
}
