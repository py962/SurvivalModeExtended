package com.terrainlab.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Determines which recipe-book recipes are gated behind visiting a dimension:
 * a recipe is gated by every dimension that one of its ingredients belongs to
 * (e.g. Soul Torch requires the Nether because of Soul Sand). An ingredient
 * belongs to a dimension only if every item it accepts is exclusive to it, so
 * tag ingredients such as #planks are not gated. Also tracks the dimensions
 * each player has visited.
 */
public final class RecipeDimensionIndex {

    private static final Set<RecipeType<?>> RECIPE_BOOK_TYPES = Set.of(
            RecipeType.CRAFTING,
            RecipeType.SMELTING,
            RecipeType.BLASTING,
            RecipeType.SMOKING
    );

    private static RecipeManager builtFor = null;

    private static Map<ResourceKey<Level>, Set<Item>> dimensionItems = Map.of();

    private static Map<ResourceKey<Recipe<?>>, Set<ResourceKey<Level>>> recipeDimensions =
            Map.of();

    private RecipeDimensionIndex() {}

    /** Dimensions that must have been visited before this recipe may be unlocked; empty if none. */
    public static synchronized Set<ResourceKey<Level>> requiredDimensions(
            MinecraftServer server,
            RecipeHolder<?> holder
    ) {
        RecipeManager current = server.getRecipeManager();

        if (current != builtFor) {
            rebuild(current);
        }

        return recipeDimensions.getOrDefault(holder.id(), Set.of());
    }

    /** Returns true if the player has visited every dimension the recipe requires. */
    public static boolean canUnlock(
            MinecraftServer server,
            ServerPlayer player,
            RecipeHolder<?> holder
    ) {
        Set<ResourceKey<Level>> required = requiredDimensions(server, holder);

        if (required.isEmpty()) {
            return true;
        }

        for (ResourceKey<Level> dimension : required) {
            if (!hasVisitedDimension(player, dimension)) {
                return false;
            }
        }

        return true;
    }

    /** Returns true if the player has already visited this dimension. */
    public static boolean hasVisitedDimension(
            ServerPlayer player,
            ResourceKey<Level> dimension
    ) {
        String key = visitedKey(dimension);
        CompoundTag data = player.getPersistentData();

        if (data.getCompoundOrEmpty(Player.PERSISTED_NBT_TAG).getBooleanOr(key, false)) {
            return true;
        }

        // Older saves keep the flag at the root, which does not survive death; move it across.
        if (data.getBooleanOr(key, false)) {
            markDimensionVisited(player, dimension);
            return true;
        }

        return false;
    }

    /** Marks a dimension as visited; stored under PlayerPersisted so it survives death and End exit. */
    public static void markDimensionVisited(
            ServerPlayer player,
            ResourceKey<Level> dimension
    ) {
        String key = visitedKey(dimension);
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompoundOrEmpty(Player.PERSISTED_NBT_TAG);

        if (!persisted.getBooleanOr(key, false)) {
            persisted.putBoolean(key, true);
            data.put(Player.PERSISTED_NBT_TAG, persisted);
        }
    }

