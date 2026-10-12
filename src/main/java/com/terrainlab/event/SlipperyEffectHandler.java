package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModMobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slippery effect: horizontal speed decays linearly to zero over
 * {@link #DECAY_DURATION_TICKS}, independent of the block underfoot (block friction is
 * neutralized in LivingTravelEffectsMixin). A new slide starts whenever the entity speeds
 * up beyond the current slide's prediction, so acceleration stays normal but stopping is
 * slow. Runs on both sides because player movement is simulated on the client.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class SlipperyEffectHandler {

    private static final int DECAY_DURATION_TICKS = 60; // 3 seconds

    private record DecaySession(double v0, int elapsedTicks) {
        DecaySession next() {
            return new DecaySession(v0, elapsedTicks + 1);
        }

        double targetSpeed() {
            return Math.max(0.0, v0 * (1.0 - (double) elapsedTicks / DECAY_DURATION_TICKS));
        }
    }

    // Keyed by UUID so state survives entity object churn; one map per logical side, since
    // singleplayer ticks the same UUID on both.
    private static final Map<UUID, DecaySession> clientSessions = new ConcurrentHashMap<>();
    private static final Map<UUID, DecaySession> serverSessions = new ConcurrentHashMap<>();

    private static Map<UUID, DecaySession> sessionsFor(LivingEntity living) {
        return living.level().isClientSide() ? clientSessions : serverSessions;
    }

    @SubscribeEvent
    public static void onLeaveLevel(EntityLeaveLevelEvent event) {
        (event.getLevel().isClientSide() ? clientSessions : serverSessions).remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;

        UUID id = living.getUUID();
        Map<UUID, DecaySession> decaySessions = sessionsFor(living);

        if (!living.hasEffect(ModMobEffects.SLIPPERY)) {
            decaySessions.remove(id);
            return;
        }

        Vec3 delta = living.getDeltaMovement();
        double currentSpeed = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (currentSpeed < 1.0E-5) {
            decaySessions.remove(id);
            return;
        }

        DecaySession session = decaySessions.get(id);

        // No session, or the entity sped up past the prediction (new input): restart the slide.
        if (session == null || currentSpeed > session.targetSpeed() + 1.0E-4) {
            session = new DecaySession(currentSpeed, 0);
        } else {
            session = session.next();
        }

        double targetSpeed = session.targetSpeed();
        if (targetSpeed <= 0.0) {
            living.setDeltaMovement(0.0, delta.y, 0.0);
            decaySessions.remove(id);
            return;
        }

        double scale = targetSpeed / currentSpeed;
        living.setDeltaMovement(delta.x * scale, delta.y, delta.z * scale);
        decaySessions.put(id, session);
    }
}
