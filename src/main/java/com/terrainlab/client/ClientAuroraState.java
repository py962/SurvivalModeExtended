package com.terrainlab.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Client-side mirror of the server's aurora state, written by the sync payload and read by the
 * sky renderer, plus the colour tint from a nearby Celestial Forge ritual.
 */
public class ClientAuroraState {

    private static final double TINT_RANGE = 160.0; // blocks from a ritual that see the tinted aurora
    private static final long TINT_HOLD_TICKS = 10;  // a ritual refreshes the tint every client tick

    private static boolean active = false;
    private static int tintColour = 0xFFFFFF;
    private static long lastTintTime = Long.MIN_VALUE;
    private static double lastTintDistSq = Double.MAX_VALUE;

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean value) {
        active = value;
    }

    /**
     * Called each client tick by a forge ritual; tints the aurora for players within range. With
     * several rituals in range, the nearest one wins.
     */
    public static void tintFrom(BlockPos forgePos, int colour) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        double distSq = mc.player.blockPosition().distSqr(forgePos);
        if (distSq > TINT_RANGE * TINT_RANGE) {
            return;
        }
        long now = mc.level.getGameTime();
        if (now == lastTintTime && distSq > lastTintDistSq) {
            return;
        }
        tintColour = colour;
        lastTintTime = now;
        lastTintDistSq = distSq;
    }

    /** True while a ritual is tinting the aurora; the renderer fades the tint in and out. */
    public static boolean isTinted(long gameTime) {
        return lastTintTime != Long.MIN_VALUE && gameTime - lastTintTime <= TINT_HOLD_TICKS;
    }

    public static int tintColour() {
        return tintColour;
    }

    /** Clears the state on disconnect. */
    public static void reset() {
        active = false;
        lastTintTime = Long.MIN_VALUE;
    }
}
