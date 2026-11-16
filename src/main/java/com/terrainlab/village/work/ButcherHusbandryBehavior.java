package com.terrainlab.village.work;

import com.terrainlab.registry.ModAttachments;
import com.terrainlab.registry.ModEntities;
import com.terrainlab.village.VillageChestService;
import com.terrainlab.village.VillagerWorkConstants;
import com.terrainlab.village.VillagerWorkState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Butcher husbandry within the work area around the job site. Per species (chicken, sheep, pig, cow,
 * caribou):
 * <ul>
 *   <li>fewer than {@link VillagerWorkConstants#BREED_BELOW} animals (adults and babies) and at least
 *       {@link VillagerWorkConstants#BREED_MIN_ADULTS}: fetch feed (own inventory first, else walk to
 *       the village chest) and put every adult that can breed in love, nearest first, without waiting
 *       for pairs — once per species per {@link VillagerWorkConstants#BREED_COOLDOWN_TICKS};</li>
 *   <li>more than {@link VillagerWorkConstants#SLAUGHTER_ABOVE} adults: walk to the oldest eligible adult and
 *       kill it with one blow (villager as the damage source, so normal mob-kill loot drops); the fresh
 *       drops go into the butcher's inventory, raw food smoked instantly. At most once per
 *       {@link VillagerWorkConstants#SLAUGHTER_COOLDOWN_TICKS}. {@link DepositProduceBehavior} then
 *       carries them to the chest.</li>
 * </ul>
 * Never touches named, leashed, ridden/riding, saddled or armoured, tamed or owned animals. Does
 * nothing if the village has no chest.
 */
public class ButcherHusbandryBehavior extends AbstractVillagerWorkBehavior {

    /** A species the butcher manages: entity type and breeding feed. */
    public enum Species {
        CHICKEN("chicken", () -> EntityTypes.CHICKEN, s -> s.is(ItemTags.CHICKEN_FOOD)),
        SHEEP("sheep", () -> EntityTypes.SHEEP, s -> s.is(Items.WHEAT)),
        PIG("pig", () -> EntityTypes.PIG, s -> s.is(Items.CARROT) || s.is(Items.POTATO) || s.is(Items.BEETROOT)),
        COW("cow", () -> EntityTypes.COW, s -> s.is(Items.WHEAT)),
        CARIBOU("caribou", () -> ModEntities.CARIBOU.get(), s -> s.is(Items.WHEAT));

        final String key;
        final Supplier<EntityType<?>> type;
        final Predicate<ItemStack> food;

        Species(String key, Supplier<EntityType<?>> type, Predicate<ItemStack> food) {
            this.key = key;
            this.type = type;
            this.food = food;
        }
    }

    public static boolean isAnyBreedingFood(ItemStack stack) {
        for (Species s : Species.values()) {
            if (s.food.test(stack)) {
                return true;
            }
        }
        return false;
    }

    private enum Task { BREED, SLAUGHTER }

    private enum Phase { FETCH, FEED, KILL }

    private @Nullable Task task;
    private Phase phase = Phase.FEED;
    private @Nullable Species species;
    private @Nullable Animal animalA;
    private final List<Animal> feedQueue = new ArrayList<>();
    private @Nullable BlockPos chestPos;
    private @Nullable BlockPos bellPos;
    private @Nullable BlockPos jobSite;

    public ButcherHusbandryBehavior() {
        super(VillagerProfession.BUTCHER, Activity.WORK);
    }

    @Override
    protected int retryDelay() {
        return VillagerWorkConstants.BUTCHER_SCAN_INTERVAL_TICKS;
    }

    // ---------------------------------------------------------------- eligibility

    /** Animals the butcher may feed or kill: no names, leads, vehicles, saddles/armour or owners. */
    static boolean isFreeAnimal(Animal animal) {
        if (!animal.isAlive() || animal.hasCustomName() || animal.isLeashed()
                || animal.isPassenger() || animal.isVehicle()) {
            return false;
        }
        if (animal instanceof TamableAnimal tamable && tamable.isTame()) {
            return false;
        }
        if (animal instanceof AbstractHorse horse && horse.isTamed()) {
            return false;
        }
        if (animal instanceof OwnableEntity owned && owned.getOwnerReference() != null) {
            return false;
        }
        if (animal.getData(ModAttachments.MOUNT_OWNER.get()).isPresent()) {
            return false;
        }
        return animal.getItemBySlot(EquipmentSlot.SADDLE).isEmpty()
                && animal.getItemBySlot(EquipmentSlot.BODY).isEmpty();
    }

    private static boolean canBreedNow(Animal animal) {
        return isFreeAnimal(animal) && !animal.isBaby() && animal.getAge() == 0 && animal.canFallInLove();
    }

    // ---------------------------------------------------------------- plan

    @Override
    protected boolean plan(ServerLevel level, Villager body, BlockPos site, long now) {
        Optional<BlockPos> bell = VillagerWork.bell(body, level, site);
        Optional<BlockPos> chest = bell.flatMap(b -> VillageChestService.findChest(level, b));
        if (chest.isEmpty()) {
            return false;
        }
        bellPos = bell.get();
        chestPos = chest.get();
        jobSite = site;

        int r = VillagerWorkConstants.RANGE;
        AABB box = new AABB(site).inflate(r, VillagerWorkConstants.RANGE_VERTICAL, r);
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, box,
                a -> a.isAlive() && VillagerWork.inWorkArea(site, a.position()));

        VillagerWorkState state = VillagerWork.state(body);
        List<Species> order = new ArrayList<>(List.of(Species.values()));
        Collections.shuffle(order, new java.util.Random(level.getRandom().nextLong()));

        for (Species s : order) {
            List<Animal> adults = animals.stream().filter(a -> a.getType() == s.type.get() && !a.isBaby()).toList();
            // Babies count towards the breeding limit, so the herd does not overshoot while they grow.
            int count = (int) animals.stream().filter(a -> a.getType() == s.type.get()).count();
            if (count < VillagerWorkConstants.BREED_BELOW && adults.size() >= VillagerWorkConstants.BREED_MIN_ADULTS
                    && VillagerWorkState.elapsed(state.lastBreed(s.key), now, VillagerWorkConstants.BREED_COOLDOWN_TICKS)
                    && planBreed(level, body, s, adults, count, now)) {
                return true;
            }
        }
        if (VillagerWorkState.elapsed(state.lastSlaughter(), now, VillagerWorkConstants.SLAUGHTER_COOLDOWN_TICKS)
                && VillagerWork.freeSlots(body.getInventory()) >= 2) {
            for (Species s : order) {
                List<Animal> adults = animals.stream().filter(a -> a.getType() == s.type.get() && !a.isBaby()).toList();
                if (adults.size() > VillagerWorkConstants.SLAUGHTER_ABOVE) {
                    Optional<Animal> victim = adults.stream()
                            .filter(a -> isFreeAnimal(a) && !a.isInLove())
                            .max(Comparator.comparingInt(a -> a.tickCount));
                    if (victim.isPresent()) {
                        task = Task.SLAUGHTER;
                        species = s;
                        animalA = victim.get();
                        phase = Phase.KILL;
                        beginLeg(now);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean planBreed(ServerLevel level, Villager body, Species s, List<Animal> adults, int count, long now) {
        // Each pair makes one baby, so feed at most enough to fill the herd up to the limit.
        int room = 2 * (VillagerWorkConstants.BREED_BELOW - count);
        List<Animal> ready = adults.stream()
                .filter(ButcherHusbandryBehavior::canBreedNow)
                .sorted(Comparator.comparingDouble(a -> a.distanceToSqr(body)))
                .limit(Math.max(1, room))
                .toList();
        if (ready.isEmpty()) {
            return false;
        }
        int own = VillagerWork.count(body.getInventory(), s.food);
        int available = own + VillageChestService.count(level, bellPos, s.food);
        if (available < 1) {
            return false;
        }
        task = Task.BREED;
        species = s;
        feedQueue.clear();
        feedQueue.addAll(ready.subList(0, Math.min(ready.size(), available)));
        phase = own >= feedQueue.size() ? Phase.FEED : Phase.FETCH;
        beginLeg(now);
        return true;
    }

    // ---------------------------------------------------------------- work

    @Override
    protected void work(ServerLevel level, Villager body, long now) {
        if (task == null || species == null) {
            fail(body, now);
            return;
        }
        switch (phase) {
            case FETCH -> fetch(level, body, now);
            case FEED -> feed(level, body, now);
            case KILL -> kill(level, body, now);
        }
    }

    private void fetch(ServerLevel level, Villager body, long now) {
        if (chestPos == null || bellPos == null) {
            fail(body, now);
            return;
        }
        Vec3 centre = Vec3.atCenterOf(chestPos);
        if (VillagerWork.withinReach(body, centre)) {
            VillagerWork.setChestOpen(level, chestPos, true);
            makeRoom(level, body);
            topUp(level, body, species.food, Math.max(feedQueue.size(), VillagerWorkConstants.FEED_RESERVE));
            // Keep a little of both feeds on hand so later breeding can skip the chest.
            topUp(level, body, Species.COW.food, VillagerWorkConstants.FEED_RESERVE);
            topUp(level, body, Species.PIG.food, VillagerWorkConstants.FEED_RESERVE);
            VillagerWork.setChestOpen(level, chestPos, false);
            int carried = VillagerWork.count(body.getInventory(), species.food);
            if (carried < 1) {
                fail(body, now);
                return;
            }
            // Less feed than expected: feed the nearest ones only.
            while (feedQueue.size() > carried) {
                feedQueue.removeLast();
            }
            phase = Phase.FEED;
            beginLeg(now);
        } else if (legTimedOut(now)) {
            fail(body, now);
        } else {
            walkTo(body, chestPos, 1);
        }
    }

    private void topUp(ServerLevel level, Villager body, Predicate<ItemStack> feed, int target) {
        int need = target - VillagerWork.count(body.getInventory(), feed);
        while (need > 0) {
            ItemStack got = VillageChestService.take(level, bellPos, feed, need);
            if (got.isEmpty()) {
                return;
            }
            need -= got.getCount();
            VillagerWork.giveOrDrop(body, level, got);
        }
    }

    /** Puts everything back except the feeds and one stack of the butcher's own food, so the feed fits. */
    private void makeRoom(ServerLevel level, Villager body) {
        var inv = body.getInventory();
        boolean keptFood = false;
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.isEmpty() || species.food.test(stack) || Species.COW.food.test(stack) || Species.PIG.food.test(stack)) {
                continue;
            }
            if (!keptFood && !VillagerProducePlans.isButcherProduce(stack) && !isAnyBreedingFood(stack)) {
                keptFood = true;
                continue;
            }
            inv.setItem(slot, VillageChestService.deposit(level, bellPos, stack.copy()));
        }
    }

    private boolean stillValid(@Nullable Animal animal) {
        return animal != null && !animal.isRemoved() && animal.isAlive() && jobSite != null
                && VillagerWork.inWorkArea(jobSite, animal.position());
    }

    private void feed(ServerLevel level, Villager body, long now) {
        // Animals that wandered off or were fed meanwhile are skipped, not a reason to give up.
        while (!feedQueue.isEmpty() && (!stillValid(feedQueue.getFirst()) || !canBreedNow(feedQueue.getFirst()))) {
            feedQueue.removeFirst();
            beginLeg(now);
        }
        if (feedQueue.isEmpty()) {
            endBreeding(body, now);
            return;
        }
        Animal target = feedQueue.getFirst();
        ItemStack food = firstMatching(body, species.food);
        if (food.isEmpty()) {
            endBreeding(body, now);
            return;
        }
        holdItem(body, food);
        if (body.distanceToSqr(target) <= VillagerWorkConstants.ACT_REACH * VillagerWorkConstants.ACT_REACH) {
            body.getLookControl().setLookAt(target);
            body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
            VillagerWork.remove(body.getInventory(), species.food, 1);
            target.setInLove(null);
            if (species == Species.CARIBOU) {
                target.setData(ModAttachments.CARIBOU_VILLAGE_LOVE.get(), true);
            }
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_EAT,
                    SoundSource.NEUTRAL, 0.6F, 1.0F);
            feedQueue.removeFirst();
            beginLeg(now);
        } else if (legTimedOut(now)) {
            // This one can't be reached; carry on with the rest.
            feedQueue.removeFirst();
            beginLeg(now);
        } else {
            walkTo(body, target, 1);
        }
    }

    private void endBreeding(Villager body, long now) {
        VillagerWork.setState(body, VillagerWork.state(body).withLastBreed(species.key, now));
        finish();
    }

    private void kill(ServerLevel level, Villager body, long now) {
        Animal victim = animalA;
        if (!stillValid(victim) || !isFreeAnimal(victim)) {
            fail(body, now);
            return;
        }
        if (body.distanceToSqr(victim) <= VillagerWorkConstants.ACT_REACH * VillagerWorkConstants.ACT_REACH) {
            body.getLookControl().setLookAt(victim);
            body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
            AABB dropArea = victim.getBoundingBox().inflate(1.5D);
            victim.hurtServer(level, level.damageSources().mobAttack(body), victim.getHealth() + 20.0F);
            VillagerWork.setState(body, VillagerWork.state(body).withLastSlaughter(now));
            if (victim.isDeadOrDying()) {
                collectDrops(level, body, dropArea);
            }
            finish();
        } else if (legTimedOut(now)) {
            fail(body, now);
        } else {
            walkTo(body, victim, 1);
        }
    }

    /** Picks up the kill's fresh drops (age ≤ 2 ticks) into the inventory, smoking raw food. */
    private static void collectDrops(ServerLevel level, Villager body, AABB area) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area, e -> e.isAlive() && e.getAge() <= 2)) {
            ItemStack stack = item.getItem().copy();
            ItemStack converted = VillagerProducePlans.smoked(level, stack);
            int count = stack.getCount();
            ItemStack rest = body.getInventory().addItem(converted);
            if (rest.isEmpty()) {
                body.take(item, count);
                item.discard();
            } else {
                // Inventory full: what did not fit stays on the ground.
                item.setItem(rest);
            }
        }
        level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL,
                0.2F, (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 1.4F + 2.0F);
    }

    private static ItemStack firstMatching(Villager body, Predicate<ItemStack> filter) {
        for (int slot = 0; slot < body.getInventory().getContainerSize(); slot++) {
            ItemStack stack = body.getInventory().getItem(slot);
            if (!stack.isEmpty() && filter.test(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    protected void cleanup(ServerLevel level, Villager body, long now) {
        task = null;
        species = null;
        animalA = null;
        feedQueue.clear();
    }
}
