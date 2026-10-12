package com.terrainlab.temperature;

/**
 * Per-player input state for empty-hand drinking: IDLE -> HOLDING (drinks after
 * 1s held) -> COOLDOWN (needs 1s of continuous release) -> IDLE. Not serialized.
 */
public class WaterDrinkState {

    public enum Phase { IDLE, HOLDING, COOLDOWN }

    public Phase phase = Phase.IDLE;
    public long holdStartTick = -1;
    public long releaseStartTick = -1; // -1 while held or before a release period starts
}
