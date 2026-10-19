package com.terrainlab.client;

import com.terrainlab.TerrainLab;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Draws "Survival Mode Extended" in gold under the Minecraft logo and edition banner on the title screen. */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public final class TitleScreenNameClient {
    /** Vanilla LogoRenderer: logo top at y=30, 44 high; edition banner overlaps by 7 and is 14 high (ends at y=81). */
    private static final int Y = 30 + 44 - 7 + 14 + 3;
    private static final int GOLD = 0xFFFFAA00;

    private TitleScreenNameClient() {}

    @SubscribeEvent
    static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen)) return;
        event.getGuiGraphics().centeredText(screen.getFont(), Component.translatable("menu.terrainlab.title_subtitle"),
                screen.width / 2, Y, GOLD);
    }
}
