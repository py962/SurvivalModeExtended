package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;

/**
 * Poplar saplings grow the same tree the mod generates in the wild: the orange poplar with leaf litter.
 * Vanilla's poplar sapling picks a red, orange or yellow poplar; this swaps whichever it picked. The
 * event only fires for growth from saplings (and fungi and mushrooms), so worldgen is unaffected.
 * The 1.21.11 version does the same through its sapling's terrainlab:poplar feature.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class PoplarSaplingGrowthHandler {

    private static final Set<ResourceKey<Feature>> SAPLING_POPLARS =
            Set.of(TreeFeatures.RED_POPLAR, TreeFeatures.ORANGE_POPLAR, TreeFeatures.YELLOW_POPLAR);

    private PoplarSaplingGrowthHandler() {
    }

    @SubscribeEvent
    public static void onBlockGrowFeature(BlockGrowFeatureEvent event) {
        Holder<Feature> feature = event.getFeature();
        if (feature != null && feature.unwrapKey().filter(SAPLING_POPLARS::contains).isPresent()) {
            event.setFeature(TreeFeatures.ORANGE_POPLAR_LEAF_LITTER);
        }
    }
}
