package com.terrainlab.item;

import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** The player taking a crafted item, while its post-processing runs (for centring zoomed maps on them). */
public final class MapZoomCrafter {

    private MapZoomCrafter() {
    }

    private static final ThreadLocal<Player> CURRENT = new ThreadLocal<>();

    public static void set(@Nullable Player player) {
        if (player == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(player);
        }
    }

    public static @Nullable Player get() {
        return CURRENT.get();
    }
}
