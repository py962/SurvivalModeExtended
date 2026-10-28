package com.terrainlab.mixin;

import com.terrainlab.character.CharacterSession;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.UUID;

/**
 * Singleplayer characters: loads the host's player data by the CHARACTER's own UUID.
 * <p>
 * Vanilla {@code PlayerList#loadPlayerData} treats the singleplayer owner specially: when the
 * joining profile's name matches the host ({@code isSingleplayerOwner} compares names), it loads
 * the data of the UUID stored in level.dat ({@code singleplayer_uuid}, the last host) instead of the
 * joining UUID, so a world keeps its progress if the account's UUID changes. Every character shares
 * the account's name, so without this hook each character would load whichever character played
 * last. With a character session bound to this server:
 * <ul>
 *   <li>the character's own {@code players/data/<uuid>.dat} is loaded when it exists;</li>
 *   <li>otherwise, only for character 1 and only when level.dat names a UUID this world has never
 *       used for a character (a world from before characters, played under another UUID), the
 *       vanilla redirect still runs so that progress is not lost;</li>
 *   <li>otherwise the character has no data yet and starts as a new player.</li>
 * </ul>
 * Without a session (dedicated servers, LAN guests, new/demo worlds) vanilla runs unchanged.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListCharacterDataMixin {

    @Shadow @Final private MinecraftServer server;
    @Shadow @Final private PlayerDataStorage playerIo;

    @Inject(method = "loadPlayerData", at = @At("HEAD"), cancellable = true)
    private void terrainlab$loadCharacterData(NameAndId nameAndId, CallbackInfoReturnable<Optional<CompoundTag>> cir) {
        CharacterSession.Selection character = CharacterSession.forServer(this.server);
        if (character == null || !character.uuid().equals(nameAndId.id())) return;

        Optional<CompoundTag> own = this.playerIo.load(nameAndId);
        if (own.isPresent()) {
            cir.setReturnValue(own);
            return;
        }
        UUID lastOwner = this.server.getWorldData().getSinglePlayerUUID();
        if (character.primary() && lastOwner != null && !character.known().contains(lastOwner)) {
            return; // legacy owner data: let vanilla redirect to it
        }
        cir.setReturnValue(Optional.empty());
    }
}
