package com.terrainlab.difficulty;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;

/**
 * Per-player local difficulty, replacing vanilla's world-age and chunk-time terms.
 *
 * <p>Normal and Hard (including hardcore) only; Easy and Peaceful keep vanilla.
 *
 * <p>Scale = 0.75 + time + kills + moon, multiplied by the world difficulty (Normal 2, Hard 3):
 * <ul>
 *   <li>time: up to +0.25, reached after 50 hours played (vanilla allows +1.25 from time);</li>
 *   <li>kills: +0.01 per kill (mobs of any kind, animals included, and players), up to +2.0 at 200 kills;</li>
 *   <li>moon: vanilla's full-moon bonus (up to +0.25), never more than the time term.</li>
 * </ul>
 * The nearest player within {@link #PLAYER_RANGE} blocks decides; with nobody near, vanilla applies.
 */
public final class PlayerDifficulty {
    public static final double PLAYER_RANGE = 128.0;

    private static final float BASE_SCALE = 0.75F;
    private static final float MAX_TIME_SCALE = 0.25F;
    private static final float FULL_TIME_TICKS = 50.0F * 60 * 60 * 20; // 50 hours played
    private static final float SCALE_PER_KILL = 0.01F;
    private static final float MAX_KILL_SCALE = 2.0F;
    private static final float MAX_MOON_SCALE = 0.25F;

    private PlayerDifficulty() {}

    /** Effective difficulty at pos from the nearest player, or NaN when no player is in range. */
    public static float effectiveAt(ServerLevel level, BlockPos pos) {
        Difficulty base = level.getDifficulty();
        // Easy keeps vanilla local difficulty; Normal and Hard (including hardcore) use the player score.
        if (base != Difficulty.NORMAL && base != Difficulty.HARD) return Float.NaN;
        Player nearest = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                PLAYER_RANGE, p -> !p.isSpectator());
        if (!(nearest instanceof ServerPlayer player)) return Float.NaN;

        int playTicks = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        float time = MAX_TIME_SCALE * Mth.clamp(playTicks / FULL_TIME_TICKS, 0.0F, 1.0F);
        // Same kill count as the Pacifist challenge: vanilla mob kills (animals included) plus player kills.
        int killCount = player.getStats().getValue(Stats.CUSTOM.get(Stats.MOB_KILLS))
                + player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS));
        float kills = Math.min(MAX_KILL_SCALE, SCALE_PER_KILL * killCount);
        float moon = Mth.clamp(level.getMoonBrightness(pos) * MAX_MOON_SCALE, 0.0F, time);
        return base.getId() * (BASE_SCALE + time + kills + moon);
    }
}
