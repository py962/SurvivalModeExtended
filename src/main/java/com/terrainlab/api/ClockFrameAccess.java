package com.terrainlab.api;

/**
 * Duck interface exposing a clock-holding item frame's redstone pulse state
 * (see ItemFrameClockComparatorMixin) to ItemFrameRedstoneHandler.
 * <p>
 * Needed because vanilla's ItemFrame#getAnalogOutput() is {@code rotation % 8 + 1}
 * for a non-empty frame and can never output a clean 0/15 swing. Lives outside
 * com.terrainlab.mixin for the same reason as BorrowingAccess.
 */
public interface ClockFrameAccess {
    boolean terrainlab$isClockPulseHigh();

    void terrainlab$setClockPulseHigh(boolean high);
}
