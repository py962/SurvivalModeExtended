package com.terrainlab.client;

import com.terrainlab.TerrainLab;
import com.terrainlab.blockentity.PotionCauldronBlockEntity;
import com.terrainlab.item.PotionMixing;
import com.terrainlab.registry.ModBlocks;
import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * Tints the potion cauldron's liquid (the vanilla water-cauldron models' tintindex 0 content face)
 * with the blended colour stored in its block entity.
 */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public final class PotionCauldronClient {

    private static final int DEFAULT_COLOR = PotionMixing.blendColor(List.of());

    private PotionCauldronClient() {
    }

    @SubscribeEvent
    static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) ->
                        level != null && pos != null && level.getBlockEntity(pos) instanceof PotionCauldronBlockEntity cauldron
                                ? cauldron.getColor() : DEFAULT_COLOR,
                ModBlocks.POTION_CAULDRON.get());
    }
}
