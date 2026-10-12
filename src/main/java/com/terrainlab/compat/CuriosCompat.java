package com.terrainlab.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Thin wrapper over the Curios inventory API for checking a slot's contents.
 */
public final class CuriosCompat {

    private CuriosCompat() {
    }

    public static boolean isWorn(LivingEntity entity, String slotId, Item item) {
        return CuriosApi.getCuriosInventory(entity)
                .flatMap(inv -> inv.getStacksHandler(slotId))
                .map(handler -> {
                    var stacks = handler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        if (stacks.getStackInSlot(i).is(item)) return true;
                    }
                    return false;
                })
                .orElse(false);
    }

    /** Returns the first non-empty stack in the given Curios slot that matches the predicate. */
    public static Optional<ItemStack> findStack(LivingEntity entity, String slotId, Predicate<ItemStack> predicate) {
        return CuriosApi.getCuriosInventory(entity)
                .flatMap(inv -> inv.getStacksHandler(slotId))
                .flatMap(handler -> {
                    var stacks = handler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        ItemStack stack = stacks.getStackInSlot(i);
                        if (!stack.isEmpty() && predicate.test(stack)) {
                            return Optional.of(stack);
                        }
                    }
                    return Optional.empty();
                });
    }
}
