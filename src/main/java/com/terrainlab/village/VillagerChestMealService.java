package com.terrainlab.village;

import com.terrainlab.food.FoodCategory;
import com.terrainlab.food.FoodCategoryTable;
import com.terrainlab.registry.ModAttachments;
import com.terrainlab.temperature.FoodTemperatureEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Villagers eat from the village chest ({@link VillageChestService}). On the staggered read, a
 * villager standing within {@link #BELL_REACH} blocks of its bell (MEETING_POINT memory) that is
 * injured, cold, hot or hungry takes ONE item, shows a short eating animation (holds it, eating
 * sounds and crumbs), and gets the food's player effects at double strength:
 * <ul>
 *   <li>hunger points x2 added to vanilla {@code foodLevel} (which vanilla also spends on breeding);</li>
 *   <li>HEALTH food heals x2 over the same duration ({@link VillagerHealthService#queueHeal});</li>
 *   <li>COOL/WARMTH food pushes x2 as a timed, linearly decaying offset on the villager's temperature
 *       ({@link VillagerFoodTempOffset}, read in {@link VillagerThermalService}).</li>
 * </ul>
 * Then {@link #MEAL_COOLDOWN_TICKS} before it eats again. Choice: injured -> HEALTH food (apple,
 * carrot, dried kelp, golden carrot last), else any food; cold -> WARMTH food; hot -> COOL food;
 * hungry -> the food with the most hunger points. The first need with a matching item wins.
 * Bowls and other use-remainders go back into the chest (dropped if it is full).
 */
public final class VillagerChestMealService {

    public static final long MEAL_COOLDOWN_TICKS = 1200L;
    public static final double BELL_REACH = 4.0D;
    private static final double BELL_REACH_VERTICAL = 3.0D;

    /** Food temperature offsets last this long, decaying linearly to 0. */
    public static final long FOOD_TEMP_OFFSET_TICKS = 12000L;
    /** Stacked food offsets are capped at this many single (already doubled) pushes. */
    private static final double FOOD_TEMP_OFFSET_MAX_STACK = 2.0;

    private static final int VILLAGER_EFFECT_MULTIPLIER = 2;

    private static final int EAT_TICKS = 32;
    private static final int EAT_SOUND_INTERVAL = 4;

    /** Foods villagers never take: harmful, teleporting, or too precious. */
    private static final Set<Item> NEVER_EAT = Set.of(
            Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.PUFFERFISH, Items.POISONOUS_POTATO,
            Items.CHORUS_FRUIT, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.SUSPICIOUS_STEW);

    /** Running eating animations (cosmetic only; effects are applied when the item is taken). */
    private static final List<Meal> MEALS = new ArrayList<>();

    private record Meal(Villager villager, ItemStack food, long endTick) {
    }

    private VillagerChestMealService() {
    }

    // ---------------------------------------------------------------- staggered read

    public static void update(Villager villager, ServerLevel level, long gameTime) {
        finishStaleMeal(villager, gameTime);

        if (villager.isSleeping() || villager.isTrading() || !villager.isAlive()) {
            return;
        }
        if (villager.getData(ModAttachments.VILLAGER_EATING_UNTIL.get()) > 0L) {
            return;
        }
        long last = villager.getData(ModAttachments.VILLAGER_LAST_CHEST_MEAL_TICK.get());
        if (last >= 0L && gameTime - last < MEAL_COOLDOWN_TICKS) {
            return;
        }

        int flags = villager.getData(ModAttachments.VILLAGER_MOOD_FLAGS.get());
        int needs = flags & (VillagerMoodService.INJURED | VillagerMoodService.COLD
                | VillagerMoodService.HOT | VillagerMoodService.HUNGRY);
        if (needs == 0) {
            return;
        }

        Optional<BlockPos> bell = bellInReach(villager, level);
        if (bell.isEmpty()) {
            return;
        }

        ItemStack food = takeFood(level, bell.get(), needs);
        if (food.isEmpty()) {
            return;
        }

        applyEffects(villager, level, food, gameTime);
        returnRemainder(villager, level, bell.get(), food);
        startEating(villager, level, food, gameTime);
        villager.setData(ModAttachments.VILLAGER_LAST_CHEST_MEAL_TICK.get(), gameTime);
    }

    private static Optional<BlockPos> bellInReach(Villager villager, ServerLevel level) {
        Optional<GlobalPos> meeting = villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT);
        if (meeting.isEmpty() || !meeting.get().dimension().equals(level.dimension())) {
            return Optional.empty();
        }
        BlockPos bell = meeting.get().pos();
        double dx = villager.getX() - (bell.getX() + 0.5D);
        double dz = villager.getZ() - (bell.getZ() + 0.5D);
        double dy = villager.getY() - bell.getY();
        if (dx * dx + dz * dz > BELL_REACH * BELL_REACH || Math.abs(dy) > BELL_REACH_VERTICAL) {
            return Optional.empty();
        }
        return Optional.of(bell);
    }

    // ---------------------------------------------------------------- choice

    private static ItemStack takeFood(ServerLevel level, BlockPos bell, int needs) {
        if ((needs & VillagerMoodService.INJURED) != 0) {
            ItemStack food = takeBest(level, bell, VillagerChestMealService::healthScore);
            if (food.isEmpty()) {
                food = takeBest(level, bell, VillagerChestMealService::anyFoodScore);
            }
            if (!food.isEmpty()) {
                return food;
            }
        }
        if ((needs & VillagerMoodService.COLD) != 0) {
            ItemStack food = takeBest(level, bell, s -> categoryScore(s, FoodCategory.WARMTH));
            if (!food.isEmpty()) {
                return food;
            }
        }
        if ((needs & VillagerMoodService.HOT) != 0) {
            ItemStack food = takeBest(level, bell, s -> categoryScore(s, FoodCategory.COOL));
            if (!food.isEmpty()) {
                return food;
            }
        }
        if ((needs & VillagerMoodService.HUNGRY) != 0) {
            return takeBest(level, bell, VillagerChestMealService::hungerPoints);
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack takeBest(ServerLevel level, BlockPos bell, java.util.function.ToIntFunction<ItemStack> score) {
        Optional<ItemStack> best = VillageChestService.findBest(level, bell, score);
        if (best.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack template = best.get();
        return VillageChestService.take(level, bell, s -> ItemStack.isSameItemSameComponents(s, template), 1);
    }

    /** HEALTH foods by preference: apple, carrot, dried kelp, then golden carrot (precious). */
    private static int healthScore(ItemStack stack) {
        Item item = stack.getItem();
        if (FoodCategoryTable.getCategory(item) != FoodCategory.HEALTH || FoodCategoryTable.getHealthData(item) == null) {
            return 0;
        }
        if (item == Items.APPLE) return 4;
        if (item == Items.CARROT) return 3;
        if (item == Items.DRIED_KELP) return 2;
        return 1;
    }

    private static int categoryScore(ItemStack stack, FoodCategory category) {
        return isEdible(stack) && FoodCategoryTable.getCategory(stack.getItem()) == category ? 1 : 0;
    }

    /** Any edible food, preferring more hunger points. */
    private static int anyFoodScore(ItemStack stack) {
        return isEdible(stack) ? 1 + hungerPoints(stack) : 0;
    }

    /**
     * Hunger points as for players: the mod's HUNGER tiers from {@link FoodCategoryTable}; HEALTH,
     * COOL, WARMTH and ENERGY foods give none; foods outside the table use vanilla nutrition.
     */
    private static int hungerPoints(ItemStack stack) {
        if (!isEdible(stack)) {
            return 0;
        }
        Item item = stack.getItem();
        FoodCategory category = FoodCategoryTable.getCategory(item);
        if (category == null) {
            FoodProperties food = stack.get(DataComponents.FOOD);
            return food == null ? 0 : food.nutrition();
        }
        Integer points = FoodCategoryTable.getHungerPoints(item);
        return points == null ? 0 : points;
    }

    private static boolean isEdible(ItemStack stack) {
        if (stack.isEmpty() || NEVER_EAT.contains(stack.getItem())) {
            return false;
        }
        FoodCategory category = FoodCategoryTable.getCategory(stack.getItem());
        if (category == FoodCategory.ENERGY) {
            return false; // energy is a player-only effect
        }
        return category != null || stack.has(DataComponents.FOOD);
    }

    // ---------------------------------------------------------------- effects

    private static void applyEffects(Villager villager, ServerLevel level, ItemStack food, long gameTime) {
        Item item = food.getItem();

        int points = hungerPoints(food);
        if (points > 0) {
            VillagerFoodAccess.addFoodLevel(villager, points * VILLAGER_EFFECT_MULTIPLIER);
        }

        if (FoodCategoryTable.getCategory(item) == FoodCategory.HEALTH) {
            FoodCategoryTable.HealthData heal = FoodCategoryTable.getHealthData(item);
            if (heal != null) {
                VillagerHealthService.queueHeal(villager, heal.amount() * VILLAGER_EFFECT_MULTIPLIER, heal.durationTicks());
            }
        }

        Double push = FoodTemperatureEffects.getDirectPush(item);
        if (push != null && push != 0.0) {
            double doubled = push * VILLAGER_EFFECT_MULTIPLIER;
            VillagerFoodTempOffset current = villager.getData(ModAttachments.VILLAGER_FOOD_TEMP_OFFSET.get());
            double cap = Math.abs(doubled) * FOOD_TEMP_OFFSET_MAX_STACK;
            double combined = Math.max(-cap, Math.min(cap, current.currentAt(gameTime) + doubled));
            villager.setData(ModAttachments.VILLAGER_FOOD_TEMP_OFFSET.get(),
                    new VillagerFoodTempOffset((float) combined, gameTime, gameTime + FOOD_TEMP_OFFSET_TICKS));
        }
    }

    /** Bowls (and any other use-remainder) go back into the chest; dropped at the villager if full. */
    private static void returnRemainder(Villager villager, ServerLevel level, BlockPos bell, ItemStack food) {
        UseRemainder remainder = food.get(DataComponents.USE_REMAINDER);
        if (remainder == null) {
            return;
        }
        ItemStack leftover = VillageChestService.deposit(level, bell, remainder.convertInto().copy());
        if (!leftover.isEmpty()) {
            villager.spawnAtLocation(level, leftover);
        }
    }

    // ---------------------------------------------------------------- eating animation

    private static void startEating(Villager villager, ServerLevel level, ItemStack food, long gameTime) {
        ItemStack held = food.copyWithCount(1);
        if (villager.getMainHandItem().isEmpty()) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, held.copy());
        }
        villager.getNavigation().stop();
        long end = gameTime + EAT_TICKS;
        villager.setData(ModAttachments.VILLAGER_EATING_UNTIL.get(), end);
        MEALS.add(new Meal(villager, held, end));
        eatEffects(villager, level, held);
    }

    /** Ticks the running eating animations: sound and crumbs every few ticks, then clears the hand. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (MEALS.isEmpty()) {
            return;
        }
        Iterator<Meal> it = MEALS.iterator();
        while (it.hasNext()) {
            Meal meal = it.next();
            Villager villager = meal.villager();
            if (villager.isRemoved() || !(villager.level() instanceof ServerLevel level)) {
                it.remove();
                continue;
            }
            long now = level.getGameTime();
            if (now >= meal.endTick()) {
                endEating(villager, meal.food());
                it.remove();
            } else if ((meal.endTick() - now) % EAT_SOUND_INTERVAL == 0) {
                eatEffects(villager, level, meal.food());
            }
        }
    }

    private static void eatEffects(Villager villager, ServerLevel level, ItemStack food) {
        Vec3 look = villager.getViewVector(1.0F);
        Vec3 mouth = villager.getEyePosition().add(look.scale(0.3D)).subtract(0.0D, 0.25D, 0.0D);
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, food),
                mouth.x, mouth.y, mouth.z, 4, 0.08D, 0.05D, 0.08D, 0.05D);
        level.playSound(null, villager.getX(), villager.getY(), villager.getZ(),
                SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL,
                0.5F + 0.4F * level.getRandom().nextFloat(),
                (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.2F + 1.0F);
    }

    private static void endEating(Villager villager, ItemStack food) {
        villager.setData(ModAttachments.VILLAGER_EATING_UNTIL.get(), 0L);
        ItemStack hand = villager.getMainHandItem();
        // Only clear our own food; a trade display item may have replaced it.
        if (!villager.isTrading() && !hand.isEmpty() && ItemStack.isSameItemSameComponents(hand, food)) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    /** After a reload mid-meal (no running animation), clear the held food on the next read. */
    private static void finishStaleMeal(Villager villager, long gameTime) {
        long until = villager.getData(ModAttachments.VILLAGER_EATING_UNTIL.get());
        if (until <= 0L || gameTime < until) {
            return;
        }
        for (Meal meal : MEALS) {
            if (meal.villager() == villager) {
                return; // the tick handler will finish it
            }
        }
        villager.setData(ModAttachments.VILLAGER_EATING_UNTIL.get(), 0L);
        ItemStack hand = villager.getMainHandItem();
        if (!villager.isTrading() && !hand.isEmpty() && isEdible(hand)) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }
}
