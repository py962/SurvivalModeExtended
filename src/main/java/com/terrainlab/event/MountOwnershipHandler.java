package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Assigns an owner to camels (including elephants), striders and happy ghasts: the player who fits
 * the saddle or harness, stored in the {@code MOUNT_OWNER} attachment.
 * <p>
 * Equipment-change events don't identify the player, so each right-click on a mount is remembered,
 * and an empty-to-filled change of the relevant slot within {@link #CLAIM_WINDOW_TICKS} of that click
 * claims the mount. Dispensers, spawns, commands and chunk loads have no recent click and never claim.
 * The last player to fit the gear becomes the owner.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class MountOwnershipHandler {

    private static final boolean DEBUG_LOGGING = false;

    /** 3 minutes; allows time spent in the camel inventory GUI. */
    private static final long CLAIM_WINDOW_TICKS = 3600L;

    private record Interaction(UUID player, long gameTime) {}

    // mob UUID -> most recent player right-click. Server thread only.
    private static final Map<UUID, Interaction> RECENT = new HashMap<>();

    private static boolean isOwnableMount(Entity e) {
        return e instanceof Camel || e instanceof Strider || e instanceof HappyGhast;
    }

    /** Records who last right-clicked the mount, unless the interaction was cancelled. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity target = event.getTarget();
        if (!isOwnableMount(target)) return;

        Player player = event.getEntity();
        RECENT.put(target.getUUID(), new Interaction(player.getUUID(), level.getGameTime()));
        prune(level.getGameTime());
        log("interact recorded mob={} player={} tick={}",
                target.getType().toShortString(), player.getScoreboardName(), level.getGameTime());
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;

        EquipmentSlot slot = event.getSlot();
        boolean relevant =
                (slot == EquipmentSlot.SADDLE && (entity instanceof Camel || entity instanceof Strider))
                || (slot == EquipmentSlot.BODY && entity instanceof HappyGhast);
        if (!relevant) return;

        // Only empty -> filled claims; removals and swaps never change the owner.
        if (!event.getFrom().isEmpty() || event.getTo().isEmpty()) return;

        Interaction rec = RECENT.remove(entity.getUUID());
        if (rec == null) {
            log("equip on {} slot={} item={} but no recent player click - owner unchanged (dispenser/load/etc)",
                    entity.getType().toShortString(), slot, event.getTo().getItem());
            return;
        }
        long age = level.getGameTime() - rec.gameTime();
        if (age < 0 || age > CLAIM_WINDOW_TICKS) {
            log("equip on {} slot={} but click was {} ticks ago (window {}) - owner unchanged",
                    entity.getType().toShortString(), slot, age, CLAIM_WINDOW_TICKS);
            return;
        }

        entity.setData(ModAttachments.MOUNT_OWNER.get(), Optional.of(rec.player()));
        log("OWNER SET mob={} slot={} item={} owner={} (click {} ticks ago)",
                entity.getType().toShortString(), slot, event.getTo().getItem(), rec.player(), age);
    }

    private static void prune(long now) {
        if (RECENT.size() < 64) return;
        Iterator<Map.Entry<UUID, Interaction>> it = RECENT.entrySet().iterator();
        while (it.hasNext()) {
            long age = now - it.next().getValue().gameTime();
            if (age < 0 || age > CLAIM_WINDOW_TICKS) it.remove();
        }
    }

    private static void log(String msg, Object... args) {
        // if (DEBUG_LOGGING) TerrainLab.LOGGER.info("[MOUNT_OWNER] " + msg, args);
    }
}
