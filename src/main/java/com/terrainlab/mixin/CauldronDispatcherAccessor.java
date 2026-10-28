package com.terrainlab.mixin;

import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Reads a cauldron dispatcher's item map without building an ItemStack (not allowed during registration). */
@Mixin(CauldronInteraction.Dispatcher.class)
public interface CauldronDispatcherAccessor {
    @Accessor("items")
    Map<Item, CauldronInteraction> terrainlab$items();
}
