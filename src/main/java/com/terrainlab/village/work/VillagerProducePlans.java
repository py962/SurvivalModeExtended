package com.terrainlab.village.work;

import com.terrainlab.village.VillagerWorkConstants;
import com.terrainlab.village.VillagerWorkState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;

import java.util.ArrayList;
import java.util.List;

/** The {@link DepositProduceBehavior.ProducePlan}s for farmer, butcher and fisherman. */
public final class VillagerProducePlans {

    private VillagerProducePlans() {
    }

    private static boolean intervalDue(VillagerWorkState state, long now) {
        return VillagerWorkState.elapsed(state.lastDeposit(), now, VillagerWorkConstants.DEPOSIT_INTERVAL_TICKS);
    }

    private static boolean filling(SimpleContainer inv) {
        return VillagerWork.freeSlots(inv) <= VillagerWorkConstants.DEPOSIT_WHEN_FREE_SLOTS_AT_MOST;
    }

    private static void addStacks(List<ItemStack> out, Item item, int count) {
        while (count > 0) {
            int n = Math.min(count, item.getDefaultMaxStackSize());
            out.add(new ItemStack(item, n));
            count -= n;
        }
    }

    // ---------------------------------------------------------------- farmer

    /**
     * Farmer: all wheat (half of it baked 3:1 into bread), potatoes and carrots above the replanting
     * reserve, beetroot in sixes as beetroot soup, and all pumpkins, melon slices and sugar cane. Seeds
     * are never delivered.
     */
    public static final DepositProduceBehavior.ProducePlan FARMER = new DepositProduceBehavior.ProducePlan() {
        @Override
        public boolean wantsTrip(Villager body, VillagerWorkState state, long now) {
            int amount = farmerDeliverable(body.getInventory());
            // "Filling" needs a worthwhile load, or a farmer with many crop types would shuttle constantly.
            return amount > 0 && (intervalDue(state, now)
                    || (filling(body.getInventory()) && amount >= VillagerWorkConstants.FILLING_MIN_ITEMS));
        }

        @Override
        public List<ItemStack> extract(ServerLevel level, Villager body) {
            SimpleContainer inv = body.getInventory();
            List<ItemStack> out = new ArrayList<>();

            int wheat = VillagerWork.remove(inv, s -> s.is(Items.WHEAT), Integer.MAX_VALUE);
            int bread = (wheat / 2) / VillagerWorkConstants.WHEAT_PER_BREAD;
            addStacks(out, Items.BREAD, bread);
            addStacks(out, Items.WHEAT, wheat - bread * VillagerWorkConstants.WHEAT_PER_BREAD);

            for (Item crop : new Item[]{Items.POTATO, Items.CARROT}) {
                int excess = inv.countItem(crop) - VillagerWorkConstants.SEED_CROP_RESERVE;
                if (excess > 0) {
                    addStacks(out, crop, VillagerWork.remove(inv, s -> s.is(crop), excess));
                }
            }

            int soups = inv.countItem(Items.BEETROOT) / VillagerWorkConstants.BEETROOT_PER_SOUP;
            if (soups > 0) {
                VillagerWork.remove(inv, s -> s.is(Items.BEETROOT), soups * VillagerWorkConstants.BEETROOT_PER_SOUP);
                addStacks(out, Items.BEETROOT_SOUP, soups);
            }

            for (Item item : new Item[]{Items.PUMPKIN, Items.MELON_SLICE, Items.SUGAR_CANE}) {
                addStacks(out, item, VillagerWork.remove(inv, s -> s.is(item), Integer.MAX_VALUE));
            }
            return out;
        }
    };

    static int farmerDeliverable(SimpleContainer inv) {
        int total = inv.countItem(Items.WHEAT)
                + Math.max(0, inv.countItem(Items.POTATO) - VillagerWorkConstants.SEED_CROP_RESERVE)
                + Math.max(0, inv.countItem(Items.CARROT) - VillagerWorkConstants.SEED_CROP_RESERVE)
                + inv.countItem(Items.PUMPKIN) + inv.countItem(Items.MELON_SLICE) + inv.countItem(Items.SUGAR_CANE);
        if (inv.countItem(Items.BEETROOT) >= VillagerWorkConstants.BEETROOT_PER_SOUP) {
            total += VillagerWorkConstants.BEETROOT_PER_SOUP;
        }
        return total;
    }

