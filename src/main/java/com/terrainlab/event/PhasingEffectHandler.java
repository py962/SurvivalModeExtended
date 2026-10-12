package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModMobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phasing effect enforcement, run on both sides (player movement is client-simulated): while
 * active, the entity has noPhysics, no vertical velocity, and its Y locked to where the
 * effect started; suffocation damage is cancelled.
 * <p>
 * noGravity is never touched (it is saved to NBT). noPhysics is remembered before Phasing
 * turns it on and restored once when Phasing ends. State is kept per side because in
 * singleplayer both sides share this JVM and the same entity UUIDs.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class PhasingEffectHandler {

    private static final boolean PHASING_DIAGNOSTICS = false;

    private static final class SideState {
        /** Y captured the first tick Phasing is active. */
        final Map<UUID, Double> lockedY = new ConcurrentHashMap<>();
        /** noPhysics as it was before Phasing turned it on. */
        final Map<UUID, Boolean> originalNoPhysics = new ConcurrentHashMap<>();
    }

    private static final SideState CLIENT = new SideState();
    private static final SideState SERVER = new SideState();

    private static SideState side(LivingEntity living) {
        return living.level().isClientSide() ? CLIENT : SERVER;
    }

    /** Called before anything turns noPhysics on for Phasing. Only the first call counts. */
    public static void rememberOriginal(LivingEntity living) {
        side(living).originalNoPhysics.putIfAbsent(living.getUUID(), living.noPhysics);
    }

    /** Puts noPhysics back as it was before Phasing, if Phasing changed it. */
    public static void restoreOriginal(LivingEntity living) {
        SideState state = side(living);
        UUID id = living.getUUID();
        state.lockedY.remove(id);
        Boolean original = state.originalNoPhysics.remove(id);
        if (original != null) {
            // Players get noPhysics re-set from isSpectator() at the start of
            // their next tick anyway (Player.tick), so this is right for them too.
            living.noPhysics = original;
        }
    }

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;

        SideState state = side(living);
        UUID id = living.getUUID();

        if (!living.hasEffect(ModMobEffects.PHASING)) {
            if (state.originalNoPhysics.containsKey(id) || state.lockedY.containsKey(id)) {
                restoreOriginal(living);
            }
            return;
        }

        rememberOriginal(living);
        living.noPhysics = true;

        Vec3 delta = living.getDeltaMovement();
        if (delta.y != 0.0) {
            living.setDeltaMovement(delta.x, 0.0, delta.z);
        }

        // Hard-clamp Y every tick: covers jumps, knockback, explosions, pistons, etc.
        Double locked = state.lockedY.get(id);
        if (locked == null) {
            locked = living.getY();
            state.lockedY.put(id, locked);
        } else if (living.getY() != locked) {
            living.setPos(living.getX(), locked, living.getZ());
        }

        if (PHASING_DIAGNOSTICS && !living.level().isClientSide()) {
            // TerrainLab.LOGGER.info(
                    // "=== PHASING DIAG === noPhysics={} Y={} lockedY={} deltaY={}",
                    // living.noPhysics, String.format("%.2f", living.getY()),
                    // String.format("%.2f", locked), String.format("%.4f", living.getDeltaMovement().y)
            // );
        }
    }

    /** Drops state for entities that unload, die or change dimension while Phasing. */
    @SubscribeEvent
    public static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            SideState state = event.getLevel().isClientSide() ? CLIENT : SERVER;
            state.lockedY.remove(living.getUUID());
            state.originalNoPhysics.remove(living.getUUID());
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity living = event.getEntity();
        if (!living.hasEffect(ModMobEffects.PHASING)) return;

        DamageSource source = event.getSource();
        if (source.is(DamageTypes.IN_WALL)) {
            event.setCanceled(true);
        }
    }
}
