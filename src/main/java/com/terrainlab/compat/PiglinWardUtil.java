package com.terrainlab.compat;

import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.core.component.DataComponents;

/**
 * Shared Awkward Potion check and zombification ward, used by the feed, splash,
 * lingering and arrow delivery paths.
 * <p>
 * Matches potion identity rather than effects, since the Awkward Potion has none.
 * The ward is permanent; vanilla resets timeInOverworld once the piglin is immune.
 */
public final class PiglinWardUtil {

    private PiglinWardUtil() {
    }

    public static boolean isAwkwardPotion(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && contents.is(Potions.AWKWARD);
    }

    public static void applyWard(AbstractPiglin piglin) {
        piglin.setImmuneToZombification(true);
    }

    /** Wards a piglin and awards "piglin_cure" if the cause is a player. */
    public static void applyWard(AbstractPiglin piglin, net.minecraft.world.entity.Entity cause) {
        applyWard(piglin);
        if (cause instanceof net.minecraft.server.level.ServerPlayer player) {
            com.terrainlab.advancement.ModAdvancements.award(player, "piglin_cure");
        }
    }

    public static boolean isAwkwardPotion(PotionContents contents) {
        return contents != null && contents.is(Potions.AWKWARD);
    }
}
