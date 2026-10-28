package com.terrainlab.mixin;

import com.terrainlab.character.CharacterSession;
import net.minecraft.client.server.IntegratedPlayerList;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Singleplayer characters: level.dat's "Player" tag always holds character 1.
 * <p>
 * {@code MinecraftServer#saveAllChunks} writes {@code PlayerList#getSingleplayerData()} into
 * level.dat; {@code IntegratedPlayerList} returns the host's last saved data there, and a null
 * keeps the tag level.dat was loaded with ({@code PrimaryLevelData#createTag}). While another
 * character is the host this returns null, so level.dat keeps character 1's data: the world's main
 * progress stays the one vanilla (or this mod without the character list) would load, and
 * {@code PlayerListCharacterDataMixin} never hands character 1's tag to anyone else. The other
 * characters' data is saved in their own {@code playerdata/<uuid>.dat} as usual.
 */
@Mixin(IntegratedPlayerList.class)
public abstract class IntegratedPlayerListCharacterMixin {

    @Inject(method = "getSingleplayerData", at = @At("HEAD"), cancellable = true)
    private void terrainlab$keepPrimaryInLevelDat(CallbackInfoReturnable<CompoundTag> cir) {
        CharacterSession.Selection character = CharacterSession.forServer(((IntegratedPlayerList) (Object) this).getServer());
        if (character != null && !character.primary()) {
            cir.setReturnValue(null);
        }
    }
}
