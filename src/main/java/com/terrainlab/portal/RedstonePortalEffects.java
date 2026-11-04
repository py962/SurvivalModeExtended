package com.terrainlab.portal;

import com.terrainlab.network.RedstonePortalFlashPacket;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Redstone Portal transition effect, run once per trip from the TeleportTransition callback:
 * a burst of redstone dust and a high, short beacon chime at both the departure and arrival
 * points, and (for players) the client-side red arrival flash. Only living entities trigger
 * it, so a stream of dropped items through a portal stays quiet.
 */
public final class RedstonePortalEffects {

    private static final SoundEvent SOUND = SoundEvents.BEACON_POWER_SELECT;
    private static final float SOUND_VOLUME = 0.6F;
    private static final float SOUND_PITCH = 1.6F;

    private static final int PARTICLE_COUNT = 40;
    private static final double PARTICLE_SPREAD_XZ = 0.4;
    private static final double PARTICLE_SPREAD_Y = 0.8;

    private RedstonePortalEffects() {
    }

    /**
     * @param fromLevel the level the entity left
     * @param fromPos   the entity's position just before it left
     * @param entity    the entity, already at its destination
     */
    public static void play(ServerLevel fromLevel, Vec3 fromPos, Entity entity) {
        if (!(entity instanceof LivingEntity) || entity.isSpectator()) {
            return;
        }
        burst(fromLevel, fromPos, entity.getBbHeight());
        if (entity.level() instanceof ServerLevel toLevel) {
            burst(toLevel, entity.position(), entity.getBbHeight());
        }
        if (entity instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, RedstonePortalFlashPacket.INSTANCE);
        }
    }

    private static void burst(ServerLevel level, Vec3 feet, float height) {
        double y = feet.y + height / 2.0;
        level.sendParticles(DustParticleOptions.REDSTONE, feet.x, y, feet.z, PARTICLE_COUNT,
                PARTICLE_SPREAD_XZ, PARTICLE_SPREAD_Y, PARTICLE_SPREAD_XZ, 0.0);
        level.playSound(null, feet.x, y, feet.z, SOUND, SoundSource.BLOCKS, SOUND_VOLUME, SOUND_PITCH);
    }
}
