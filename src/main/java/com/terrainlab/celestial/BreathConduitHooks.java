package com.terrainlab.celestial;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModDataComponents;
import com.terrainlab.registry.ModMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.Optional;
import java.util.UUID;

/**
 * Additions layered on the vanilla conduit, which serves as the Breath conduit: keeps
 * {@link CelestialConduitRegistry} in sync for amulet pairing, and grants Warmth to any
 * player that has Conduit Power.
 *
 * <p>The pair id lives in the vanilla block entity's own component map, which vanilla
 * saves and loads with the block entity.
 */
public final class BreathConduitHooks {

    public static final boolean VERBOSE_LOGGING = CelestialDebug.VERBOSE;

    // Only re-apply Warmth once it lags Conduit Power's remaining duration by this much.
    private static final int WARMTH_RESYNC_SLACK_TICKS = 20;

    private BreathConduitHooks() {
    }

    /** Called at the tail of vanilla ConduitBlockEntity#serverTick on its 40-tick frame-check cadence.
     * @param frameCount vanilla's effectBlocks.size(); 0 when the conduit is not submerged. */
    public static void onServerCheck(ServerLevel level, BlockPos pos, boolean active, int frameCount,
                                     DataComponentMap components) {
        // Unpaired conduits are registered under a positional key so unpaired creative amulets can find them.
        UUID pairId = CelestialConduitRegistry.keyFor(
                components.get(ModDataComponents.CELESTIAL_PAIR_ID.get()), level.dimension(), pos);

        int radius = CelestialFrame.rangeFor(frameCount);
        CelestialConduitRegistry registry = CelestialConduitRegistry.get(level);
        CelestialConduitRegistry.ConduitRecord wanted = new CelestialConduitRegistry.ConduitRecord(
                level.dimension(), pos.immutable(), active, CelestialVariant.BREATH, radius);

        Optional<CelestialConduitRegistry.ConduitRecord> existing = registry.get(pairId);
        if (existing.isPresent() && existing.get().equals(wanted)) {
            if (VERBOSE_LOGGING) {
                // TerrainLab.LOGGER.info("[BREATH_CONDUIT] {} {} pair {} unchanged (active={}, frame={}, radius={})",
                        // level.dimension().identifier(), pos, pairId, active, frameCount, radius);
            }
            return;
        }

        registry.setConduit(pairId, level.dimension(), pos.immutable(), active, CelestialVariant.BREATH, radius);
        // TerrainLab.LOGGER.info("[BREATH_CONDUIT] {} {} pair {} registered/updated: active={}, frame={}, radius={} (was {})",
                // level.dimension().identifier(), pos, pairId, active, frameCount, radius,
                // existing.map(Object::toString).orElse("absent"));
    }

    /** Called on real block removal (not chunk unload). Removes the registry record only if it
     * still points at this conduit, so a second conduit sharing the pair id keeps its record. */
    public static void onRemoved(ServerLevel level, BlockPos pos, DataComponentMap components) {
        UUID pairId = CelestialConduitRegistry.keyFor(
                components.get(ModDataComponents.CELESTIAL_PAIR_ID.get()), level.dimension(), pos);

        CelestialConduitRegistry registry = CelestialConduitRegistry.get(level);
        Optional<CelestialConduitRegistry.ConduitRecord> existing = registry.get(pairId);
        if (existing.isEmpty()) {
            // TerrainLab.LOGGER.info("[BREATH_CONDUIT] removed conduit at {} {} pair {} - no registry record to remove",
                    // level.dimension().identifier(), pos, pairId);
            return;
        }
        if (!existing.get().dimension().equals(level.dimension()) || !existing.get().pos().equals(pos)) {
            // TerrainLab.LOGGER.info("[BREATH_CONDUIT] removed conduit at {} {} pair {} - record belongs to {} {}, leaving it",
                    // level.dimension().identifier(), pos, pairId, existing.get().dimension().identifier(), existing.get().pos());
            return;
        }

        registry.removeConduit(pairId);
        // TerrainLab.LOGGER.info("[BREATH_CONDUIT] removed conduit at {} {} pair {} - unregistered",
                // level.dimension().identifier(), pos, pairId);
    }

    /** Grants Warmth matching the player's remaining Conduit Power duration. Called every STAGGER ticks. */
    public static void tickPlayer(ServerPlayer player) {
        MobEffectInstance power = player.getEffect(MobEffects.CONDUIT_POWER);
        if (power == null) {
            return;
        }

        MobEffectInstance warmth = player.getEffect(ModMobEffects.WARMTH);
        boolean powerInfinite = power.isInfiniteDuration();
        int wantedDuration = powerInfinite ? MobEffectInstance.INFINITE_DURATION : power.getDuration();

        if (warmth != null && (warmth.isInfiniteDuration()
                || (!powerInfinite && warmth.getDuration() >= wantedDuration - WARMTH_RESYNC_SLACK_TICKS))) {
            if (VERBOSE_LOGGING) {
                // TerrainLab.LOGGER.info("[BREATH_CONDUIT] {} has Conduit Power ({}t) and Warmth already covers it ({}t)",
                        // player.getName().getString(), power.getDuration(), warmth.getDuration());
            }
            return;
        }

        // ambient=true, visible=false, showIcon=true: same flags as CelestialEffects#give.
        player.addEffect(new MobEffectInstance(ModMobEffects.WARMTH, wantedDuration, 0, true, false, true));
        if (VERBOSE_LOGGING) {
            // TerrainLab.LOGGER.info("[BREATH_CONDUIT] {} has Conduit Power ({}t) - granted Warmth for {}t (previous Warmth: {})",
                    // player.getName().getString(), power.getDuration(), wantedDuration,
                    // warmth == null ? "none" : warmth.getDuration() + "t");
        }
    }
}
