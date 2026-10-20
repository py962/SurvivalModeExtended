package com.terrainlab.client;

import com.terrainlab.TerrainLab;
import com.terrainlab.advancement.AccomplishmentCard;
import com.terrainlab.screen.AccomplishmentScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ToastAddEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds a received accomplishment card until it can be shown: the card opens once the player is
 * in the world with no screen open (so it waits for the credits, the level-loading screen, chat,
 * etc. to close) for {@link #SETTLE_TICKS} consecutive ticks.
 * <p>
 * Advancement toasts that arrive while a card is pending or open are held back and shown once it
 * closes: the card hides the HUD, and hidden toasts would otherwise time out unseen behind it.
 */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public final class AccomplishmentCardClient {

    private static final int SETTLE_TICKS = 10;

    private static AccomplishmentCard pending;
    private static int idleTicks;
    private static final List<Toast> heldToasts = new ArrayList<>();

    private AccomplishmentCardClient() {
    }

    public static void queue(AccomplishmentCard card) {
        pending = card;
        idleTicks = 0;
    }

    private static boolean cardActive() {
        return pending != null || Minecraft.getInstance().gui.screen() instanceof AccomplishmentScreen;
    }

    @SubscribeEvent
    public static void onToastAdd(ToastAddEvent event) {
        if (event.getToast() instanceof AdvancementToast && cardActive()) {
            heldToasts.add(event.getToast());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (pending == null) {
            if (!heldToasts.isEmpty() && !cardActive()) {
                List<Toast> toasts = new ArrayList<>(heldToasts);
                heldToasts.clear();
                toasts.forEach(mc.gui.toastManager()::addToast);
            }
            return;
        }
        if (mc.player == null || mc.level == null || !mc.player.isAlive() || mc.gui.screen() != null) {
            idleTicks = 0;
            return;
        }
        if (++idleTicks < SETTLE_TICKS) {
            return;
        }
        AccomplishmentCard card = pending;
        pending = null;
        idleTicks = 0;
        mc.gui.setScreen(new AccomplishmentScreen(card));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        pending = null;
        heldToasts.clear();
        idleTicks = 0;
    }
}