    // ---------------------------------------------------------------- butcher

    /** Butcher: everything it is not keeping as food or breeding feed, i.e. the slaughter drops. Delivered at once. */
    public static final DepositProduceBehavior.ProducePlan BUTCHER = new DepositProduceBehavior.ProducePlan() {
        @Override
        public boolean wantsTrip(Villager body, VillagerWorkState state, long now) {
            return VillagerWork.count(body.getInventory(), VillagerProducePlans::isButcherProduce) > 0;
        }

        @Override
        public List<ItemStack> extract(ServerLevel level, Villager body) {
            SimpleContainer inv = body.getInventory();
            List<ItemStack> out = new ArrayList<>();
            for (int slot = 0; slot < inv.getContainerSize(); slot++) {
                ItemStack stack = inv.getItem(slot);
                if (!stack.isEmpty() && isButcherProduce(stack)) {
                    out.add(stack.copy());
                    inv.setItem(slot, ItemStack.EMPTY);
                }
            }
            return out;
        }
    };

    static boolean isButcherProduce(ItemStack stack) {
        return !stack.has(DataComponents.VILLAGER_FOOD) && !ButcherHusbandryBehavior.isAnyBreedingFood(stack);
    }

    /** The smoker result for raw food (raw beef → steak, venison → cooked venison if a recipe exists), else the input. */
    public static ItemStack smoked(ServerLevel level, ItemStack raw) {
        SingleRecipeInput input = new SingleRecipeInput(raw.copyWithCount(1));
        return level.recipeAccess().getRecipeFor(RecipeType.SMOKING, input, level)
                .map(holder -> {
                    ItemStack result = holder.value().assemble(input);
                    return result.copyWithCount(Math.min(result.getMaxStackSize(), result.getCount() * raw.getCount()));
                })
                .orElse(raw);
    }

    // ---------------------------------------------------------------- fisherman

    static boolean isCatch(ItemStack stack) {
        return stack.is(Items.COD) || stack.is(Items.SALMON) || stack.is(Items.DRIED_KELP);
    }

    static int catchCount(Villager body) {
        return VillagerWork.count(body.getInventory(), VillagerProducePlans::isCatch);
    }

    private static List<ItemStack> extractCatch(Villager body) {
        SimpleContainer inv = body.getInventory();
        List<ItemStack> out = new ArrayList<>();
        addStacks(out, Items.COOKED_COD, VillagerWork.remove(inv, s -> s.is(Items.COD), Integer.MAX_VALUE));
        addStacks(out, Items.COOKED_SALMON, VillagerWork.remove(inv, s -> s.is(Items.SALMON), Integer.MAX_VALUE));
        addStacks(out, Items.DRIED_KELP, VillagerWork.remove(inv, s -> s.is(Items.DRIED_KELP), Integer.MAX_VALUE));
        return out;
    }

    /** Fisherman during work: deliver once {@link VillagerWorkConstants#FISH_DELIVER_AT} items are carried (fish cooked). */
    public static final DepositProduceBehavior.ProducePlan FISHERMAN_WORK = new DepositProduceBehavior.ProducePlan() {
        @Override
        public boolean wantsTrip(Villager body, VillagerWorkState state, long now) {
            return catchCount(body) >= VillagerWorkConstants.FISH_DELIVER_AT;
        }

        @Override
        public List<ItemStack> extract(ServerLevel level, Villager body) {
            return extractCatch(body);
        }
    };

    /** Fisherman after work (MEET at the bell, next to the chest): deliver whatever is left. */
    public static final DepositProduceBehavior.ProducePlan FISHERMAN_END_OF_WORK = new DepositProduceBehavior.ProducePlan() {
        @Override
        public boolean wantsTrip(Villager body, VillagerWorkState state, long now) {
            return catchCount(body) > 0;
        }

        @Override
        public List<ItemStack> extract(ServerLevel level, Villager body) {
            return extractCatch(body);
        }
    };
}
