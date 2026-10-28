package com.terrainlab.advancement;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Set;

/**
 * The accomplishments judged when a living player returns from the End to the Overworld.
 * <ul>
 *   <li><b>Survivor</b> - non-hardcore world; in Survival or Adventure now; never cheats, Creative
 *   or an easier game rule (any difficulty).</li>
 *   <li><b>Ultimate Survivor</b> - the same in a hardcore ("Ultra-Hardcore") world, replacing
 *   Survivor there.</li>
 *   <li><b>Pacifist</b> - vanilla mob_kills and player_kills both zero; never cheats, Creative,
 *   an easier game rule or Easy (Peaceful allowed).</li>
 *   <li><b>Nomad</b> - never completed a sleep twice in the same chunk (NomadSleepTracker); never
 *   cheats, Creative, an easier game rule, Easy or Peaceful.</li>
 * </ul>
 * If at least one of them is newly earned, AccomplishmentCards sends the accomplishment card.
 * <p>
 * The two ways a player leaves the End:
 * <ul>
 *   <li>The exit portal before the credits have been seen: vanilla removes the player, shows
 *   the credits, then rebuilds them through the "end conquered" respawn. That fires only
 *   PlayerRespawnEvent with isEndConquered() (never set by a death respawn).</li>
 *   <li>Everything else (the exit portal after the credits, redstone portals, commands): a
 *   ServerPlayer.teleport across dimensions, which fires PlayerChangedDimensionEvent.</li>
 * </ul>
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class EndReturnChallenges {

    static final int SURVIVOR_FORBIDDEN =
            ChallengeEligibility.CHEATS | ChallengeEligibility.CREATIVE | ChallengeEligibility.GAME_RULES;
    static final int PACIFIST_FORBIDDEN = SURVIVOR_FORBIDDEN | ChallengeEligibility.EASY;
    static final int NOMAD_FORBIDDEN = PACIFIST_FORBIDDEN | ChallengeEligibility.PEACEFUL;

    private EndReturnChallenges() {
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getFrom() == Level.END && event.getTo() == Level.OVERWORLD
                && event.getEntity() instanceof ServerPlayer player) {
            onReturnedFromEnd(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() && event.getEntity() instanceof ServerPlayer player
                && player.level().dimension() == Level.OVERWORLD) {
            onReturnedFromEnd(player);
        }
    }

    private static void onReturnedFromEnd(ServerPlayer player) {
        if (!player.isAlive()) {
            return;
        }
        ChallengeEligibility.update(player);
        int disqualified = ChallengeEligibility.flags(player);

        Set<Accomplishment> fresh = AccomplishmentCards.newSet();

        if ((disqualified & SURVIVOR_FORBIDDEN) == 0 && player.gameMode().isSurvival()) {
            award(player, player.level().getServer().isHardcore()
                    ? Accomplishment.ULTIMATE_SURVIVOR : Accomplishment.SURVIVOR, fresh);
        }

        int mobKills = player.getStats().getValue(Stats.CUSTOM.get(Stats.MOB_KILLS));
        int playerKills = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS));
        if (mobKills == 0 && playerKills == 0 && (disqualified & PACIFIST_FORBIDDEN) == 0) {
            award(player, Accomplishment.PACIFIST, fresh);
        }

        if (!player.getData(ModAttachments.NOMAD_SLEEP_RECORD.get()).repeated()
                && (disqualified & NOMAD_FORBIDDEN) == 0) {
            award(player, Accomplishment.NOMAD, fresh);
        }

        AccomplishmentCards.onAwarded(player, fresh, disqualified);
    }

    /** Fires the event and records the accomplishment in {@code fresh} if it was not done before. */
    private static void award(ServerPlayer player, Accomplishment accomplishment, Set<Accomplishment> fresh) {
        boolean had = AccomplishmentCards.has(player, accomplishment);
        ModAdvancements.award(player, accomplishment.id());
        if (!had && AccomplishmentCards.has(player, accomplishment)) {
            fresh.add(accomplishment);
        }
    }
}
