package com.terrainlab.mixin;

import com.terrainlab.celestial.LuckLootBonus;
import net.minecraft.world.level.storage.loot.LootPool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Adds the Luck-effect bonus roll (+1) to the computed roll count of each pool of the outermost
 * qualifying loot table (see {@link LuckLootBonus}). Targets the first int local of addRandomItems
 * (rolls + floor(bonus_rolls * luck)) as it is stored; the loop counter is the second int local.
 */
@Mixin(LootPool.class)
public abstract class LootPoolLuckBonusMixin {

    @ModifyVariable(method = "addRandomItems", at = @At("STORE"), ordinal = 0, require = 1)
    private int terrainlab$luckBonusRoll(int count) {
        return count + LuckLootBonus.extraRolls();
    }
}
