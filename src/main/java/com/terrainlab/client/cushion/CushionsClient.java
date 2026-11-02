package com.terrainlab.client.cushion;

import com.terrainlab.TerrainLab;
import com.terrainlab.cushion.Cushions;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public final class CushionsClient {
    private CushionsClient() {}

    /** Vanilla's creative colour order (white, greys, black, then the spectrum). */
    private static final java.util.List<DyeColor> GAMEPLAY_ORDER = java.util.List.of(
            DyeColor.WHITE, DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.BLACK, DyeColor.BROWN, DyeColor.RED,
            DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.LIME, DyeColor.GREEN, DyeColor.CYAN, DyeColor.LIGHT_BLUE,
            DyeColor.BLUE, DyeColor.PURPLE, DyeColor.MAGENTA, DyeColor.PINK);

    @SubscribeEvent
    static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CushionRenderer.LAYER, CushionModel::createBodyLayer);
    }

    @SubscribeEvent
    static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Cushions.CUSHION.get(), CushionRenderer::new);
    }

    /** In the Coloured Blocks tab after the beds, as in 26.3. */
    @SubscribeEvent
    static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.COLORED_BLOCKS) return;
        ItemStack previous = new ItemStack(Items.PINK_BED);
        for (DyeColor color : GAMEPLAY_ORDER) {
            ItemStack stack = new ItemStack(Cushions.item(color));
            event.insertAfter(previous, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            previous = stack;
        }
    }
}
