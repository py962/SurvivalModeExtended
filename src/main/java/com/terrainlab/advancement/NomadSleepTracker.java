package com.terrainlab.advancement;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

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

    private NomadSleepTracker() {
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

    /** Warns once per lie-down when the bed is in a chunk already slept in. Never blocks sleeping. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean sleeping = player.isSleeping();
        boolean wasSleeping = player.getData(ModAttachments.NOMAD_WAS_SLEEPING.get());
        if (sleeping == wasSleeping) {
            return;
        }
        player.setData(ModAttachments.NOMAD_WAS_SLEEPING.get(), sleeping);
        if (!sleeping) {
            return;
        }
        BlockPos bed = player.getSleepingPos().orElse(player.blockPosition());
        String dimension = player.level().dimension().identifier().toString();
        if (player.getData(ModAttachments.NOMAD_SLEEP_RECORD.get()).contains(dimension, ChunkPos.asLong(bed))
                && runIntact(player)) {
            player.displayClientMessage(Component.translatable("message.terrainlab.nomad.slept_here_before"), true);
        }
    }
}
