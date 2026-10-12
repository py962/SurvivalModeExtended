package com.terrainlab.api;

/**
 * Duck interface exposing a ServerPlayer's Borrowing view state (set by
 * BorrowingSleepMixin) to other mixins and handlers.
 * <p>
 * Lives outside com.terrainlab.mixin because Mixin refuses direct references
 * to plain classes in a mixin package.
 */
public interface BorrowingAccess {
    boolean terrainlab$isBorrowing();

    /** Sets the Borrowing state; also cleared by BorrowingEffectExpiryHandler when the effect expires mid-sleep. */
    void terrainlab$setBorrowing(boolean borrowing);
}
