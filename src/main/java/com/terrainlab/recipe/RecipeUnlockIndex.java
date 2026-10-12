package com.terrainlab.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.terrainlab.TerrainLab;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Maps each item to the crafting/smelting/blasting/smoking recipes that use it
 * as an ingredient, so obtaining any ingredient unlocks the recipe. Special
 * recipes are skipped.
 *
 * <p>Exclusions are read from {@code data/terrainlab/recipe_unlock/ingredient_exclusions.json}:
 * <pre>
 *   { "exclusions": [ { "ingredient": "minecraft:stick" | "#minecraft:planks",
 *                       "recipes": [ "minecraft:diamond_pickaxe", ... ] } ] }
 * </pre>
 * An excluded pair means that ingredient alone does not unlock that recipe.
 *
 * <p>The index is rebuilt lazily whenever the server's RecipeManager instance
 * changes (a new one is created on every reload), by which point tags are bound.
 */
public final class RecipeUnlockIndex {

    /** Diagnostic logging for the recipe-unlock system. */
    public static final boolean VERBOSE_LOGGING = false;

    private static final Identifier EXCLUSIONS_FILE =
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "recipe_unlock/ingredient_exclusions.json");

    private static final Set<RecipeType<?>> RECIPE_BOOK_TYPES = Set.of(
            RecipeType.CRAFTING, RecipeType.SMELTING, RecipeType.BLASTING, RecipeType.SMOKING);

    private static RecipeManager builtFor = null;
    private static Map<Item, List<RecipeHolder<?>>> byItem = Map.of();

    private RecipeUnlockIndex() {}

    /** Recipes that obtaining {@code item} should unlock. Never null. */
    public static synchronized List<RecipeHolder<?>> recipesFor(MinecraftServer server, Item item) {
        RecipeManager current = server.getRecipeManager();
        if (current != builtFor) {
            rebuild(server, current);
        }
        return byItem.getOrDefault(item, List.of());
    }

    private static void rebuild(MinecraftServer server, RecipeManager recipeManager) {
        Map<Item, Set<ResourceKey<Recipe<?>>>> exclusions = loadExclusions(server);
        Map<Item, List<RecipeHolder<?>>> map = new IdentityHashMap<>();

        int indexed = 0;
        int skippedType = 0;
        int skippedSpecial = 0;
        int skippedNoIngredients = 0;
        int excludedPairs = 0;

        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            Recipe<?> recipe = holder.value();

            if (!RECIPE_BOOK_TYPES.contains(recipe.getType())) {
                skippedType++;
                continue;
            }
            if (recipe.isSpecial()) {
                skippedSpecial++;
                continue;
            }

            Set<Item> ingredientItems = new HashSet<>();
            for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
                ingredient.items().forEach(h -> ingredientItems.add(h.value()));
            }
            ingredientItems.remove(Items.AIR);

            if (ingredientItems.isEmpty()) {
                skippedNoIngredients++;
                continue;
            }

            for (Item item : ingredientItems) {
                Set<ResourceKey<Recipe<?>>> excludedForItem = exclusions.get(item);
                if (excludedForItem != null && excludedForItem.contains(holder.id())) {
                    excludedPairs++;
                    continue;
                }
                map.computeIfAbsent(item, k -> new ArrayList<>()).add(holder);
            }
            indexed++;
        }

        byItem = map;
        builtFor = recipeManager;

        // TerrainLab.LOGGER.info(
                // "[RECIPE_UNLOCK] index rebuilt: {} recipes indexed across {} items; skipped {} non-book-type, {} special, {} with no ingredients; {} exclusion pairs applied",
                // indexed, map.size(), skippedType, skippedSpecial, skippedNoIngredients, excludedPairs);
    }

    /** item -> recipe ids that item must NOT unlock on its own. */
    private static Map<Item, Set<ResourceKey<Recipe<?>>>> loadExclusions(MinecraftServer server) {
        Map<Item, Set<ResourceKey<Recipe<?>>>> result = new IdentityHashMap<>();

        Optional<Resource> resource = server.getResourceManager().getResource(EXCLUSIONS_FILE);
        if (resource.isEmpty()) {
            // TerrainLab.LOGGER.info("[RECIPE_UNLOCK] no exclusions file at {} - no exclusions applied", EXCLUSIONS_FILE);
            return result;
        }

        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray entries = root.has("exclusions") ? root.getAsJsonArray("exclusions") : new JsonArray();

            for (JsonElement element : entries) {
                JsonObject entry = element.getAsJsonObject();
                String ingredientString = entry.get("ingredient").getAsString();

                Set<Item> items = resolveItems(ingredientString);
                if (items.isEmpty()) {
                    TerrainLab.LOGGER.warn("[RECIPE_UNLOCK] exclusion ingredient '{}' matched no items - entry ignored", ingredientString);
                    continue;
                }

                Set<ResourceKey<Recipe<?>>> recipeKeys = new HashSet<>();
                for (JsonElement r : entry.getAsJsonArray("recipes")) {
                    Identifier id = Identifier.tryParse(r.getAsString());
                    if (id == null) {
                        TerrainLab.LOGGER.warn("[RECIPE_UNLOCK] invalid recipe id '{}' in exclusions - ignored", r.getAsString());
                        continue;
                    }
                    recipeKeys.add(ResourceKey.create(Registries.RECIPE, id));
                }

                for (Item item : items) {
                    result.computeIfAbsent(item, k -> new HashSet<>()).addAll(recipeKeys);
                }
            }
            // TerrainLab.LOGGER.info("[RECIPE_UNLOCK] loaded {} exclusion entries covering {} items", entries.size(), result.size());
        } catch (Exception e) {
            TerrainLab.LOGGER.error("[RECIPE_UNLOCK] failed to read {} - no exclusions applied", EXCLUSIONS_FILE, e);
            result.clear();
        }
        return result;
    }

    /** "minecraft:stick" -> {stick};  "#minecraft:planks" -> every item in the tag. */
    private static Set<Item> resolveItems(String s) {
        Set<Item> items = new HashSet<>();
        if (s.startsWith("#")) {
            Identifier tagId = Identifier.tryParse(s.substring(1));
            if (tagId == null) return items;
            TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
            for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                items.add(h.value());
            }
        } else {
            Identifier id = Identifier.tryParse(s);
            if (id == null) return items;
            BuiltInRegistries.ITEM.getOptional(id).ifPresent(items::add);
        }
        items.remove(Items.AIR);
        return items;
    }
}
