package com.terrainlab.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Drinkable experience bottle that grants two levels on consumption. The consumable behaviour and the
 * empty-bottle remainder are set on the item properties in {@code ModItems}.
 */
public class XpBottleItem extends Item {

    private static final int XP_LEVELS_GRANTED = 2;

    public XpBottleItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        // Base Consumable handling: sound, particles, stats and the stack decrement.
        ItemStack result = super.finishUsingItem(stack, level, entity);

        if (!level.isClientSide() && entity instanceof ServerPlayer serverPlayer) {
            serverPlayer.giveExperienceLevels(XP_LEVELS_GRANTED);
        }

        return result;
    }
}
