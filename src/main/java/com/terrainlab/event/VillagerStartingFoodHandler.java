package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.village.VillagerHungerDebug;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Gives every villager {@link #STARTING_BREAD} loaves the first time it enters a world (spawned,
 * born, generated with a village, or already in a world from before this change), so villagers
 * don't all start out hungry. Vanilla villagers start with no food at all. A flag in the
 * villager's persistent data stops it happening twice.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class VillagerStartingFoodHandler {

    /** Two loaves: 8 food points, above the hunger threshold of 4 and below vanilla's breeding 12. */
    public static final int STARTING_BREAD = 2;

    private static final String GIVEN_TAG = "terrainlab_starting_food";

    private VillagerStartingFoodHandler() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (villager.getPersistentData().getBooleanOr(GIVEN_TAG, false)) return;

        villager.getPersistentData().putBoolean(GIVEN_TAG, true);
        villager.getInventory().addItem(new ItemStack(Items.BREAD, STARTING_BREAD));
        VillagerHungerDebug.startingFood(villager, STARTING_BREAD);
    }
}
