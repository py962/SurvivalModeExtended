package com.terrainlab.util;

import net.minecraft.world.entity.player.Player;

/**
 * Experience-point maths based on level and progress bar. {@code Player#totalExperience} is not
 * used because spending levels (enchanting, anvils) does not reduce it.
 */
public final class XpMath {

    private XpMath() {
    }

    public static int xpNeededForLevel(int level) {
        if (level >= 30) return 112 + (level - 30) * 9;
        if (level >= 15) return 37 + (level - 15) * 5;
        return 7 + level * 2;
    }

    /** XP points needed to reach the start of {@code level} from zero. */
    public static long xpToReachLevel(int level) {
        long total = 0;
        for (int i = 0; i < level; i++) {
            total += xpNeededForLevel(i);
        }
        return total;
    }

    /** Spendable XP points: whole levels plus the current progress bar. */
    public static int realTotalXp(Player player) {
        long total = xpToReachLevel(player.experienceLevel)
                + Math.round(player.experienceProgress * xpNeededForLevel(player.experienceLevel));
        return (int) Math.min(Integer.MAX_VALUE, total);
    }
}
