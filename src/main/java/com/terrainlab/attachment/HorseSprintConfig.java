package com.terrainlab.attachment;

import com.terrainlab.TerrainLab;
import net.minecraft.resources.Identifier;

/** Tuning constants and modifier id for horse trot scaling, sprinting and stamina. */
public class HorseSprintConfig {
    // Each horse's own rolled base speed is scaled down once on first spawn,
    // preserving breeding variance while shifting it into trot range.
    public static final double TROT_SCALE = 0.5D;

    // ADD_MULTIPLIED_TOTAL: final = base * (1 + 1.3) on top of the trot-scaled base.
    public static final double SPRINT_MULTIPLIER = 1.3D;

    public static final float MAX_STAMINA = 100.0f;
    public static final float DRAIN_PER_TICK = 100.0f / (30 * 20);   // 30s of sprint
    public static final float REGEN_PER_TICK = 100.0f / (25 * 20);   // 25s to fully recover
    public static final float RESUME_THRESHOLD = MAX_STAMINA * 0.2f; // must regen to 20% before sprinting again

    public static final Identifier SPRINT_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "horse_sprint_boost");
}
