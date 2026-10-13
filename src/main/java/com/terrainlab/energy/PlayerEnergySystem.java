package com.terrainlab.energy;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.terrainlab.energy.PlayerEnergyConstants.*;

/**
 * Per-tick energy drain for sprinting, raising a heavy wood shield (lightness 0-1), jumping and
 * attacking/mining, plus HUD sync.
 *
 * <p>Each actual jump drains once ({@link #onJump}, fired when the server detects a jump from the
 * player's movement, as vanilla does for jump exhaustion). Attack/mine drains once on press and then every
 * MINING_DRAIN_INTERVAL_TICKS while held. PLAYER_ENERGY is not client-synced, so it is sent to the
 * client every 10 ticks via PlayerEnergyPacket.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class PlayerEnergySystem {

    private PlayerEnergySystem() {
    }

    public static void tick(ServerPlayer player) {
        // Energy is frozen in creative/spectator.
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        float energy = player.getData(ModAttachments.PLAYER_ENERGY.get());

        if (player.isSprinting()) {
            energy -= SPRINT_DRAIN_PER_TICK;
        }

        energy -= com.terrainlab.event.ShieldCombatEvents.raisedShieldEnergyDrain(player);

        energy -= attackHoldDrain(player);

        energy = Math.max(MIN_ENERGY, Math.min(MAX_ENERGY, energy));
        player.setData(ModAttachments.PLAYER_ENERGY.get(), energy);

        if (player.level().getGameTime() % 10 == 0) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    player, new com.terrainlab.network.PlayerEnergyPacket(energy));
        }
    }

    /**
     * A new life starts at SPAWN_ENERGY, and the HUD is told at once rather than on the next
     * 10-tick sync (or never, while picking a respawn spot in spectator).
     */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        player.setData(ModAttachments.PLAYER_ENERGY.get(), SPAWN_ENERGY);
        PacketDistributor.sendToPlayer(player, new com.terrainlab.network.PlayerEnergyPacket(SPAWN_ENERGY));
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) {
            return;
        }
        float energy = player.getData(ModAttachments.PLAYER_ENERGY.get());
        player.setData(ModAttachments.PLAYER_ENERGY.get(), Math.max(MIN_ENERGY, energy - JUMP_DRAIN));
    }

    private static float attackHoldDrain(ServerPlayer player) {
        boolean down = player.getData(ModAttachments.PLAYER_WANTS_ATTACK.get());
        int heldTicks = player.getData(ModAttachments.PLAYER_ATTACK_HELD_TICKS.get());

        if (!down) {
            player.setData(ModAttachments.PLAYER_ATTACK_HELD_TICKS.get(), 0);
            return 0.0F;
        }

        boolean shouldDrain = heldTicks == 0 || (heldTicks % MINING_DRAIN_INTERVAL_TICKS == 0);
        player.setData(ModAttachments.PLAYER_ATTACK_HELD_TICKS.get(), heldTicks + 1);

        return shouldDrain ? ATTACK_SWING_DRAIN : 0.0F;
    }
}
