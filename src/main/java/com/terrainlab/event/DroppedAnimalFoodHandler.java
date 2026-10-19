
        package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Comparator;

@EventBusSubscriber(modid = TerrainLab.MODID)
public final class DroppedAnimalFoodHandler {

    private static final int CHECK_INTERVAL_TICKS = 10;
    private static final double FEEDING_RANGE = 3.0D;

    private DroppedAnimalFoodHandler() {
    }

    @SubscribeEvent
    public static void onItemTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ItemEntity droppedItem)) {
            return;
        }

        if (!(droppedItem.level() instanceof ServerLevel level)) {
            return;
        }

        if (droppedItem.isRemoved()
                || droppedItem.tickCount % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        ItemStack stack = droppedItem.getItem();

        if (stack.isEmpty()) {
            return;
        }

        Animal animal = level.getEntitiesOfClass(
                        Animal.class,
                        droppedItem.getBoundingBox().inflate(FEEDING_RANGE),
                        candidate ->
                                candidate.isAlive()
                                        && !candidate.isBaby()
                                        && candidate.getAge() == 0
                                        && !candidate.isInLove()
                                        && candidate.isFood(stack)
                ).stream()
                .min(Comparator.comparingDouble(
                        candidate -> candidate.distanceToSqr(droppedItem)
                ))
                .orElse(null);

        if (animal == null) {
            return;
        }

        // Consume one item and put the animal into love mode.
        stack.shrink(1);
        animal.setInLove(null);

        if (stack.isEmpty()) {
            droppedItem.discard();
        } else {
            droppedItem.setItem(stack);
        }
    }
}

