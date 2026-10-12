package com.terrainlab.event;

import com.terrainlab.recipe.RecipeDimensionIndex;
import com.terrainlab.recipe.RecipeUnlockService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Triggers recipe unlocking on login (full inventory scan) and on a player's first entry
 * into each dimension, which can unlock dimension-gated recipes.
 */
public final class RecipeUnlockHandler {

    private RecipeUnlockHandler() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(
            PlayerEvent.PlayerLoggedInEvent event
    ) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // The Overworld counts as visited, so its recipes are never gated.
            RecipeDimensionIndex.markDimensionVisited(
                    player,
                    Level.OVERWORLD
            );

            RecipeUnlockService.scanWholeInventory(player);
        }

    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (RecipeDimensionIndex.hasVisitedDimension(
                player,
                event.getTo()
        )) {
            return;
        }

        RecipeDimensionIndex.markDimensionVisited(
                player,
                event.getTo()
        );

        // Ingredients already held may now satisfy recipes gated behind this dimension.
        RecipeUnlockService.onDimensionVisited(player);

    }
}
