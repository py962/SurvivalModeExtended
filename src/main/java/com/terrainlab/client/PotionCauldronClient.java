package com.terrainlab.client;

import com.terrainlab.TerrainLab;
import com.terrainlab.blockentity.PotionCauldronBlockEntity;
import com.terrainlab.item.PotionMixing;
import com.terrainlab.registry.ModBlocks;
import java.util.List;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * Tints the potion cauldron's liquid (the vanilla water-cauldron models' tintindex 0 content face)
 * with the blended colour stored in its block entity. Break particles stay untinted.
 */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public final class PotionCauldronClient {

    private PotionCauldronClient() {
    }

    @SubscribeEvent
    static void onRegisterBlockTints(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(List.of(new BlockTintSource() {
            @Override
            public int color(BlockState state) {
                return PotionMixing.blendColor(List.of());
            }

            @Override
            public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                return level.getBlockEntity(pos) instanceof PotionCauldronBlockEntity cauldron
                        ? cauldron.getColor() : color(state);
            }

            @Override
            public int colorAsTerrainParticle(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                return -1;
            }
        }), ModBlocks.POTION_CAULDRON.get());
    }
}
