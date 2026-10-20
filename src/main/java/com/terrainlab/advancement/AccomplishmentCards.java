package com.terrainlab.advancement;

import com.terrainlab.TerrainLab;
import com.terrainlab.network.AccomplishmentCardPacket;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Server half of the accomplishment card: builds the card when an End return newly earns at
 * least one accomplishment (see EndReturnChallenges), stores it on the player and sends it to the
 * client; and the {@code /accomplishments} command (no permission needed) that sends the stored
 * card again.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class AccomplishmentCards {

    private AccomplishmentCards() {
    }

    /** Whether the player already has this accomplishment's advancement (false if it is missing). */
    public static boolean has(ServerPlayer player, Accomplishment accomplishment) {
        AdvancementHolder holder = player.level().getServer().getAdvancements()
                .get(Identifier.fromNamespaceAndPath(TerrainLab.MODID, accomplishment.advancementPath()));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /**
     * Called after the End-return awards. {@code fresh} holds the accomplishments that went from
     * not done to done just now; with none, nothing happens (no card on a repeat finish).
     */
    public static void onAwarded(ServerPlayer player, Set<Accomplishment> fresh, int disqualified) {
        if (fresh.isEmpty()) {
            return;
        }
        Accomplishment title = null;
        List<String> earned = new ArrayList<>();
        List<String> freshIds = new ArrayList<>();
        for (Accomplishment a : Accomplishment.values()) {
            if (fresh.contains(a)) {
                freshIds.add(a.id());
                if (title == null) {
                    title = a;
                }
            }
            if (fresh.contains(a) || has(player, a)) {
                earned.add(a.id());
            }
        }

        MinecraftServer server = player.level().getServer();
        ServerStatsCounter stats = player.getStats();
        long distanceCm = 0L;
        for (Identifier stat : BuiltInRegistries.CUSTOM_STAT) {
            if (stat.getPath().endsWith("_one_cm")) {
                distanceCm += stats.getValue(Stats.CUSTOM.get(stat));
            }
        }

        AccomplishmentCard card = new AccomplishmentCard(
                title.id(),
                earned,
                freshIds,
                player.getName().getString(),
                server.getWorldData().getLevelName(),
                server.overworld().getSeed(),
                server.isHardcore(),
                server.overworld().getDifficulty().getId(),
                (disqualified & ChallengeEligibility.CHEATS) != 0,
                server.overworld().getDayTime() / 24000L + 1L,
                stats.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)),
                stats.getValue(Stats.CUSTOM.get(Stats.DEATHS)),
                stats.getValue(Stats.CUSTOM.get(Stats.MOB_KILLS)),
                distanceCm,
                LocalDate.now().toEpochDay());

        player.setData(ModAttachments.ACCOMPLISHMENT_CARD.get(), card);
        PacketDistributor.sendToPlayer(player, new AccomplishmentCardPacket(card));
        player.sendSystemMessage(Component.translatable("message.terrainlab.accomplishment.hint",
                Component.keybind("key.screenshot")));
    }

    /** Creates the empty set used by EndReturnChallenges to collect newly earned accomplishments. */
    public static Set<Accomplishment> newSet() {
        return EnumSet.noneOf(Accomplishment.class);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("accomplishments")
                .executes(ctx -> show(ctx.getSource())));
    }

    private static int show(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("commands.terrainlab.accomplishments.player_only"));
            return 0;
        }
        AccomplishmentCard card = player.getData(ModAttachments.ACCOMPLISHMENT_CARD.get());
        if (card.isEmpty()) {
            source.sendFailure(Component.translatable("commands.terrainlab.accomplishments.none"));
            return 0;
        }
        PacketDistributor.sendToPlayer(player, new AccomplishmentCardPacket(card));
        return 1;
    }
}
