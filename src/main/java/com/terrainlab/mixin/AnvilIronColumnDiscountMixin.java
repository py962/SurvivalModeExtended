package com.terrainlab.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Halves the prior-work penalty charged by an anvil standing on a column of at least four iron
 * blocks. Only the cost of the current operation is reduced; the item's stored repair cost still
 * grows normally. The penalty is recomputed from both inputs' {@code REPAIR_COST} and half of it
 * is subtracted as vanilla sets the cost, before its "Too Expensive!" check at 40 levels.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilIronColumnDiscountMixin {

    /** Iron blocks required directly beneath the anvil. */
    private static final int IRON_COLUMN_HEIGHT = 4;

    // Ordinal 4 is the main cost assignment in createResultInternal (after the 1 at the start
    // and the three early-exit zeroes).
    @ModifyArg(
            method = "createResultInternal",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/DataSlot;set(I)V", ordinal = 4)
    )
    private int terrainlab$halvePriorWorkPenaltyOnIronColumn(int currentCost) {
        AnvilMenu self = (AnvilMenu) (Object) this;

        ItemStack left = self.getSlot(0).getItem();
        if (left.isEmpty() || currentCost <= 0) {
            return currentCost;
        }

        ItemStack right = self.getSlot(1).getItem();
        int priorWork = left.getOrDefault(DataComponents.REPAIR_COST, 0)
                + (right.isEmpty() ? 0 : right.getOrDefault(DataComponents.REPAIR_COST, 0));
        if (priorWork <= 0) {
            return currentCost;
        }

        ContainerLevelAccess access = ((ItemCombinerMenuAccessor) self).terrainlab$getAccess();
        if (!isOnIronColumn(access)) {
            return currentCost;
        }

        int discount = priorWork - priorWork / 2;
        return Math.max(currentCost - discount, 1); // vanilla's minimum of 1 level
    }

    /** True when the {@link #IRON_COLUMN_HEIGHT} blocks below the anvil are all iron blocks. */
    private static boolean isOnIronColumn(ContainerLevelAccess access) {
        return access.evaluate((level, pos) -> {
            for (int i = 1; i <= IRON_COLUMN_HEIGHT; i++) {
                if (!level.getBlockState(pos.below(i)).is(Blocks.IRON_BLOCK)) {
                    return false;
                }
            }
            return true;
        }, false);
    }
}
