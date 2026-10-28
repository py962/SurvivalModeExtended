package com.terrainlab.mixin;

import com.terrainlab.character.CharacterSession;
import net.minecraft.core.UUIDUtil;
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
 * joining profile's name matches the host ({@code isSingleplayerOwner} compares names), it returns
 * the "Player" tag stored in level.dat ({@code WorldData#getLoadedPlayerTag}) instead of the
 * joining UUID's {@code playerdata/<uuid>.dat}. Every character shares the account's name, so
 * without this hook every character would load that one tag. With a character session bound to
 * this server:
 * <ul>
 *   <li>the level.dat tag is still used when it belongs to this character (its "UUID"), or, for
 *       character 1 only, when it belongs to no UUID this world has used for a character (a world
 *       from before characters, played under another UUID) so that progress is not lost;</li>
 *   <li>otherwise the character's own {@code playerdata/<uuid>.dat} is loaded, and a character
 *       without one starts as a new player.</li>
 * </ul>
 * {@code IntegratedPlayerListCharacterMixin} keeps that level.dat tag character 1's.
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

        CompoundTag levelPlayer = this.server.getWorldData().getLoadedPlayerTag();
        if (levelPlayer != null) {
            UUID tagUuid = levelPlayer.getIntArray("UUID")
                    .filter(ints -> ints.length == 4)
                    .map(UUIDUtil::uuidFromIntArray)
                    .orElse(null);
            if (character.uuid().equals(tagUuid)
                    || character.primary() && (tagUuid == null || !character.known().contains(tagUuid))) {
                return; // vanilla returns the level.dat tag (the host passes isSingleplayerOwner by name)
            }
        }
        cir.setReturnValue(this.playerIo.load(nameAndId));
    }
}
