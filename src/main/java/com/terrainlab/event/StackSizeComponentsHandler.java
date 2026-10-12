package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

import java.util.List;

/**
 * Raises stack sizes so villager trades hand over and accept their full counts, with related
 * items matched for consistency: stews, beds and book and quill to 16; filled buckets,
 * potions and leather flasks to 3. Items with durability cannot stack, so shears and
 * shields are traded singly instead.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class StackSizeComponentsHandler {

    private static final int LARGE_TRADE_STACK = 16;
    private static final int SMALL_TRADE_STACK = 3;

    // Stews and the book and quill, matching written books.
    private static final List<Item> LARGE_STACK_ITEMS = List.of(
            Items.RABBIT_STEW, Items.MUSHROOM_STEW, Items.BEETROOT_SOUP, Items.SUSPICIOUS_STEW,
            Items.WRITABLE_BOOK);

    // Filled buckets and potions.
    private static final List<Item> SMALL_STACK_ITEMS = List.of(
            Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.MILK_BUCKET, Items.POWDER_SNOW_BUCKET,
            Items.COD_BUCKET, Items.SALMON_BUCKET, Items.PUFFERFISH_BUCKET, Items.TROPICAL_FISH_BUCKET,
            Items.AXOLOTL_BUCKET, Items.TADPOLE_BUCKET,
            Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION);

    private StackSizeComponentsHandler() {
    }

    @SubscribeEvent
    public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        for (Item item : LARGE_STACK_ITEMS) {
            event.modify(item, patch -> patch.set(DataComponents.MAX_STACK_SIZE, LARGE_TRADE_STACK));
        }
        event.modifyMatching(item -> item instanceof BedItem,
                patch -> patch.set(DataComponents.MAX_STACK_SIZE, LARGE_TRADE_STACK));

        for (Item item : SMALL_STACK_ITEMS) {
            event.modify(item, patch -> patch.set(DataComponents.MAX_STACK_SIZE, SMALL_TRADE_STACK));
        }
        event.modify(ModItems.LEATHER_FLASK.get(), patch -> patch.set(DataComponents.MAX_STACK_SIZE, SMALL_TRADE_STACK));
    }
}
