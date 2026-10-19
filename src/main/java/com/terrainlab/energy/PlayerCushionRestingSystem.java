
        package com.terrainlab.energy;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.Cushion;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = TerrainLab.MODID)
public final class PlayerCushionRestingSystem {

    /**
     * One hunger point is converted into energy every 80 ticks
     * spent sitting on a cushion (4 seconds at 20 TPS).
     */
    private static final int TICKS_PER_CONVERSION = 400;

    private static final Map<UUID, Integer> SITTING_TICKS = new HashMap<>();

    private PlayerCushionRestingSystem() {
    }

    /**
     * Called from TerrainLab's existing server tick loop.
     */
    public static void tick(ServerPlayer player) {
        UUID playerId = player.getUUID();

        // Cushion sitting restores energy only, never health.
        if (!(player.getVehicle() instanceof Cushion)) {
            SITTING_TICKS.remove(playerId);
            return;
        }

        // Do not restore energy in creative or spectator mode.
        if (player.isCreative() || player.isSpectator()) {
            SITTING_TICKS.remove(playerId);
            return;
        }

        float energy = player.getData(ModAttachments.PLAYER_ENERGY.get());

        // Cushion recovery cannot exceed the normal resting cap.
        if (energy >= PlayerEnergyConstants.REST_CAP) {
            SITTING_TICKS.remove(playerId);
            return;
        }

        int hunger = player.getFoodData().getFoodLevel();

        // Require the same minimum hunger as ordinary energy resting.
        if (hunger < PlayerEnergyConstants.MIN_HUNGER_FOR_ENERGY_REST) {
            // Preserve the elapsed sitting time while recovery is paused.
            return;
        }

        int elapsed = SITTING_TICKS.getOrDefault(playerId, 0) + 1;

        if (elapsed < TICKS_PER_CONVERSION) {
            SITTING_TICKS.put(playerId, elapsed);
            return;
        }

        // Charge hunger only when an energy conversion actually occurs.
        float newEnergy = Math.min(
                PlayerEnergyConstants.REST_CAP,
                energy + PlayerEnergyConstants.ENERGY_PER_HUNGER_POINT
        );

        player.getFoodData().setFoodLevel(hunger - 1);
        player.setData(ModAttachments.PLAYER_ENERGY, newEnergy);

        // Start a new conversion interval.
        SITTING_TICKS.put(playerId, 0);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SITTING_TICKS.remove(event.getEntity().getUUID());
    }
}

