package com.terrainlab.client;

/** Client-side mirror of {@code DeathPickingStatePacket}, read by {@link CelestialDeathPickingClientHandler}. */
public final class ClientDeathPickingState {

    private static volatile boolean active = false;
    private static volatile int ticksRemaining = 0;

    private ClientDeathPickingState() {
    }

    public static void set(boolean active, int ticksRemaining) {
        ClientDeathPickingState.active = active;
        ClientDeathPickingState.ticksRemaining = ticksRemaining;
    }

    public static boolean isActive() {
        return active;
    }

    public static int ticksRemaining() {
        return ticksRemaining;
    }

    /** Cleared on disconnect so a session never carries into the next world. */
    public static void reset() {
        set(false, 0);
    }
}
