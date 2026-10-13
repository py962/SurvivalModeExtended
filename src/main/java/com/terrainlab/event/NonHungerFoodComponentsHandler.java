package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * Makes HEALTH, COOL and WARMTH foods from {@link com.terrainlab.food.FoodCategoryTable}
 * edible at full hunger by overriding their default FOOD component with
 * {@code alwaysEdible}, keeping vanilla nutrition and saturation (the hunger gain itself is
 * undone by {@link ModFoodEffectsHandler}).
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class NonHungerFoodComponentsHandler {

    @SubscribeEvent
    public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        // HEALTH tier
        setAlwaysEdible(event, Items.CARROT, 3, 0.6F);
        setAlwaysEdible(event, Items.APPLE, 4, 0.3F);
        setAlwaysEdible(event, Items.GOLDEN_CARROT, 6, 1.2F);

        // COOL tier
        setAlwaysEdible(event, Items.MELON_SLICE, 2, 0.3F);

        // WARMTH tier
        setAlwaysEdible(event, Items.MUSHROOM_STEW, 6, 0.6F);
        setAlwaysEdible(event, Items.RABBIT_STEW, 10, 0.6F);
        setAlwaysEdible(event, Items.BEETROOT_SOUP, 6, 0.6F);
    }

    private static void setAlwaysEdible(ModifyDefaultComponentsEvent event, Item item, int nutrition, float saturationModifier) {
        event.modify(item, (builder, context, it) -> builder.set(
                DataComponents.FOOD,
                new FoodProperties.Builder()
                        .nutrition(nutrition)
                        .saturationModifier(saturationModifier)
                        .alwaysEdible()
                        .build()
        ));
    }
}
