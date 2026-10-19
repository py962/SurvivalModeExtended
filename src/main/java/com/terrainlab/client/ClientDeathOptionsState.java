package com.terrainlab.client;

/** Client-side mirror of {@code DeathOptionsPacket}, read by {@link CelestialDeathScreenHandler}. */
public final class ClientDeathOptionsState {

    private static volatile boolean conduit = false;
    private static volatile boolean pick = false;

    private ClientDeathOptionsState() {
    }

    public static void set(boolean conduit, boolean pick) {
        ClientDeathOptionsState.conduit = conduit;
        ClientDeathOptionsState.pick = pick;
    }

    public static boolean conduitAvailable() {
        return conduit;
    }

    public static boolean pickAvailable() {
        return pick;
    }

    /** Cleared on login, disconnect and respawn so options never carry over to a later death. */
    public static void reset() {
        set(false, false);
    }
}
