package com.terrainlab.mixin;

import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes AnvilMenu's private {@code cost} DataSlot so AnvilIronColumnDiscountMixin can adjust the
 * charged XP-level cost after vanilla computes it.
 */
@Mixin(AnvilMenu.class)
public interface AnvilMenuCostAccessor {

    @Accessor("cost")
    DataSlot terrainlab$getCostSlot();
}
