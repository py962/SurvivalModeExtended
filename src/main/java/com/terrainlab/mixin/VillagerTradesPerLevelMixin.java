package com.terrainlab.mixin;

import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Villagers unlock 4 trades per profession level instead of 2. The literal 2 passed to
 * {@code addOffersFromItemListings} is the only int constant in {@code updateTrades}.
 */
@Mixin(Villager.class)
public class VillagerTradesPerLevelMixin {

    @ModifyConstant(method = "updateTrades", constant = @Constant(intValue = 2))
    private int terrainlab$fourTradesPerLevel(int original) {
        return 4;
    }
}
