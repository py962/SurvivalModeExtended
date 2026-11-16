package com.terrainlab.mixin;

import com.terrainlab.hearth.HearthCompat;
import com.terrainlab.hearth.VillagerSittingState;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerRenderer.class)
public abstract class VillagerRendererSittingMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/npc/villager/Villager;Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;F)V",
            at = @At("TAIL"), require = 1)
    private void terrainlab$markSitting(Villager villager, VillagerRenderState state, float partialTicks, CallbackInfo ci) {
        ((VillagerSittingState) state).terrainlab$setSitting(
                villager.getVehicle() != null && HearthCompat.isCushion(villager.getVehicle()));
    }
}
