package com.terrainlab.food;

import com.terrainlab.registry.ModDataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The mod's flower stews, one entry per flower, matching the result components of
 * data/minecraft/recipe/suspicious_stew_from_&lt;flower&gt;.json exactly (effects with durations in ticks,
 * item_name key, stew_flower id). Used so stews from other sources (brown mooshrooms, anything calling
 * FlowerBlock.getSuspiciousEffects) are identical to the crafted ones. Keep in sync with the recipes.
 *
 * <p>Torchflower is deliberately absent: its crafted recipe is disabled, so there is no torchflower stew.
 */
public final class FlowerStews {

    public record Stew(String flower, String nameKey, SuspiciousStewEffects effects) {

        /** Writes the crafted result's components onto a suspicious stew stack. */
        public void applyTo(ItemStack stack) {
            stack.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, effects);
            stack.set(DataComponents.ITEM_NAME, Component.translatable(nameKey));
            stack.set(ModDataComponents.STEW_FLOWER.get(), flower);
        }
    }

    private static volatile Map<Item, Stew> table;

    private FlowerStews() {
    }

    private static Map<Item, Stew> table() {
        Map<Item, Stew> t = table;
        if (t == null) {
            t = new HashMap<>();
            put(t, Items.ALLIUM, "allium", "fire_resistance", MobEffects.FIRE_RESISTANCE, 60);
            put(t, Items.AZURE_BLUET, "azure_bluet", "blindness", MobEffects.BLINDNESS, 220);
            put(t, Items.BLUE_ORCHID, "blue_orchid", "energy", null, 0);
            put(t, Items.CLOSED_EYEBLOSSOM, "closed_eyeblossom", "nausea", MobEffects.NAUSEA, 140);
            put(t, Items.CORNFLOWER, "cornflower", "jump_boost", MobEffects.JUMP_BOOST, 100);
            put(t, Items.DANDELION, "dandelion", "resistance", MobEffects.RESISTANCE, 160);
            put(t, Items.LILY_OF_THE_VALLEY, "lily_of_the_valley", "poison", MobEffects.POISON, 220);
            put(t, Items.OPEN_EYEBLOSSOM, "open_eyeblossom", "blindness", MobEffects.BLINDNESS, 220);
            put(t, Items.ORANGE_TULIP, "orange_tulip", "weakness", MobEffects.WEAKNESS, 140);
            put(t, Items.OXEYE_DAISY, "oxeye_daisy", "healing", null, 0);
            put(t, Items.PINK_TULIP, "pink_tulip", "weakness", MobEffects.WEAKNESS, 140);
            put(t, Items.POPPY, "poppy", "night_vision", MobEffects.NIGHT_VISION, 100);
            put(t, Items.RED_TULIP, "red_tulip", "weakness", MobEffects.WEAKNESS, 140);
            put(t, Items.WHITE_TULIP, "white_tulip", "weakness", MobEffects.WEAKNESS, 140);
            put(t, Items.WITHER_ROSE, "wither_rose", "wither", MobEffects.WITHER, 140);
            table = t;
        }
        return t;
    }

    private static void put(Map<Item, Stew> t, Item item, String flower, String name,
                            @Nullable Holder<MobEffect> effect, int duration) {
        SuspiciousStewEffects effects = effect == null ? SuspiciousStewEffects.EMPTY
                : new SuspiciousStewEffects(List.of(new SuspiciousStewEffects.Entry(effect, duration)));
        t.put(item, new Stew(flower, "item.terrainlab.suspicious_stew." + name, effects));
    }

    /** The crafted stew for a flower item, or null if the item has none. */
    public static @Nullable Stew forItem(Item item) {
        return table().get(item);
    }

    /** The crafted stew with this stew_flower id, or null. */
    public static @Nullable Stew forFlower(@Nullable String flower) {
        if (flower == null) return null;
        for (Stew s : table().values()) {
            if (s.flower().equals(flower)) return s;
        }
        return null;
    }

    /**
     * The name key shared by every table entry whose effects equal these, or null if none match or the
     * matches disagree (e.g. empty effects: Blue Orchid "energy" vs Oxeye Daisy "healing").
     */
    public static @Nullable String nameKeyForEffects(SuspiciousStewEffects effects) {
        String key = null;
        for (Stew s : table().values()) {
            if (!s.effects().equals(effects)) continue;
            if (key == null) key = s.nameKey();
            else if (!key.equals(s.nameKey())) return null;
        }
        return key;
    }

    /** Torchflower has no stew (crafted recipe disabled), so brown mooshrooms refuse it. */
    public static boolean isRefusedByMooshroom(ItemStack stack) {
        return stack.is(Items.TORCHFLOWER);
    }
}
