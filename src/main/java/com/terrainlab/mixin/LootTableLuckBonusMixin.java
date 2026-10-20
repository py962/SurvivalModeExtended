package com.terrainlab.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.terrainlab.celestial.LuckLootBonus;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;

import java.util.function.Consumer;

/**
 * Tracks loot-table nesting depth and, for the outermost table only, whether the Luck-effect bonus
 * roll applies (see {@link LuckLootBonus}). getRandomItemsRaw(LootContext, Consumer) is the single
 * funnel for fill(), getRandomItems(...) and nested loot_table entries.
 */
@Mixin(LootTable.class)
public abstract class LootTableLuckBonusMixin {

    @WrapMethod(method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V", require = 1)
    private void terrainlab$luckBonusScope(LootContext context, Consumer<ItemStack> output, Operation<Void> original) {
        boolean previous = LuckLootBonus.enter((LootTable) (Object) this, context);
        try {
            original.call(context, output);
        } finally {
            LuckLootBonus.exit(previous);
        }
    }
}
