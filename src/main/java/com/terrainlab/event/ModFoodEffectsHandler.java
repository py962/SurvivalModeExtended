package com.terrainlab.event;

import com.terrainlab.energy.PlayerEnergyConstants;
import com.terrainlab.food.FoodCategory;
import com.terrainlab.food.FoodCategoryTable;
import com.terrainlab.food.PendingHealSystem;
import com.terrainlab.registry.ModAttachments;
import com.terrainlab.temperature.FoodTemperatureEffects;
import com.terrainlab.temperature.PlayerInternalTemperature;
import com.terrainlab.temperature.PlayerTemperatureConstants;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Food and drink consumption effects. For items in {@link FoodCategoryTable}, vanilla's
 * hunger gain is undone (using the hunger snapshot taken at use start) and the category
 * effect is applied instead; Water Bottles and Blue Orchid / Oxeye Daisy Suspicious Stew
 * get their own effects. Other items are left to vanilla.
 */
public class ModFoodEffectsHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModFoodEffectsHandler.class);

    @SubscribeEvent
    public static void onItemStartUsing(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Item item = event.getItem().getItem();
        FoodCategory category = FoodCategoryTable.getCategory(item);

        if (category == null) {
            return;
        }

        int currentHunger = player.getFoodData().getFoodLevel();
        player.setData(ModAttachments.PLAYER_PRE_EAT_HUNGER.get(), currentHunger);
        player.setData(ModAttachments.PLAYER_PRE_EAT_SATURATION.get(), player.getFoodData().getSaturationLevel());
    }

    @SubscribeEvent
    public static void onItemFinishUsing(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack stack = event.getItem();
        Item item = stack.getItem();
        FoodCategory category = FoodCategoryTable.getCategory(item);

        // Vanilla consumption (stack shrink + FoodData.eat) has already happened by this point.

        if (category != null) {
            int preHunger = player.getData(ModAttachments.PLAYER_PRE_EAT_HUNGER.get());

            player.getFoodData().setFoodLevel(preHunger);
            player.getFoodData().setSaturation(player.getData(ModAttachments.PLAYER_PRE_EAT_SATURATION.get()));

            try {
                switch (category) {
                    case HEALTH -> {
                        var healthData = FoodCategoryTable.getHealthData(item);
                        if (healthData != null) {
                            if (healthData.durationTicks() <= 0) {
                                player.heal(healthData.amount());
                            } else {
                                PendingHealSystem.queueHeal(player, healthData.amount(), healthData.durationTicks());
                            }
                        } else {
                            LOGGER.warn("[TerrainLabFoodDebug] FINISH HEALTH category but healthData is NULL for item={} - no effect will be applied", item);
                        }
                    }
                    case COOL -> {
                        PlayerInternalTemperature.applyInstantPush(player, PlayerTemperatureConstants.COOLING_FOOD_PUSH);
                    }
                    case WARMTH -> {
                        PlayerInternalTemperature.applyInstantPush(player, PlayerTemperatureConstants.WARMING_FOOD_PUSH);
                    }
                    case ENERGY -> {
                        boolean consumedUse = com.terrainlab.energy.PlayerEnergyFoodCap.tryConsumeUse(player);
                        if (consumedUse) {
                            float energy = player.getData(com.terrainlab.registry.ModAttachments.PLAYER_ENERGY.get());
                            float newEnergy = Math.min(PlayerEnergyConstants.MAX_ENERGY, energy + PlayerEnergyConstants.ENERGY_FOOD_PUSH);
                            player.setData(com.terrainlab.registry.ModAttachments.PLAYER_ENERGY.get(), newEnergy);
                        } else {
                        }
                    }
                    case HUNGER_1, HUNGER_2, HUNGER_3 -> {
                        Integer points = FoodCategoryTable.getHungerPoints(item);
                        if (points != null) {
                            int newHunger = Math.min(20, preHunger + points);
                            player.getFoodData().setFoodLevel(newHunger);
                        } else {
                            LOGGER.warn("[TerrainLabFoodDebug] FINISH {} category but points is NULL for item={} - hunger stays at preHunger={}",
                                    category, item, preHunger);
                        }
                    }
                    case RISKY -> LOGGER.warn("[TerrainLabFoodDebug] FINISH RISKY category reached for item={} - should be unreachable, RISKY items should be absent from the table", item);
                }
            } catch (Exception ex) {
                LOGGER.error("[TerrainLabFoodDebug] FINISH EXCEPTION in category={} branch for item={} stackCountAtFinish={} preHunger={}",
                        category, item, stack.getCount(), preHunger, ex);
                throw ex;
            }

            return;
        }

        // Suspicious Stew: Blue Orchid and Oxeye Daisy are remapped here; Dandelion's WARMTH is a real effect vanilla applies.
        if (item == net.minecraft.world.item.Items.SUSPICIOUS_STEW) {
            String flower = stack.get(com.terrainlab.registry.ModDataComponents.STEW_FLOWER.get());
            if ("blue_orchid".equals(flower)
                    && com.terrainlab.energy.PlayerEnergyFoodCap.tryConsumeUse(player)) {
                float energy = player.getData(com.terrainlab.registry.ModAttachments.PLAYER_ENERGY.get());
                player.setData(com.terrainlab.registry.ModAttachments.PLAYER_ENERGY.get(),
                        Math.min(PlayerEnergyConstants.MAX_ENERGY, energy + PlayerEnergyConstants.ENERGY_FOOD_PUSH));
            } else if ("oxeye_daisy".equals(flower)) {
                player.heal(3.0F);
            }
            return;
        }
        // Water Bottle: no hunger to undo, cooling push only.
        if (FoodTemperatureEffects.isWaterBottle(stack)) {
            PlayerInternalTemperature.applyInstantPush(player, PlayerTemperatureConstants.COOLING_FOOD_PUSH);
            return;
        }

    }
}
