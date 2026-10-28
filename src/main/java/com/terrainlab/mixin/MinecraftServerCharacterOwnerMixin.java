package com.terrainlab.mixin;

import com.terrainlab.character.CharacterSession;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.UUID;

/**
 * Singleplayer characters: level.dat's {@code singleplayer_uuid} always names character 1.
 * <p>
 * {@code MinecraftServer#saveAllChunks} writes the host's UUID into level.dat
 * ({@code LevelStorageAccess#saveDataTag(WorldData, UUID)}); vanilla uses it to pick the owner's
 * player file next time and to choose where to preload chunks. During a character session the host
 * is the character, so this keeps it pointing at character 1 instead: the world's main progress
 * stays the one vanilla (or this mod without the character list) would load, and
 * {@code PlayerListCharacterDataMixin} never mistakes another character for legacy owner data.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerCharacterOwnerMixin {

    @ModifyArg(
            method = "saveAllChunks",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;saveDataTag(Lnet/minecraft/world/level/storage/WorldData;Ljava/util/UUID;)V"),
            index = 1)
    private UUID terrainlab$keepPrimaryOwner(UUID singleplayerUuid) {
        CharacterSession.Selection character = CharacterSession.forServer((MinecraftServer) (Object) this);
        return character == null ? singleplayerUuid : character.primaryUuid();
    }
}
