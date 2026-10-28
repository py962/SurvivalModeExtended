package com.terrainlab.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * Potion-cauldron mixing: which potions may be poured in, and how the poured potions combine
 * into one "Custom Potion".
 * <p>
 * Combining rule per effect: the highest amplifier wins and the durations of every instance add
 * up (capped at {@link #MAX_COMBINED_DURATION}); instant effects keep the highest amplifier only,
 * and an infinite instance keeps the effect infinite.
 */
public final class PotionMixing {

    public static final int MAX_POTIONS = 3;
    /** 8 minutes. */
    public static final int MAX_COMBINED_DURATION = 9600;
    /**
     * PotionContents custom name: vanilla names a potion "item.minecraft.potion.effect.&lt;name&gt;",
     * so the en_us key is item.minecraft.potion.effect.terrainlab_custom ("Custom Potion").
     */
    public static final String CUSTOM_POTION_NAME = "terrainlab_custom";

    private PotionMixing() {
    }

    /** Drinkable potions with at least one effect can be poured (water, awkward, mundane... cannot). */
    public static boolean canPour(ItemStack stack) {
        if (!stack.is(Items.POTION)) {
            return false;
        }
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && contents.hasEffects();
    }

    public static List<MobEffectInstance> combineEffects(List<PotionContents> potions) {
        Map<Holder<MobEffect>, int[]> combined = new LinkedHashMap<>(); // {amplifier, duration}
        for (PotionContents potion : potions) {
            for (MobEffectInstance effect : potion.getAllEffects()) {
                Holder<MobEffect> type = effect.getEffect();
                boolean instant = type.value().isInstantenous();
                int duration = effect.getDuration();
                int[] entry = combined.get(type);
                if (entry == null) {
                    combined.put(type, new int[]{effect.getAmplifier(), instant ? Math.max(1, duration) : duration});
                    continue;
                }
                entry[0] = Math.max(entry[0], effect.getAmplifier());
                if (instant) {
                    entry[1] = Math.max(entry[1], duration);
                } else if (entry[1] == MobEffectInstance.INFINITE_DURATION || duration == MobEffectInstance.INFINITE_DURATION) {
                    entry[1] = MobEffectInstance.INFINITE_DURATION;
                } else {
                    entry[1] = entry[1] + duration;
                }
            }
        }
        List<MobEffectInstance> result = new ArrayList<>(combined.size());
        for (Map.Entry<Holder<MobEffect>, int[]> e : combined.entrySet()) {
            int duration = e.getValue()[1];
            if (duration != MobEffectInstance.INFINITE_DURATION && !e.getKey().value().isInstantenous()) {
                duration = Math.min(duration, MAX_COMBINED_DURATION);
            }
            result.add(new MobEffectInstance(e.getKey(), duration, e.getValue()[0]));
        }
        return result;
    }

    /** Opaque ARGB average of the poured potions' colours. */
    public static int blendColor(List<PotionContents> potions) {
        if (potions.isEmpty()) {
            return 0xFF000000 | PotionContents.BASE_POTION_COLOR;
        }
        int r = 0;
        int g = 0;
        int b = 0;
        for (PotionContents potion : potions) {
            int c = potion.getColor();
            r += (c >> 16) & 0xFF;
            g += (c >> 8) & 0xFF;
            b += c & 0xFF;
        }
        int n = potions.size();
        return 0xFF000000 | ((r / n) << 16) | ((g / n) << 8) | (b / n);
    }

    public static ItemStack createCustomPotion(List<PotionContents> potions) {
        ItemStack stack = new ItemStack(Items.POTION);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(
                Optional.empty(),
                Optional.of(blendColor(potions)),
                combineEffects(potions),
                Optional.of(CUSTOM_POTION_NAME)));
        return stack;
    }
}
