package com.terrainlab.mixin;

import com.terrainlab.character.CharacterSession;
import com.terrainlab.client.character.CharacterClient;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Singleplayer characters: the integrated server logs the local player in as the chosen character.
 * <p>
 * The {@code IntegratedServer} constructor sets the singleplayer profile to
 * {@code Minecraft#getGameProfile()} (the account). When the world was opened through the
 * character screen, this replaces it with the character's profile: same NAME and skin properties
 * as the account, the character's UUID. {@code ServerLoginPacketListenerImpl#handleHello} hands
 * the singleplayer profile to the connecting client whose name matches it, and the client builds
 * its {@code LocalPlayer} from the profile the server sends back, so this one substitution makes
 * both sides use the character's UUID; the account itself ({@code Minecraft#getUser}) is never
 * touched, so multiplayer, Realms and other worlds are unaffected.
 * <p>
 * Runs on the client thread ({@code MinecraftServer.spin} calls the constructor there), before the
 * server thread starts and before anything reads the profile.
 */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerCharacterMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void terrainlab$useCharacterProfile(CallbackInfo ci) {
        IntegratedServer self = (IntegratedServer) (Object) this;
        CharacterSession.Selection character = CharacterSession.claim(self);
        if (character != null) {
            self.setSingleplayerProfile(character.profile());
            CharacterClient.onCharacterSessionStarted(character);
        }
    }
}
