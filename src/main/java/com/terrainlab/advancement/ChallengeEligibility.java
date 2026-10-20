package com.terrainlab.advancement;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PermissionsChangedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Permanent per-player disqualification flags for the challenge advancements (Pacifist, Nomad;
 * Survivor and Ultimate Survivor use CHEATS and CREATIVE).
 * A flag is set the first time its condition is seen while the player is in the world and never
 * clears. Stored as a bitmask in {@link ModAttachments#CHALLENGE_DISQUALIFIED} (serialized, copied
 * on death), so it is per player and per world.
 * <p>
 * Checked on login, on game-mode change, on permission (op) change, on difficulty change
 * (MinecraftServerDifficultyMixin) and every {@link #CHECK_INTERVAL} ticks, which also catches the
 * cases without a server-side event (e.g. the singleplayer "Allow Commands" toggle).
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class ChallengeEligibility {

    /** Had operator / cheat permission (commands at gamemaster level). */
    public static final int CHEATS = 1;
    /** Was in Creative mode. */
    public static final int CREATIVE = 1 << 1;
    /** World difficulty was Easy. */
    public static final int EASY = 1 << 2;
    /** World difficulty was Peaceful. */
    public static final int PEACEFUL = 1 << 3;

    private static final int CHECK_INTERVAL = 20;

    private ChallengeEligibility() {
    }

    /** Re-checks the current state, then returns true if none of the {@code forbidden} flags was ever set. */
    public static boolean isEligible(ServerPlayer player, int forbidden) {
        update(player);
        return (flags(player) & forbidden) == 0;
    }

    public static int flags(ServerPlayer player) {
        return player.getData(ModAttachments.CHALLENGE_DISQUALIFIED.get());
    }

    public static void update(ServerPlayer player) {
        record(player, player.isCreative(),
                player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
    }

    /** Re-checks every online player (difficulty is server-wide). */
    public static void updateAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            update(player);
        }
    }

    private static void record(ServerPlayer player, boolean creative, boolean cheats) {
        int old = flags(player);
        int flags = old;
        if (cheats) {
            flags |= CHEATS;
        }
        if (creative) {
            flags |= CREATIVE;
        }
        Difficulty difficulty = player.level().getDifficulty();
        if (difficulty == Difficulty.EASY) {
            flags |= EASY;
        } else if (difficulty == Difficulty.PEACEFUL) {
            flags |= PEACEFUL;
        }
        if (flags != old) {
            player.setData(ModAttachments.CHALLENGE_DISQUALIFIED.get(), flags);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            update(player);
        }
    }

    // Fired before the change; LOWEST so a mod that cancels it gets its say first.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGameModeChange(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            record(player, event.getNewGameMode() == GameType.CREATIVE,
                    player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
        }
    }

    // Fired before the op level changes (/op, /deop).
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPermissionsChanged(PermissionsChangedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            record(player, player.isCreative(),
                    event.getNewLevel().hasPermission(Permissions.COMMANDS_GAMEMASTER));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % CHECK_INTERVAL == 0) {
            update(player);
        }
    }
}
