package com.terrainlab.mixin;

import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes ItemCombinerMenu's {@code access} field for AnvilIronColumnDiscountMixin. Targets the declaring
 * superclass, since a shadow on AnvilMenu does not resolve inherited fields; AnvilMenu instances can be cast
 * to this interface.
 */
@Mixin(ItemCombinerMenu.class)
public interface ItemCombinerMenuAccessor {

    @Accessor("access")
    ContainerLevelAccess terrainlab$getAccess();
}
