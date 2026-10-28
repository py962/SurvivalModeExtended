package com.terrainlab.mixin;

import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@code DispenserBlock#getDispenseMethod} (behaviour registry plus the equipment /
 * spawn-egg fallbacks) to the Minecart with Dispenser.
 */
@Mixin(DispenserBlock.class)
public interface DispenserBlockInvoker {

    @Invoker("getDispenseMethod")
    DispenseItemBehavior terrainlab$getDispenseMethod(Level level, ItemStack itemStack);
}
