package com.terrainlab.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.terrainlab.client.ClientStatFlash;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Golden flash on the vanilla hearts and food bar (see ClientStatFlash). A white mask of each
 * filled icon is drawn over it, tinted gold at the pulse's opacity, at exactly the position
 * vanilla just used (so heart rows, low-health jiggle, the Regeneration wave and the food
 * shake all carry over). Empty containers and the damage-blink hearts are left alone.
 */
@Mixin(Hud.class)
public abstract class HudStatFlashMixin {

    @Unique
    private static final Identifier TERRAINLAB$HEART_FULL = Identifier.fromNamespaceAndPath("terrainlab", "hud/stat_flash/heart_full");
    @Unique
    private static final Identifier TERRAINLAB$HEART_HALF = Identifier.fromNamespaceAndPath("terrainlab", "hud/stat_flash/heart_half");
    @Unique
    private static final Identifier TERRAINLAB$FOOD_FULL = Identifier.fromNamespaceAndPath("terrainlab", "hud/stat_flash/food_full");
    @Unique
    private static final Identifier TERRAINLAB$FOOD_HALF = Identifier.fromNamespaceAndPath("terrainlab", "hud/stat_flash/food_half");

    @Inject(method = "extractHeart", at = @At("TAIL"))
    private void terrainlab$flashHeart(GuiGraphicsExtractor graphics, Hud.HeartType type, int xo, int yo,
                                       boolean isHardcore, boolean blinks, boolean half, CallbackInfo ci) {
        if (type == Hud.HeartType.CONTAINER || blinks) {
            return;
        }
        float intensity = ClientStatFlash.health();
        if (intensity <= 0.0F) {
            return;
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, half ? TERRAINLAB$HEART_HALF : TERRAINLAB$HEART_FULL,
                xo, yo, 9, 9, ClientStatFlash.goldColor(intensity));
    }

    @WrapOperation(
            method = "extractFood",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"
            )
    )
    private void terrainlab$flashFood(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite,
                                      int x, int y, int w, int h, Operation<Void> original) {
        original.call(graphics, pipeline, sprite, x, y, w, h);
        float intensity = ClientStatFlash.hunger();
        if (intensity <= 0.0F) {
            return;
        }
        // Vanilla draws the empty icon first, then full or half (normal or Hunger-effect variant).
        String path = sprite.getPath();
        Identifier mask;
        if (path.startsWith("hud/food_full")) {
            mask = TERRAINLAB$FOOD_FULL;
        } else if (path.startsWith("hud/food_half")) {
            mask = TERRAINLAB$FOOD_HALF;
        } else {
            return;
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, mask, x, y, w, h, ClientStatFlash.goldColor(intensity));
    }
}
