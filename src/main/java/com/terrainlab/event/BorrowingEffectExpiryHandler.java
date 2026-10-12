package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.api.BorrowingAccess;
import com.terrainlab.registry.ModMobEffects;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * Wakes a borrowing player when the Borrowing effect's timer runs out while they are still asleep.
 * Uses {@link MobEffectEvent.Expired}, which fires only on natural expiry (not milk or commands);
 * {@code stopSleepInBed} runs the normal wake path so the borrow session is cleaned up.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class BorrowingEffectExpiryHandler {

    private BorrowingEffectExpiryHandler() {
    }

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        if (!event.getEffectInstance().is(ModMobEffects.BORROWING)) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!(player instanceof BorrowingAccess access)) {
            return;
        }

        if (access.terrainlab$isBorrowing() && player.isSleeping()) {
            // TerrainLab.LOGGER.info("[BORROWING] {} - Borrowing effect expired mid-sleep, waking",
                    // player.getName().getString());
            player.stopSleepInBed(false, true);
        }
    }
}
