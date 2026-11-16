package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.hearth.AvoidHearthGoal;
import com.terrainlab.hearth.HearthRepel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Phantom;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent;

/** Village hearth protection: no natural hostile spawns nearby, and hostiles that wander in turn back. */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class HearthRepelHandler {

    private HearthRepelHandler() {
    }

    private static final int AVOID_PRIORITY = 1;

    @SubscribeEvent
    public static void onSpawnCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getSpawnType() != EntitySpawnReason.NATURAL || !HearthRepel.isRepelled(event.getEntityType())) return;
        if (event.getLevel() instanceof ServerLevel level && HearthRepel.find(level, event.getPos()) != null) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    // Insomnia phantoms spawn around the player, so a player at the hearth gets none.
    @SubscribeEvent
    public static void onPhantomSpawn(PlayerSpawnPhantomsEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level
                && HearthRepel.find(level, event.getEntity().blockPosition()) != null) {
            event.setResult(PlayerSpawnPhantomsEvent.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !HearthRepel.isRepelled(event.getEntity().getType())) return;
        if (event.getEntity() instanceof Phantom phantom) {
            phantom.goalSelector.addGoal(0, new AvoidHearthGoal.Flying(phantom));
        } else if (event.getEntity() instanceof PathfinderMob mob) {
            mob.goalSelector.addGoal(AVOID_PRIORITY, new AvoidHearthGoal.Walking(mob));
        }
    }
}
