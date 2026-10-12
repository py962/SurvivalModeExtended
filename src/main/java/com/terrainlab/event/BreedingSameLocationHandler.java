package com.terrainlab.event;

import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;

/**
 * Prevents breeding between two animals at exactly the same position (stacked via commands,
 * glitches or other mods). Runs at HIGHEST priority so the cancellation happens before
 * {@link CelestialLuckXpHandler}'s breeding XP reward.
 */
public class BreedingSameLocationHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreedAttempt(BabyEntitySpawnEvent event) {
        Mob parentA = event.getParentA();
        Mob parentB = event.getParentB();
        if (parentA == null || parentB == null) {
            return;
        }
        if (parentA.position().equals(parentB.position())) {
            event.setCanceled(true);
        }
    }
}
