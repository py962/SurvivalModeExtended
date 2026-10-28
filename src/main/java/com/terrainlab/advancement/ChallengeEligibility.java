package com.terrainlab.advancement;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PermissionsChangedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.GameRuleChangedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Permanent per-player disqualification flags for the challenge advancements (Pacifist, Nomad;
 * Survivor and Ultimate Survivor use CHEATS, CREATIVE and GAME_RULES).
 * A flag is set the first time its condition is seen while the player is in the world and never
 * clears. Stored as a bitmask in {@link ModAttachments#CHALLENGE_DISQUALIFIED} (serialized, copied
 * on death), so it is per player and per world.
 * <p>
 * GAME_RULES is set when any rule in {@link #EASIER_RULES} is at its easier value; the ids of the
 * rules seen are kept in {@link ModAttachments#CHALLENGE_GAME_RULES} and each is announced to the
 * player once in chat.
 * <p>
 * Checked on login, on game-mode change, on permission (op) change, on difficulty change
 * (MinecraftServerDifficultyMixin), on game rule change and every {@link #CHECK_INTERVAL} ticks,
 * which also catches the cases without a server-side event (e.g. the singleplayer "Allow Commands"
 * toggle).
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
    /** A world game rule was at an easier value (see {@link #EASIER_RULES}). */
    public static final int GAME_RULES = 1 << 4;

    /** Vanilla default random tick speed; anything faster makes farming easier. */
    private static final int DEFAULT_RANDOM_TICK_SPEED = 3;

    private record EasierRule(GameRule<?> rule, Predicate<GameRules> easier) {
        static EasierRule whenFalse(GameRule<Boolean> rule) {
            return new EasierRule(rule, rules -> !rules.get(rule));
        }
    }

    /** The game rules whose easier value disqualifies, with that value. */
    private static final List<EasierRule> EASIER_RULES = List.of(
            new EasierRule(GameRules.KEEP_INVENTORY, rules -> rules.get(GameRules.KEEP_INVENTORY)),
            EasierRule.whenFalse(GameRules.SPAWN_MOBS),
            EasierRule.whenFalse(GameRules.FALL_DAMAGE),
            EasierRule.whenFalse(GameRules.FIRE_DAMAGE),
            EasierRule.whenFalse(GameRules.DROWNING_DAMAGE),
            EasierRule.whenFalse(GameRules.FREEZE_DAMAGE),
            EasierRule.whenFalse(GameRules.ADVANCE_TIME),
            EasierRule.whenFalse(GameRules.SPAWN_PHANTOMS),
            EasierRule.whenFalse(GameRules.RAIDS),
            EasierRule.whenFalse(GameRules.SPAWN_PATROLS),
            EasierRule.whenFalse(GameRules.SPAWN_WARDENS),
            EasierRule.whenFalse(GameRules.MOB_GRIEFING),
            new EasierRule(GameRules.RANDOM_TICK_SPEED,
                    rules -> rules.get(GameRules.RANDOM_TICK_SPEED) > DEFAULT_RANDOM_TICK_SPEED),
            EasierRule.whenFalse(GameRules.ADVANCE_WEATHER));

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

    /** Re-checks every online player (difficulty and game rules are server-wide). */
    public static void updateAll(MinecraftServer server) {
        if (server.getPlayerList() == null) {
            return;
        }
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
        if (recordGameRules(player)) {
            flags |= GAME_RULES;
        }
        if (flags != old) {
            player.setData(ModAttachments.CHALLENGE_DISQUALIFIED.get(), flags);
        }
    }

    /**
     * Adds any rule now at its easier value to the player's list (announcing it once), then
     * returns true if the list is non-empty.
     */
    private static boolean recordGameRules(ServerPlayer player) {
        List<String> seen = player.getData(ModAttachments.CHALLENGE_GAME_RULES.get());
        GameRules rules = player.level().getGameRules();
        List<String> updated = null;
        for (EasierRule check : EASIER_RULES) {
            String id = check.rule().id();
            if (!seen.contains(id) && check.easier().test(rules)) {
                if (updated == null) {
                    updated = new ArrayList<>(seen);
                }
                updated.add(id);
                player.sendSystemMessage(Component.translatable(
                        "message.terrainlab.challenge.game_rule_disabled", id).withStyle(ChatFormatting.YELLOW));
            }
        }
        if (updated != null) {
            player.setData(ModAttachments.CHALLENGE_GAME_RULES.get(), List.copyOf(updated));
            return true;
        }
        return !seen.isEmpty();
    }

    // Fired after the value is stored (/gamerule, the Game Rules screen).
    @SubscribeEvent
    public static void onGameRuleChanged(GameRuleChangedEvent event) {
        updateAll(event.getServer());
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
