package com.terrainlab.advancement;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Nomad: records the chunk of every COMPLETED sleep (one that ends the night) and flags a repeat.
 * <p>
 * A completed sleep is a wake-up with wakeImmediately == false and updateLevel == false: the only
 * server-side caller passing those is ServerLevel#wakeUpAllPlayers, run when enough players have
 * slept long enough to skip the night. Every way of sleeping in this mod goes through vanilla's
 * sleeping-player list and is woken there: vanilla beds (including beds claimed by villagers),
 * Borrowing dreams, houseboat beds and howdah beds. Leaving the bed early, being woken by damage or
 * by the morning without a skip, and setting the spawn point are other callers and don't count.
 * Cushion sitting is not sleeping.
 * <p>
 * The chunk is the one holding the bed the player was lying in (getSleepingPos), per dimension.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class NomadSleepTracker {

    /**
     * Sleeping ticks that must pass, for a player who has been warned, before their sleep may end the night.
     * Vanilla already makes a sleeper wait 100 ticks (and caps its own timer there), so this adds a few
     * seconds on top, during which leaving the bed keeps the Nomad run intact.
     */
    public static final int CANCEL_WINDOW_TICKS = 200;
    private static final int COUNTDOWN_INTERVAL_TICKS = 20;

    /** Per sleeping player: whether the bed is in an already-slept-in chunk, and ticks asleep (server thread only). */
    private static final class SleepState {
        final boolean warned;
        int ticks;

        SleepState(boolean warned) {
            this.warned = warned;
        }
    }

    private static final Map<UUID, SleepState> SLEEPING = new HashMap<>();

    private NomadSleepTracker() {
    }

    /**
     * True while a warned player's cancel window is still open: they must not count as sleeping long enough,
     * or the night could be skipped, and the sleep recorded, before they have had time to leave the bed.
     */
    public static boolean holdsSleep(ServerPlayer player) {
        SleepState state = SLEEPING.get(player.getUUID());
        return state != null && state.warned && state.ticks < CANCEL_WINDOW_TICKS;
    }

    /** True while the Nomad run can still succeed: no repeated chunk and no disqualifier. */
    public static boolean runIntact(ServerPlayer player) {
        return !player.getData(ModAttachments.NOMAD_SLEEP_RECORD.get()).repeated()
                && ChallengeEligibility.isEligible(player, EndReturnChallenges.NOMAD_FORBIDDEN);
    }

    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.wakeImmediately() || event.updateLevel()) {
            return;
        }
        BlockPos bed = player.getSleepingPos().orElse(player.blockPosition());
        String dimension = player.level().dimension().identifier().toString();
        NomadSleepRecord record = player.getData(ModAttachments.NOMAD_SLEEP_RECORD.get());
        NomadSleepRecord updated = record.withSleep(dimension, ChunkPos.asLong(bed));
        if (updated != record) {
            player.setData(ModAttachments.NOMAD_SLEEP_RECORD.get(), updated);
        }
    }

    /**
     * When the bed is in a chunk already slept in (and the run is intact), counts down the cancel window on
     * the action bar once a second. Never blocks lying down; leaving the bed before the window ends is not a
     * completed sleep, so it keeps the Nomad run.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        if (!player.isSleeping()) {
            SLEEPING.remove(id);
            return;
        }
        SleepState state = SLEEPING.get(id);
        if (state == null) {
            BlockPos bed = player.getSleepingPos().orElse(player.blockPosition());
            String dimension = player.level().dimension().identifier().toString();
            boolean warned = player.getData(ModAttachments.NOMAD_SLEEP_RECORD.get()).contains(dimension, ChunkPos.asLong(bed))
                    && runIntact(player);
            state = new SleepState(warned);
            SLEEPING.put(id, state);
        }
        if (state.warned && state.ticks < CANCEL_WINDOW_TICKS) {
            if (state.ticks % COUNTDOWN_INTERVAL_TICKS == 0) {
                int seconds = (CANCEL_WINDOW_TICKS - state.ticks) / COUNTDOWN_INTERVAL_TICKS;
                player.displayClientMessage(Component.translatable("message.terrainlab.nomad.slept_here_before", seconds), true);
            }
            state.ticks++;
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SLEEPING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SLEEPING.clear();
    }
}
