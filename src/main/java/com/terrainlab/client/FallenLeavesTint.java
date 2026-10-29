package com.terrainlab.client;

import com.terrainlab.TerrainLab;
import com.terrainlab.forestfloor.FallenLeavesBlock;
import com.terrainlab.forestfloor.ForestFloor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Tints fallen leaves by the tree they came from. */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public final class FallenLeavesTint {
    private FallenLeavesTint() {}

    @SubscribeEvent
    public static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> state.getValue(FallenLeavesBlock.TREE).color,
                ForestFloor.FALLEN_LEAVES.get());
    }
}
