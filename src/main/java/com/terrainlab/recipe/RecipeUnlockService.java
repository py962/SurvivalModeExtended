package com.terrainlab.recipe;

import com.terrainlab.TerrainLab;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * Awards recipe-book recipes when the player obtains any of their ingredients.
 * Dimension-gated recipes are only awarded once every required dimension has been visited.
 */
public final class RecipeUnlockService {

    private RecipeUnlockService() {}

    public static void onItemObtained(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        MinecraftServer server = player.level().getServer();

        if (server == null) {
            if (RecipeUnlockIndex.VERBOSE_LOGGING) {
                // TerrainLab.LOGGER.warn(
                        // "[RECIPE_UNLOCK] no server for {} - skipped",
                        // player.getName().getString()
                // );
            }
            return;
        }

        List<RecipeHolder<?>> recipes =
                RecipeUnlockIndex.recipesFor(server, stack.getItem());

        if (recipes.isEmpty()) {
            return;
        }

        int awarded = 0;
        int blockedByDimension = 0;

        for (RecipeHolder<?> recipe : recipes) {
            if (!RecipeDimensionIndex.canUnlock(server, player, recipe)) {
                blockedByDimension++;
                continue;
            }

            awarded += player.awardRecipes(List.of(recipe));
        }

        if (RecipeUnlockIndex.VERBOSE_LOGGING &&
                (awarded > 0 || blockedByDimension > 0)) {

            // TerrainLab.LOGGER.info(
                    // "[RECIPE_UNLOCK] {} obtained {} -> {} new recipes unlocked, {} blocked by dimension",
                    // player.getName().getString(),
                    // stack.getItem(),
                    // awarded,
                    // blockedByDimension
            // );
        }
    }

    /** Runs {@link #onItemObtained} for every non-empty inventory slot (login and first dimension visit). */
    public static void scanWholeInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();

        int nonEmpty = 0;

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);

            if (!stack.isEmpty()) {
                nonEmpty++;
                onItemObtained(player, stack);
            }
        }

        if (RecipeUnlockIndex.VERBOSE_LOGGING) {
            // TerrainLab.LOGGER.info(
                    // "[RECIPE_UNLOCK] inventory scan for {}: {} non-empty slots checked",
                    // player.getName().getString(),
                    // nonEmpty
            // );
        }
    }

    /**
     * Rescans the inventory after a first dimension visit so newly ungated
     * recipes unlock from ingredients already held.
     */
    public static void onDimensionVisited(
            ServerPlayer player
    ) {
        scanWholeInventory(player);
    }
}
