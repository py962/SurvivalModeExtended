package com.terrainlab.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

/**
 * Compass that also shows distance to its target (RangefinderDistance model property). Lodestone linking
 * follows vanilla rules but produces a linked rangefinder rather than a plain compass, and a linked
 * rangefinder keeps its own name.
 */
public class RangefinderCompassItem extends CompassItem {

    public RangefinderCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (!level.getBlockState(pos).is(Blocks.LODESTONE)) {
            return super.useOn(context);
        }

        level.playSound(null, pos, SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        LodestoneTracker tracker = new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), pos)), true);

        boolean linkInPlace = player == null || (!player.hasInfiniteMaterials() && stack.getCount() == 1);
        if (linkInPlace) {
            stack.set(DataComponents.LODESTONE_TRACKER, tracker);
        } else {
            ItemStack linked = stack.transmuteCopy(this, 1);
            stack.consume(1, player);
            linked.set(DataComponents.LODESTONE_TRACKER, tracker);
            if (!player.getInventory().add(linked)) {
                player.drop(linked, false, net.minecraft.util.Prediction.PREDICTED);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        return stack.getComponents().getOrDefault(DataComponents.ITEM_NAME, CommonComponents.EMPTY);
    }
}
