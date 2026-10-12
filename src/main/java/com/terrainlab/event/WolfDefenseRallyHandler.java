package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.util.SafeSpotFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.List;

/**
 * When a player is attacked by a mob or another player, their tamed wolves within
 * {@link #RALLY_RADIUS} teleport to their side; wolves ordered to sit stay put. Independent
 * of bed anchors and horn summoning.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class WolfDefenseRallyHandler {

    private static final double RALLY_RADIUS = 100.0; // blocks

    @SubscribeEvent
    public static void onPlayerDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == player) return;
        if (attacker instanceof OwnableEntity owned && owned.getOwner() == player) return;

        List<Wolf> nearby = level.getEntitiesOfClass(
                Wolf.class,
                player.getBoundingBox().inflate(RALLY_RADIUS),
                wolf -> wolf.isTame() && wolf.isOwnedBy(player) && !wolf.isOrderedToSit());
        if (nearby.isEmpty()) return;

        BlockPos safe = SafeSpotFinder.findNearestSafeSpot(level, player.blockPosition(), 3);
        for (Wolf wolf : nearby) {
            if (safe != null) {
                wolf.teleportTo(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
            } else {
                wolf.teleportTo(player.getX(), player.getY(), player.getZ());
            }
        }
    }
}
