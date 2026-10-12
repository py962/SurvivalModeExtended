package com.terrainlab.client;

import com.terrainlab.TerrainLab;
import com.terrainlab.food.FoodCategory;
import com.terrainlab.food.FoodCategoryTable;
import com.terrainlab.temperature.FoodTemperatureEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Temporary client-side diagnostic for interrupted eating: logs use start/stop and a
 * periodic heartbeat with the physical use-key state, so client input can be compared
 * against server-driven use ticks during lag spikes.
 * <p>
 * TODO: remove once the eating-interruption issue is resolved.
 */
public class FoodUseClientDebugHandler {

    private static final String TAG = "[FoodUseClientDebug]";

    private static boolean wasTrackedUseActive = false;
    private static int ticksSinceTransition = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }

        ItemStack activeStack = player.getUseItem();
        boolean trackedUseActive = player.isUsingItem() && isTrackedFoodOrDrink(activeStack);

        boolean keyUseDown = mc.options.keyUse.isDown();

        if (trackedUseActive && !wasTrackedUseActive) {
            ticksSinceTransition = 0;
            // TerrainLab.LOGGER.info(
                    // "{} USE STARTED item={} keyUseDown={} useItemRemainingTicks={} clientGameTime={}",
                    // TAG, activeStack.getItem(), keyUseDown, player.getUseItemRemainingTicks(),
                    // player.level().getGameTime());
        } else if (!trackedUseActive && wasTrackedUseActive) {
            // TerrainLab.LOGGER.info(
                    // "{} USE ENDED (client no longer sees isUsingItem+tracked item) keyUseDown={} "
                            // + "ticksObservedByClient={} clientGameTime={}",
                    // TAG, keyUseDown, ticksSinceTransition, player.level().getGameTime());
        } else if (trackedUseActive) {
            ticksSinceTransition++;
            // Heartbeat every 10 client ticks: key still down while useItemRemainingTicks stalls
            // indicates server tick lag rather than an input problem.
            if (ticksSinceTransition % 10 == 0) {
                // TerrainLab.LOGGER.info(
                        // "{} still using item={} keyUseDown={} useItemRemainingTicks={} "
                                // + "clientTicksObserved={} clientGameTime={}",
                        // TAG, activeStack.getItem(), keyUseDown, player.getUseItemRemainingTicks(),
                        // ticksSinceTransition, player.level().getGameTime());
            }
        }

        wasTrackedUseActive = trackedUseActive;
    }

    /** True for any item in {@link FoodCategoryTable} or a water bottle. */
    private static boolean isTrackedFoodOrDrink(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        FoodCategory category = FoodCategoryTable.getCategory(item);
        return category != null || FoodTemperatureEffects.isWaterBottle(stack);
    }
}
