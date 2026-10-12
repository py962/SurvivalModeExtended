package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.entity.ElephantEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Temporary diagnostics for elephant interaction while mounted: logs the rider's eye position
 * against the elephant's hitbox, entity interactions with elephants, and empty right-clicks while
 * riding. Intended to be removed once the picking issue is resolved.
 */
public class ElephantDebugInteractionHandler {

    private static final int TICK_LOG_INTERVAL = 20; // ticks

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (player.tickCount % TICK_LOG_INTERVAL != 0) return;

        if (!(player.getVehicle() instanceof ElephantEntity elephant)) return;

        Vec3 eyePos = player.getEyePosition();
        AABB elephantBox = elephant.getBoundingBox();
        boolean eyeInsideBox = elephantBox.contains(eyePos);

        // TerrainLab.LOGGER.info(
                // "[ELEPHANT_DEBUG] mounted=true, eyePos={}, elephantBox={}, eyeInsideElephantBox={}",
                // eyePos, elephantBox, eyeInsideBox);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ElephantEntity)) return;

        Player player = event.getEntity();
        boolean isMountedOnThisElephant = player.getVehicle() == event.getTarget();

        // TerrainLab.LOGGER.info(
                // "[ELEPHANT_DEBUG] ENTITY_INTERACT fired on elephant - raycast SUCCEEDED. mountedOnThisElephant={}",
                // isMountedOnThisElephant);
    }

    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        Player player = event.getEntity();
        if (!(player.getVehicle() instanceof ElephantEntity)) return;

        // TerrainLab.LOGGER.info(
                // "[ELEPHANT_DEBUG] RIGHT_CLICK_EMPTY while mounted on elephant - raycast found NOTHING, " +
                        // "mobInteract was NOT called. This is the hitbox-overlap failure mode if it fires.");
    }
}
