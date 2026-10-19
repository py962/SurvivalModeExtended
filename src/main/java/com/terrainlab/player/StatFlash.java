package com.terrainlab.player;

import com.terrainlab.TerrainLab;
import com.terrainlab.network.PlayerEnergyPacket;
import com.terrainlab.network.StatFlashPacket;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Golden HUD flash for discrete, event-driven gains to health (incl. absorption), hunger (food
 * level or saturation) and energy. Gradual changes (natural/Regeneration healing, PendingHealSystem
 * steps, energy ticking) never call this; delayed food healing flashes once, when eaten.
 *
 * <p>Sources: item use (any food/drink/potion, compared before vs. after consumption, see
 * {@link #onUseTick}/{@link #onUseFinish}), HEALTH-tier food with delayed healing
 * (ModFoodEffectsHandler), waking (PlayerRestingSystem), cushion resting
 * (PlayerCushionRestingSystem), Instant Health from any source (HealOrHarmStatFlashMixin) and
 * Absorption gains incl. totems (AbsorptionStatFlashMixin). Repeat sends within one server tick
 * are merged, so a golden apple's item-use and Absorption hooks produce a single packet.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class StatFlash {

    public static final int HEALTH = 1;
    public static final int HUNGER = 2;
    public static final int ENERGY = 4;

    /** Per player: {server tick of last send, mask already sent that tick}. */
    private static final Map<ServerPlayer, long[]> SENT_THIS_TICK = new WeakHashMap<>();
    /** Per player: stats captured on the last item-use tick, just before consumption. */
    private static final Map<ServerPlayer, Snapshot> USE_SNAPSHOTS = new WeakHashMap<>();

    private StatFlash() {
    }

    /** Stat values to compare against after an event, to flash only actual gains. */
    public record Snapshot(float health, float absorption, int food, float saturation, float energy) {
    }

    public static Snapshot capture(ServerPlayer player) {
        return new Snapshot(
                player.getHealth(),
                player.getAbsorptionAmount(),
                player.getFoodData().getFoodLevel(),
                player.getFoodData().getSaturationLevel(),
                player.getData(ModAttachments.PLAYER_ENERGY.get()));
    }

    /** Flashes every stat that increased since {@code before}. */
    public static void sendGains(ServerPlayer player, Snapshot before) {
        Snapshot now = capture(player);
        int mask = 0;
        if (now.health() > before.health() || now.absorption() > before.absorption()) mask |= HEALTH;
        if (now.food() > before.food() || now.saturation() > before.saturation()) mask |= HUNGER;
        if (now.energy() > before.energy()) mask |= ENERGY;
        send(player, mask);
    }

    /** Sends a flash for the given stats, skipping bits already sent to this player this tick. */
    public static void send(ServerPlayer player, int mask) {
        if (mask == 0 || player.isCreative() || player.isSpectator()) {
            return;
        }
        long tick = player.level().getServer().getTickCount();
        long[] sent = SENT_THIS_TICK.computeIfAbsent(player, p -> new long[]{Long.MIN_VALUE, 0});
        if (sent[0] != tick) {
            sent[0] = tick;
            sent[1] = 0;
        }
        int fresh = mask & ~(int) sent[1];
        if (fresh == 0) {
            return;
        }
        sent[1] |= fresh;
        if ((fresh & ENERGY) != 0) {
            // Energy normally syncs every 10 ticks; push it now so the bar fills as it flashes.
            PacketDistributor.sendToPlayer(player,
                    new PlayerEnergyPacket(player.getData(ModAttachments.PLAYER_ENERGY.get())));
        }
        PacketDistributor.sendToPlayer(player, new StatFlashPacket(fresh));
    }

    // Item use: the Tick event fires in the same call as, and just before, consumption on the
    // final tick, so its snapshot excludes gradual changes during the eating animation.

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            USE_SNAPSHOTS.put(player, capture(player));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            USE_SNAPSHOTS.put(player, capture(player));
        }
    }

    /** LOWEST so every mod handler (ModFoodEffectsHandler etc.) has applied its effects first. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Snapshot before = USE_SNAPSHOTS.remove(player);
            if (before != null) {
                sendGains(player, before);
            }
        }
    }

    @SubscribeEvent
    public static void onUseStop(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            USE_SNAPSHOTS.remove(player);
        }
    }
}
