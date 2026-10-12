package com.terrainlab.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.BiomeAmbientSoundsHandler;
import net.minecraft.world.attribute.AmbientSounds;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;
import java.util.Optional;

/**
 * Silences biome loop and additions sounds while the player's eyes are underwater, using the same
 * test as vanilla's underwater ambience. Dropping the loop lets vanilla cross-fade it out and back
 * in on surfacing; the cave mood sound is kept.
 */
@Mixin(BiomeAmbientSoundsHandler.class)
public abstract class BiomeAmbientUnderwaterMuteMixin {

    @Shadow
    @Final
    private LocalPlayer player;

    @ModifyVariable(method = "tick", at = @At("STORE"), ordinal = 0, require = 1)
    private AmbientSounds terrainlab$muteBiomeAmbienceUnderwater(AmbientSounds sounds) {
        if (this.player.isUnderWater()) {
            return new AmbientSounds(Optional.empty(), sounds.mood(), List.of());
        }
        return sounds;
    }
}
