package com.terrainlab.event;

import com.terrainlab.TerrainLab;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.GrindstoneEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Grindstone salvage: a single tool / weapon / armour piece with no enchantments other than curses in the grindstone yields
 * its crafting materials (sticks and the mace's breeze rod excluded), scaled by remaining durability
 * and rounded down. Netherite items yield only netherite scrap (4 per netherite ingot), not the
 * diamonds of their base item. The primary material goes in the result slot; secondary materials are
 * handed to the player when the result is taken. No XP is awarded.
 *
 * <p>Uses NeoForge's GrindstoneEvent hooks (fired from GrindstoneMenu.createResult / the result
 * slot's onTake), so no mixin is needed. Materials are derived from the server's RecipeManager,
 * which the client doesn't have, so the result is computed on the server thread only and reaches
 * the client through the menu's normal slot sync (same as the vanilla crafting table result).
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class GrindstoneSalvageHandler {

    /** Netherite scrap returned per netherite ingot used in a smithing upgrade (full durability). */
    public static final int NETHERITE_SCRAP_PER_INGOT = 4;

    /** Materials per item at full durability, primary first. Empty list = not salvageable. */
    private static final Map<Item, List<ItemStack>> CACHE = new HashMap<>();
    /** Recipe manager the cache was built from; a /reload creates a new one and invalidates it. */
    private static WeakReference<RecipeManager> cacheOwner = new WeakReference<>(null);

    private GrindstoneSalvageHandler() {
    }

    @SubscribeEvent
    public static void onPlaceItem(GrindstoneEvent.OnPlaceItem event) {
        List<ItemStack> salvage = computeSalvage(event.getTopItem(), event.getBottomItem());
        if (salvage.isEmpty()) {
            return;
        }
        event.setOutput(salvage.get(0));
        event.setXp(0);
    }

    @SubscribeEvent
    public static void onTakeItem(GrindstoneEvent.OnTakeItem event) {
        Player player = event.getPlayer();
        if (player.level().isClientSide()) {
            return;
        }
        // The event carries the inputs as they were when the result was taken, so recomputing gives
        // exactly what the result slot showed.
        List<ItemStack> salvage = computeSalvage(event.getTopItem(), event.getBottomItem());
        if (salvage.isEmpty()) {
            return;
        }
        event.setXp(0);
        for (int i = 1; i < salvage.size(); i++) {
            player.getInventory().placeItemBackInInventory(salvage.get(i).copy());
        }
    }

    /** True for the item kinds the salvage feature covers (tools, melee weapons, armour pieces). */
    public static boolean isSalvageCandidate(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES)
                || stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.SPEARS)
                || stack.is(Items.MACE)
                || stack.is(ItemTags.HEAD_ARMOR)
                || stack.is(ItemTags.CHEST_ARMOR)
                || stack.is(ItemTags.LEG_ARMOR)
                || stack.is(ItemTags.FOOT_ARMOR);
    }

    /** True if the item carries any applied or stored enchantment that isn't a curse. */
    private static boolean hasNonCurseEnchantments(ItemStack stack) {
        for (net.minecraft.core.component.DataComponentType<net.minecraft.world.item.enchantment.ItemEnchantments> type
                : java.util.List.of(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                        net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS)) {
            net.minecraft.world.item.enchantment.ItemEnchantments enchantments =
                    stack.getOrDefault(type, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
            for (net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchantment : enchantments.keySet()) {
                if (!enchantment.is(net.minecraft.tags.EnchantmentTags.CURSE)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The single salvage input, or EMPTY when the grindstone isn't in a salvage configuration. */
    public static ItemStack salvageInput(ItemStack top, ItemStack bottom) {
        if (top.isEmpty() == bottom.isEmpty()) {
            return ItemStack.EMPTY; // both empty, or two items (vanilla repair/combine)
        }
        ItemStack input = top.isEmpty() ? bottom : top;
        if (input.getCount() != 1
                || hasNonCurseEnchantments(input) // curse-only items are salvaged too
                || !isSalvageCandidate(input)) {
            return ItemStack.EMPTY;
        }
        return input;
    }

    /** floor(count * remaining durability / max durability). */
    public static int scaleByWear(int count, ItemStack input) {
        if (!input.isDamageableItem()) {
            return count;
        }
        int max = input.getMaxDamage();
        if (max <= 0) {
            return count;
        }
        int remaining = Math.max(0, max - input.getDamageValue());
        return (int) ((long) count * remaining / max);
    }

    /**
     * Wear-scaled salvage for the given grindstone inputs: primary stack first, then secondaries.
     * Empty when not a salvage case, when not on the server thread, or when everything rounds to 0.
     */
    public static List<ItemStack> computeSalvage(ItemStack top, ItemStack bottom) {
        ItemStack input = salvageInput(top, bottom);
        if (input.isEmpty()) {
            return List.of();
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) {
            return List.of(); // client side: the server's result arrives via slot sync
        }
        List<ItemStack> full = materialsFor(server, input.getItem());
        List<ItemStack> scaled = new ArrayList<>(full.size());
        for (ItemStack material : full) {
            int count = scaleByWear(material.getCount(), input);
            if (count > 0) {
                scaled.add(material.copyWithCount(count));
            }
        }
        return scaled;
    }

    private static List<ItemStack> materialsFor(MinecraftServer server, Item item) {
        RecipeManager recipes = server.getRecipeManager();
        if (cacheOwner.get() != recipes) {
            CACHE.clear();
            cacheOwner = new WeakReference<>(recipes);
        }
        List<ItemStack> cached = CACHE.get(item);
        if (cached == null) {
            ContextMap context = SlotDisplayContext.fromLevel(server.overworld());
            cached = List.copyOf(computeMaterials(recipes, context, item, true));
            CACHE.put(item, cached);
        }
        return cached;
    }

    /**
     * Full-durability materials for {@code item}: its crafting recipe's ingredients (sticks / breeze
     * rod excluded, tag ingredients resolved to the tag's first entry), ordered largest count first.
     * Without a crafting recipe, a smithing-transform recipe is used: only the addition is returned
     * (netherite ingot becomes 4 netherite scrap); the base item's materials and the template are not.
     */
    private static List<ItemStack> computeMaterials(RecipeManager recipes, ContextMap context, Item item, boolean allowSmithing) {
        Recipe<?> crafting = findRecipe(recipes, context, item, false);
        if (crafting != null) {
            Map<Item, Integer> counts = new LinkedHashMap<>();
            for (Ingredient ingredient : crafting.placementInfo().ingredients()) {
                if (isHandle(ingredient)) {
                    continue;
                }
                Item material = defaultItem(ingredient);
                if (material != null) {
                    counts.merge(material, 1, Integer::sum);
                }
            }
            List<ItemStack> out = new ArrayList<>();
            counts.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())) // stable: ties keep recipe order
                    .forEach(e -> out.add(new ItemStack(e.getKey(), e.getValue())));
            return out;
        }

        if (!allowSmithing) {
            return List.of();
        }
        Recipe<?> smithing = findRecipe(recipes, context, item, true);
        if (!(smithing instanceof SmithingTransformRecipe transform)) {
            return List.of(); // no recipe at all (e.g. chainmail) -> nothing to salvage
        }
        // Upgraded items return only the upgrade material (netherite scrap), not the base item's materials.
        List<ItemStack> out = new ArrayList<>();
        transform.additionIngredient().ifPresent(addition -> {
            if (addition.test(new ItemStack(Items.NETHERITE_INGOT))) {
                out.add(new ItemStack(Items.NETHERITE_SCRAP, NETHERITE_SCRAP_PER_INGOT));
            } else {
                Item material = defaultItem(addition);
                if (material != null) {
                    out.add(new ItemStack(material));
                }
            }
        });
        return out;
    }

    /** Sticks (tool handles) and the mace's breeze rod (its handle) are never returned. */
    private static boolean isHandle(Ingredient ingredient) {
        return ingredient.test(new ItemStack(Items.STICK)) || ingredient.test(new ItemStack(Items.BREEZE_ROD));
    }

    /** First item of the ingredient (tag order), e.g. #planks -> oak planks, #stone_tool_materials -> cobblestone. */
    private static Item defaultItem(Ingredient ingredient) {
        Optional<Holder<Item>> first = ingredient.items().findFirst();
        return first.map(Holder::value).filter(i -> i != Items.AIR).orElse(null);
    }

    /**
     * Finds the shaped/shapeless crafting recipe (or smithing-transform recipe when {@code smithing})
     * producing {@code item}. Deterministic: a recipe whose id equals the item id wins, otherwise the
     * alphabetically first recipe id.
     */
    private static Recipe<?> findRecipe(RecipeManager recipes, ContextMap context, Item item, boolean smithing) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        RecipeHolder<?> best = null;
        for (RecipeHolder<?> holder : recipes.getRecipes()) {
            Recipe<?> recipe = holder.value();
            boolean kindMatches = smithing
                    ? recipe instanceof SmithingTransformRecipe
                    : (recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe);
            if (!kindMatches || !produces(recipe, context, item)) {
                continue;
            }
            Identifier id = holder.id().identifier();
            if (id.equals(itemId)) {
                return recipe;
            }
            if (best == null || id.toString().compareTo(best.id().identifier().toString()) < 0) {
                best = holder;
            }
        }
        return best == null ? null : best.value();
    }

    private static boolean produces(Recipe<?> recipe, ContextMap context, Item item) {
        try {
            for (RecipeDisplay display : recipe.display()) {
                if (display.result().resolveForFirstStack(context).is(item)) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            // a modded recipe with an odd display; just skip it
        }
        return false;
    }
}
