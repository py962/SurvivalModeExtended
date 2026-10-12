package com.terrainlab.client;

import com.terrainlab.temperature.WaterImmersionEffect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Display-only wetness tracker for the Thermometer, keyed by entity UUID. Computed
 * locally from fluid state rather than synced; must not be used for gameplay logic,
 * which needs the server-authoritative value.
 */
public final class ClientWetnessTracker {

    private record State(double wetness, long lastUpdateTick) {
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private ClientWetnessTracker() {
    }

    public static double update(UUID entityId, long currentGameTime, boolean immersedNow) {
        State prev = STATES.get(entityId);
        double wetness;
        if (immersedNow) {
            wetness = 1.0;
        } else if (prev == null) {
            wetness = 0.0;
        } else {
            long elapsed = currentGameTime - prev.lastUpdateTick();
            wetness = WaterImmersionEffect.decay(prev.wetness(), elapsed);
        }
        STATES.put(entityId, new State(wetness, currentGameTime));
        return wetness;
    }
}
