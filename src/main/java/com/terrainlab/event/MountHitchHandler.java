package com.terrainlab.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

/**
 * Hitching-post mechanic: riding a mount while holding a lead behaves as if the mount were led by
 * hand, so vanilla's Leashable and LeashFenceKnotEntity logic handles range and transfer.
 * <ul>
 *   <li>Mounting a Leashable tied to a fence knot claims the leash back to the rider.</li>
 *   <li>Riding an unleashed Leashable while holding a lead leashes it to the rider, consuming one lead.</li>
 *   <li>Right-clicking a bare fence with an empty hand or a lead moves every entity leashed to the player
 *   onto a new knot; no lead is consumed, matching vanilla's knot transfer.</li>
 * </ul>
 */
public class MountHitchHandler {

    // Interval for the "already mounted, then picked up a lead" check; mounting itself is handled instantly.
    private static final int STAGGER = 5;

    @SubscribeEvent
    public static void onEntityMount(EntityMountEvent event) {
        if (event.isDismounting()) return;
        if (!(event.getEntityMounting() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        if (!(event.getEntityBeingMounted() instanceof Entity mountEntity)) return;
        if (!(mountEntity instanceof Leashable leashableMount)) return;

        Entity currentHolder = leashableMount.getLeashHolder();

        if (currentHolder instanceof LeashFenceKnotEntity) {
            // Parked at a post: riding claims the leash back regardless of what is held.
            leashableMount.setLeashedTo(player, true);
            playTransferSound(mountEntity);
            return;
        }

        if (currentHolder != null) {
            // Leashed to something else (e.g. another player); leave it alone.
            return;
        }

        InteractionHand leadHand = findLeadHand(player);
        if (leadHand == null) return;

        leashableMount.setLeashedTo(player, true);
        if (!player.hasInfiniteMaterials()) {
            player.getItemInHand(leadHand).shrink(1);
        }
        playTransferSound(mountEntity);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % STAGGER != 0) return;

        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof Leashable leashableMount)) return;
        // Already leashed (to the player, or still to a knot if onEntityMount did not fire).
        if (leashableMount.getLeashHolder() != null) return;

        InteractionHand leadHand = findLeadHand(player);
        if (leadHand == null) return;

        leashableMount.setLeashedTo(player, true);
        if (!player.hasInfiniteMaterials()) {
            player.getItemInHand(leadHand).shrink(1);
        }
        playTransferSound(vehicle);
    }

    @SubscribeEvent
    public static void onRightClickFence(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        if (level.isClientSide()) return;

        // Other items keep their normal use (e.g. block placement), which the client already predicts.
        ItemStack held = player.getItemInHand(event.getHand());
        if (!held.isEmpty() && !held.is(Items.LEAD)) return;

        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).is(BlockTags.FENCES)) return;

        List<Leashable> leashedToPlayer = Leashable.leashableLeashedTo(player);
        if (leashedToPlayer.isEmpty()) return;

        LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(level, pos);

        boolean anyTransferred = false;
        for (Leashable leashable : leashedToPlayer) {
            if (leashable.canHaveALeashAttachedTo(knot)) {
                leashable.setLeashedTo(knot, true);
                anyTransferred = true;
            }
        }

        if (!anyTransferred) return;

        // No lead consumed: it was spent when the leash to the player first formed.
        event.setCanceled(true);
        knot.playPlacementSound();
    }

    private static InteractionHand findLeadHand(Player player) {
        if (player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.LEAD)) return InteractionHand.MAIN_HAND;
        if (player.getItemInHand(InteractionHand.OFF_HAND).is(Items.LEAD)) return InteractionHand.OFF_HAND;
        return null;
    }

    private static void playTransferSound(Entity entity) {
        entity.level().playSound(null, entity.blockPosition(),
                SoundEvents.LEAD_TIED, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }
}