    /** Builds the recipe -> required dimensions map. */
    private static void rebuild(RecipeManager recipeManager) {

        Map<ResourceKey<Level>, Set<Item>> itemsByDimension = new HashMap<>();

        Set<Item> nether = new HashSet<>();

        add(
                nether,

                Items.NETHERRACK,
                Items.SOUL_SAND,
                Items.SOUL_SOIL,

                Items.NETHER_BRICK,
                Items.NETHER_BRICKS,

                Items.CRIMSON_STEM,
                Items.CRIMSON_HYPHAE,
                Items.WARPED_STEM,
                Items.WARPED_HYPHAE,

                Items.CRIMSON_PLANKS,
                Items.WARPED_PLANKS,

                Items.CRIMSON_SLAB,
                Items.WARPED_SLAB,
                Items.CRIMSON_STAIRS,
                Items.WARPED_STAIRS,

                Items.CRIMSON_FENCE,
                Items.WARPED_FENCE,
                Items.CRIMSON_FENCE_GATE,
                Items.WARPED_FENCE_GATE,

                Items.CRIMSON_DOOR,
                Items.WARPED_DOOR,
                Items.CRIMSON_TRAPDOOR,
                Items.WARPED_TRAPDOOR,

                Items.CRIMSON_PRESSURE_PLATE,
                Items.WARPED_PRESSURE_PLATE,
                Items.CRIMSON_BUTTON,
                Items.WARPED_BUTTON,

                Items.NETHER_WART,
                Items.NETHER_WART_BLOCK,
                Items.WARPED_WART_BLOCK,

                Items.BLAZE_ROD,
                Items.BLAZE_POWDER,
                Items.GHAST_TEAR,
                Items.MAGMA_CREAM,

                Items.QUARTZ,
                Items.QUARTZ_BLOCK,
                Items.QUARTZ_BRICKS,
                Items.QUARTZ_PILLAR,

                Items.GLOWSTONE_DUST,
                Items.GLOWSTONE,

                Items.NETHER_GOLD_ORE,
                Items.ANCIENT_DEBRIS,
                Items.NETHERITE_SCRAP,
                Items.NETHERITE_INGOT,
                Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE,

                Items.WITHER_SKELETON_SKULL,
                Items.PIGLIN_HEAD,

                Items.CRYING_OBSIDIAN

        );

        itemsByDimension.put(Level.NETHER, nether);

        Set<Item> end = new HashSet<>();

        add(
                end,

                Items.END_STONE,
                Items.END_STONE_BRICKS,

                Items.PURPUR_BLOCK,
                Items.PURPUR_PILLAR,
                Items.PURPUR_STAIRS,
                Items.PURPUR_SLAB,

                Items.CHORUS_FRUIT,
                Items.POPPED_CHORUS_FRUIT,
                Items.CHORUS_FLOWER,

                Items.END_ROD,
                Items.DRAGON_BREATH,
                Items.DRAGON_HEAD,
                Items.SHULKER_SHELL,
                Items.ELYTRA

        );

        itemsByDimension.put(Level.END, end);

        dimensionItems = itemsByDimension;

        Map<ResourceKey<Recipe<?>>, Set<ResourceKey<Level>>> result =
                new HashMap<>();

        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {

            Recipe<?> recipe = holder.value();

            if (!RECIPE_BOOK_TYPES.contains(recipe.getType())) {
                continue;
            }

            if (recipe.isSpecial()) {
                continue;
            }

            Set<ResourceKey<Level>> required = new HashSet<>();

            for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
                Set<Item> alternatives = new HashSet<>();

                ingredient.items().forEach(holderItem -> {
                    Item item = holderItem.value();

                    if (item != Items.AIR) {
                        alternatives.add(item);
                    }
                });

                if (alternatives.isEmpty()) {
                    continue;
                }

                // Gate only when no alternative is obtainable outside the dimension.
                for (Map.Entry<ResourceKey<Level>, Set<Item>> entry :
                        dimensionItems.entrySet()) {

                    if (entry.getValue().containsAll(alternatives)) {
                        required.add(entry.getKey());
                    }
                }

            }

            if (!required.isEmpty()) {
                result.put(holder.id(), required);
            }
        }

        recipeDimensions = result;
        builtFor = recipeManager;
    }

    private static void add(Set<Item> set, Item... items) {
        for (Item item : items) {
            if (item != Items.AIR) {
                set.add(item);
            }
        }
    }

    /** Persistent-data key for a visited dimension, e.g. {@code terrainlab.recipe_unlock.visited.minecraft.the_nether}. */
    private static String visitedKey(ResourceKey<Level> dimension) {
        Identifier id = dimension.identifier();

        return "terrainlab.recipe_unlock.visited."
                + id.getNamespace()
                + "."
                + id.getPath().replace('/', '.');
    }
}
