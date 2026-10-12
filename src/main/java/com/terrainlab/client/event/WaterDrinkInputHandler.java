package com.terrainlab.client.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.network.WaterDrinkInputPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Sends the raw use-key state to the server every client tick; the server does all validation. */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public class WaterDrinkInputHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;

        boolean keyDown = mc.options.keyUse.isDown();
        mc.getConnection().send(new WaterDrinkInputPacket(keyDown));
    }
}
