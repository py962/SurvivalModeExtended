package com.terrainlab.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Frostbitten: a Zombie surrounded by a thick snow mist that pushes nearby
 * players' freeze meter up towards FREEZE_TARGET_PERCENT.
 */
public class FrostbittenEntity extends Zombie {

    private static final double MIST_RADIUS = 8.0;
    private static final int FREEZE_TARGET_PERCENT = 70;
    private static final int FREEZE_INCREMENT_PER_TICK = 4;

    /** Logs every spawn-candidate check; very noisy on the server thread. */
    private static final boolean DEBUG_LOG_SPAWN_CHECKS = false;

    public FrostbittenEntity(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level() instanceof ServerLevel serverLevel) {
            spawnMistParticles(serverLevel);
            freezeNearbyPlayers(serverLevel);
        }
    }

    private void spawnMistParticles(ServerLevel level) {
        // One clustered sendParticles call per type, every tick.
        double centerY = this.getY() + 1.0;

        level.sendParticles(ParticleTypes.SNOWFLAKE,
                this.getX(), centerY, this.getZ(),
                120,
                MIST_RADIUS, 1.5, MIST_RADIUS,
                0.02);
        level.sendParticles(ParticleTypes.CLOUD,
                this.getX(), centerY, this.getZ(),
                60,
                MIST_RADIUS, 1.2, MIST_RADIUS,
                0.01);
    }

    private void freezeNearbyPlayers(ServerLevel level) {
        AABB mistArea = this.getBoundingBox().inflate(MIST_RADIUS);
        List<Player> players = level.getEntitiesOfClass(Player.class, mistArea);
        for (Player player : players) {
            if (player.isCreative() || player.isSpectator()) continue;
            int target = player.getTicksRequiredToFreeze() * FREEZE_TARGET_PERCENT / 100;
            if (player.getTicksFrozen() < target) {
                player.setTicksFrozen(Math.min(target, player.getTicksFrozen() + FREEZE_INCREMENT_PER_TICK));
            }
        }
    }

    /** Dark, solid ground spawn check. */
    public static boolean checkFrostbittenSpawnRules(net.minecraft.world.entity.EntityType<FrostbittenEntity> type,
                                                     net.minecraft.world.level.ServerLevelAccessor level,
                                                     net.minecraft.world.entity.EntitySpawnReason reason,
                                                     net.minecraft.core.BlockPos pos,
                                                     net.minecraft.util.RandomSource random) {
        boolean groundOk = level.getBlockState(pos.below()).isSolid();
        int brightness = level.getRawBrightness(pos, level.getSkyDarken());
        boolean result = groundOk && brightness <= 7;
        if (DEBUG_LOG_SPAWN_CHECKS) {
            // com.terrainlab.TerrainLab.LOGGER.info(
                    // "Frostbitten spawn check at {}: groundOk={}, brightness={}, result={}",
                    // pos, groundOk, brightness, result);
        }
        return result;
    }
}
