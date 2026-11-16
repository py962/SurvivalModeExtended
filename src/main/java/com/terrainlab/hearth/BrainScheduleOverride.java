package com.terrainlab.hearth;

import net.minecraft.world.entity.schedule.Activity;
import org.jspecify.annotations.Nullable;

/** Replaces the scheduled activity while set. */
public interface BrainScheduleOverride {
    void terrainlab$setScheduleOverride(@Nullable Activity activity);
}
