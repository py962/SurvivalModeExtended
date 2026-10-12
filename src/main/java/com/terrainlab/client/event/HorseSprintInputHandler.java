package com.terrainlab.client.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.network.HorseSprintInputPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.animal.equine.Horse;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Sends the sprint key state to the server every client tick while riding a Horse (not donkeys or mules). */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public class HorseSprintInputHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        if (!(mc.player.getVehicle() instanceof Horse)) return;

        boolean wantsSprint = mc.options.keySprint.isDown();
        mc.getConnection().send(new HorseSprintInputPacket(wantsSprint));
    }
}
