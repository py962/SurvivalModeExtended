package com.terrainlab.mixin;

import com.mojang.authlib.GameProfile;
import com.terrainlab.client.character.CharacterClient;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Singleplayer characters: the active character is drawn with the skin chosen on its naming screen
 * (first person, third person, inventory, tab list). {@code AbstractClientPlayer#getSkin} reads
 * the skin through {@code PlayerInfo#getSkin}, so this one hook covers every place the game draws
 * the player. Characters without a chosen skin, and every other player, keep vanilla's skin.
 */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoCharacterSkinMixin {

    @Shadow
    public abstract GameProfile getProfile();

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void terrainlab$characterSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerSkin skin = CharacterClient.skinFor(this.getProfile().id());
        if (skin != null) {
            cir.setReturnValue(skin);
        }
    }
}
