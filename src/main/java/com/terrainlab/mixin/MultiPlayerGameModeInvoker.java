package com.terrainlab.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the off-hand strike sync the selected hotbar slot first, as vanilla attack/interact do. */
@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeInvoker {

    @Invoker("ensureHasSentCarriedItem")
    void terrainlab$ensureHasSentCarriedItem();
}
