package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * Makes Cocoa Beans edible by adding default FOOD and CONSUMABLE components. Vanilla's
 * {@code Item.use} only starts the eat flow when CONSUMABLE is present; the energy effect itself
 * is applied by {@code ModFoodEffectsHandler}. {@code alwaysEdible} is required because the
 * beans give no nutrition.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class CocoaBeansFoodHandler {

    private static final float CONSUME_SECONDS = 1.6F; // seconds; vanilla default eat duration

    @SubscribeEvent
    public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        // TerrainLab.LOGGER.info("[CocoaBeans] applying FOOD + CONSUMABLE default component patch");

        FoodProperties food = new FoodProperties.Builder()
                .nutrition(0)
                .saturationModifier(0.0F)
                .alwaysEdible()
                .build();

        Consumable consumable = Consumable.builder()
                .consumeSeconds(CONSUME_SECONDS)
                .animation(ItemUseAnimation.EAT)
                .sound(SoundEvents.GENERIC_EAT)
                .build();

        event.modify(Items.COCOA_BEANS, (builder, context, it) -> builder
                .set(DataComponents.FOOD, food)
                .set(DataComponents.CONSUMABLE, consumable));

        // TerrainLab.LOGGER.info("[CocoaBeans] CONSUMABLE set: consumeSeconds={}, animation={}", CONSUME_SECONDS, ItemUseAnimation.EAT);
    }
}
