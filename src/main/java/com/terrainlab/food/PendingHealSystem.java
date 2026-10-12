package com.terrainlab.food;

import com.terrainlab.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;

import java.util.Iterator;
import java.util.List;

/** Queues and ticks per-player heal-over-time effects from HEALTH-tier food. */
public final class PendingHealSystem {

    private PendingHealSystem() {
    }

    public static void queueHeal(ServerPlayer player, float amount, int ticks) {
        List<PendingHeal> heals = player.getData(ModAttachments.PLAYER_PENDING_HEALS.get());
        heals.add(new PendingHeal(amount, ticks));
    }

    public static void tick(ServerPlayer player) {
        List<PendingHeal> heals = player.getData(ModAttachments.PLAYER_PENDING_HEALS.get());
        if (heals.isEmpty()) return;

        Iterator<PendingHeal> it = heals.iterator();
        while (it.hasNext()) {
            PendingHeal heal = it.next();
            float delivered = heal.tickAmount();
            player.heal(delivered);
            heal.remainingAmount -= delivered;
            heal.remainingTicks--;
            if (heal.remainingTicks <= 0) {
                it.remove();
            }
        }
    }
}
