package com.terrainlab.mixin;

import com.terrainlab.hearth.VillagerSittingState;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(VillagerRenderState.class)
public abstract class VillagerRenderStateSittingMixin implements VillagerSittingState {

    @Unique
    private boolean terrainlab$sitting;

    @Override
    public boolean terrainlab$isSitting() {
        return this.terrainlab$sitting;
    }

    @Override
    public void terrainlab$setSitting(boolean sitting) {
        this.terrainlab$sitting = sitting;
    }
}
