package com.terrainlab.debug;

import com.terrainlab.TerrainLab;
import com.terrainlab.season.SolarTime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Temporary diagnostic for zombies surviving in daylight. Logs [ZOMBIE_TRACE] lines for
 * zombie-family spawns with sky access (any reason, any time), and every 10 s for each
 * unburnt zombie under open sky near a player during solar day (once per mob).
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class ZombieSpawnTrace {

    public static final boolean ENABLED = false;

    private static final int SCAN_INTERVAL = 200;
    private static final double SCAN_RADIUS = 128.0;

    private static final Set<UUID> REPORTED = Collections.synchronizedSet(new HashSet<>());

    private ZombieSpawnTrace() {
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!ENABLED
                || !(event.getEntity() instanceof Zombie zombie)
                || !(event.getLevel().getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        if (!level.canSeeSky(pos)) {
            return;
        }
        // TerrainLab.LOGGER.info(
                // "[ZOMBIE_TRACE] SPAWN {} reason={} at {} | F3={} solar={} solarDay={} | skyDarken={} "
                        // + "| sky light={} block light={} raw brightness={} | raining={}",
                // zombie.getType().toShortString(),
                // event.getSpawnType(),
                // pos.toShortString(),
                // Math.floorMod(level.getDayTime(), 24000L),
                // SolarTime.solarTimeOfDay(level),
                // SolarTime.isSolarDay(level),
                // level.getSkyDarken(),
                // level.getBrightness(LightLayer.SKY, pos),
                // level.getBrightness(LightLayer.BLOCK, pos),
                // level.getMaxLocalRawBrightness(pos),
                // level.isRaining());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ENABLED || event.getServer().getTickCount() % SCAN_INTERVAL != 0) {
            return;
        }
        ServerLevel level = event.getServer().getLevel(Level.OVERWORLD);
        if (level == null || !SolarTime.isSolarDay(level)) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            for (Zombie zombie : level.getEntitiesOfClass(Zombie.class,
                    player.getBoundingBox().inflate(SCAN_RADIUS),
                    z -> z.isAlive() && !z.isOnFire())) {

                BlockPos eye = BlockPos.containing(zombie.getX(), zombie.getEyeY(), zombie.getZ());
                if (!level.canSeeSky(eye) || zombie.isInWaterOrRain() || !REPORTED.add(zombie.getUUID())) {
                    continue;
                }

                // TerrainLab.LOGGER.info(
                        // "[ZOMBIE_TRACE] SURVIVOR {} spawnReason={} age={}t persistent={} at {} "
                                // + "| F3={} solar={} | burnsInDaylight={} burnTimeline={} brightness={} skyDarken={} "
                                // + "| helmet={} name={}",
                        // zombie.getType().toShortString(),
                        // zombie.getSpawnType(),
                        // zombie.tickCount,
                        // zombie.isPersistenceRequired(),
                        // zombie.blockPosition().toShortString(),
                        // Math.floorMod(level.getDayTime(), 24000L),
                        // SolarTime.solarTimeOfDay(level),
                        // zombie.getType().is(EntityTypeTags.BURN_IN_DAYLIGHT),
                        // level.environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, zombie.position()),
                        // String.format("%.2f", zombie.getLightLevelDependentMagicValue()),
                        // level.getSkyDarken(),
                        // zombie.getItemBySlot(EquipmentSlot.HEAD).getItem(),
                        // zombie.hasCustomName() ? zombie.getCustomName().getString() : "-");
            }
        }
    }
}
