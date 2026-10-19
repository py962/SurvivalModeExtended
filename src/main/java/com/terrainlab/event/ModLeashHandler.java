package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.entity.HouseboatEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Houseboat and Happy Ghast leash interactions: tying a houseboat to a ghast with a lead,
 * sneak-to-cut with shears, and blocking dismount or damage while the boat hangs in the air.
 */
public class ModLeashHandler {

    private static boolean isBoatSuspendedFromGhast(HouseboatEntity boat) {
        Leashable.LeashData data = boat.getLeashData();
        if (data == null) return false;
        if (data.leashHolder == null) return false;
        return data.leashHolder instanceof HappyGhast;
    }

    private static boolean isGhastTowingBoat(HappyGhast ghast, Level level) {
        List<Leashable> leashed = Leashable.leashableLeashedTo(ghast);
        return leashed.stream().anyMatch(l -> l instanceof HouseboatEntity);
    }

    private static HouseboatEntity getGhastBoat(HappyGhast ghast, Level level) {
        List<Leashable> leashed = Leashable.leashableLeashedTo(ghast);
        return leashed.stream()
                .filter(l -> l instanceof HouseboatEntity)
                .map(l -> (HouseboatEntity) l)
                .findFirst()
                .orElse(null);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        Entity target = event.getTarget();
        Level level = event.getLevel();

        if (level.isClientSide()) return;

        ItemStack held = player.getItemInHand(event.getHand());

        // Sneak-use on the ghast while riding its towed boat dismounts.
        if (target instanceof HappyGhast ghast && player.isSecondaryUseActive()) {
            HouseboatEntity boat = getGhastBoat(ghast, level);
            if (boat != null && boat.hasPassenger(player)) {
                player.stopRiding();
                event.setCanceled(true);
                return;
            }
        }

        // Clicking a houseboat while leading a ghast ties the boat to the ghast, whatever is in hand:
        // the lead already holding the ghast becomes the boat's tow line, so no extra lead is used.
        // (Leading the ghast usually leaves the hand empty, which would otherwise board the boat.)
        if (target instanceof HouseboatEntity boat && !boat.hasPassenger(player)) {
            List<Leashable> leashedToPlayer = Leashable.leashableLeashedTo(player);
            for (Leashable leashed : leashedToPlayer) {
                if (leashed instanceof HappyGhast ghast) {
                    // removeLeash() drops no lead item; the lead moves to the boat-to-ghast line.
                    // The ghast's stored (enchanted) lead goes with it; read it before removeLeash() forgets it.
                    net.minecraft.world.item.ItemStack ghastLead = EnchantedLeadHandler.peek(ghast).copy();
                    ghast.removeLeash();
                    boat.setLeashedTo(ghast, true);
                    EnchantedLeadHandler.record(boat, ghastLead.isEmpty() ? new ItemStack(Items.LEAD) : ghastLead);
                    if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                        com.terrainlab.advancement.ModAdvancements.award(serverPlayer, "houseboat_ghast");
                    }
                    event.setCanceled(true);
                    level.playSound(null, boat.blockPosition(),
                            net.minecraft.sounds.SoundEvents.LEAD_TIED,
                            net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
                    return;
                }
            }
        }

        // Clicking a ghast while leading a houseboat moves the boat's lead onto the ghast; no extra lead
        // is used, matching the case above. Sneaking keeps the ghast's own interactions available.
        if (target instanceof HappyGhast ghast && !player.isSecondaryUseActive()) {
            List<Leashable> leashedToPlayer = Leashable.leashableLeashedTo(player);
            for (Leashable leashed : leashedToPlayer) {
                if (leashed instanceof HouseboatEntity boat) {
                    boat.setLeashedTo(ghast, true);
                    if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                        com.terrainlab.advancement.ModAdvancements.award(serverPlayer, "houseboat_ghast");
                    }
                    event.setCanceled(true);
                    level.playSound(null, boat.blockPosition(),
                            net.minecraft.sounds.SoundEvents.LEAD_TIED,
                            net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
                    return;
                }
            }
        }

        // Shears on a suspended boat require sneaking.
        if (target instanceof HouseboatEntity boat2 && held.is(Items.SHEARS)) {
            if (isBoatSuspendedFromGhast(boat2)) {
                if (!player.isSecondaryUseActive()) {
                    event.setCanceled(true);
                    player.displayClientMessage(
                            Component.literal("Sneak + shears to disconnect."), true);
                    return;
                }
            }
        }

        // Shears on a towing ghast require sneaking.
        if (target instanceof HappyGhast ghast2 && held.is(Items.SHEARS)) {
            if (isGhastTowingBoat(ghast2, level)) {
                if (!player.isSecondaryUseActive()) {
                    event.setCanceled(true);
                    player.displayClientMessage(
                            Component.literal("Sneak + shears to disconnect."), true);
                    return;
                }
            }
        }
    }

    // Players climbing from a towed houseboat onto its ghast, keyed by game tick. Vanilla
    // startRiding fires MOUNT for the ghast before DISMOUNT for the boat in the same call, so a
    // same-tick entry marks that dismount as the climb, which is always allowed. Server thread only.
    private static final Map<UUID, Long> CLIMBING_TO_GHAST = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerDismount(EntityMountEvent event) {
        if (!(event.getEntityMounting() instanceof Player player)) return;

        // Server only: the client does not know the landed state and follows the server.
        if (player.level().isClientSide()) return;

        if (event.isMounting()) {
            if (event.getEntityBeingMounted() instanceof HappyGhast ghast
                    && player.getVehicle() instanceof HouseboatEntity current
                    && current.getLeashData() != null
                    && current.getLeashData().leashHolder == ghast) {
                CLIMBING_TO_GHAST.put(player.getUUID(), player.level().getGameTime());
            }
            return;
        }

        if (!(event.getEntityBeingMounted() instanceof HouseboatEntity boat)) return;

        Long climbTick = CLIMBING_TO_GHAST.remove(player.getUUID());
        if (climbTick != null && climbTick == player.level().getGameTime()) return;

        boolean suspended = isBoatSuspendedFromGhast(boat);
        boolean inAir = boat.isInAir();

        if (!suspended) return;
        if (!inAir) return;

        Leashable.LeashData data = boat.getLeashData();
        if (data == null || data.leashHolder == null) return;
        if (!data.leashHolder.isAlive()) return;

        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof HouseboatEntity boat)) return;
        if (isBoatSuspendedFromGhast(boat)) {
            event.setCanceled(true);
            event.getEntity().displayClientMessage(
                    Component.literal("Cannot damage the houseboat while suspended."), true);
        }
    }
}
