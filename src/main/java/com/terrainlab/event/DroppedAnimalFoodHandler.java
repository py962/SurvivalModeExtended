
        package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SolidBucketItem;
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

        if (stack.isEmpty() || !couldBeAnimalFood(stack)) {
            return;
        }

        Animal animal = level.getEntitiesOfClass(
                        Animal.class,
                        droppedItem.getBoundingBox().inflate(FEEDING_RANGE),
                        candidate ->
                                candidate.isAlive()
                                        && !candidate.isBaby()
                                        && candidate.getAge() == 0
                                        && candidate.canFallInLove()
                                        && !candidate.isInLove()
                                        && canBreedFromDroppedFood(candidate)
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

    /*
     * Cheap precheck before the area search: every vanilla breeding
     * food is defined by an item tag named "*_food". Buckets (e.g.
     * axolotl / nautilus bucket food) are never accepted as dropped food.
     */
    private static boolean couldBeAnimalFood(ItemStack stack) {
        if (stack.getItem() instanceof BucketItem
                || stack.getItem() instanceof SolidBucketItem) {
            return false;
        }

        return stack.getItemHolder()
                .tags()
                .anyMatch(tag -> tag.location().getPath().endsWith("_food"));
    }

    /*
     * Excludes animals that cannot breed from a plain dropped item under
     * vanilla rules: horses and similar need the normal interaction,
     * untamed tamables must be tamed first, and axolotls eat bucketed fish.
     */
    private static boolean canBreedFromDroppedFood(Animal animal) {
        if (animal instanceof AbstractHorse || animal instanceof Axolotl) {
            return false;
        }

        return !(animal instanceof TamableAnimal tamable) || tamable.isTame();
    }
}

