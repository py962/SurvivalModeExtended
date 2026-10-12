package com.terrainlab.food;

/** A heal-over-time entry: the remaining amount is spread evenly over the remaining ticks. */
public class PendingHeal {
    public float remainingAmount;
    public int remainingTicks;

    public PendingHeal(float amount, int ticks) {
        this.remainingAmount = amount;
        this.remainingTicks = ticks;
    }

    public float tickAmount() {
        if (remainingTicks <= 0) return 0f;
        return remainingAmount / remainingTicks;
    }
}
