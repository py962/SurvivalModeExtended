package com.terrainlab.mixin;

import com.terrainlab.client.VillagerMoodClient;
import com.terrainlab.village.VillagerMoodService;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tints a hot villager's skin red. {@code getModelTint} affects only the base villager model, so the
 * clothing layers stay untinted and only the face and hands flush.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class VillagerHeatTintMixin {

    /** Multiplies the skin: red kept, green and blue pulled down. */
    private static final int HOT_TINT = ARGB.colorFromFloat(1.0F, 1.0F, 0.78F, 0.72F);

    @Inject(method = "getModelTint", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$heatFlush(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
        if (!((Object) this instanceof VillagerRenderer) || !(state instanceof VillagerRenderState villager)) {
            return;
        }
        Integer flags = villager.getRenderData(VillagerMoodClient.MOOD_FLAGS);
        if (flags != null && (flags & VillagerMoodService.HOT) != 0) {
            cir.setReturnValue(HOT_TINT);
        }
    }
}
