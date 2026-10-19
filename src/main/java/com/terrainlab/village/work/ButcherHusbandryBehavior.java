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
 * caribou), counting all adults present:
 * <ul>
 *   <li>fewer than {@link VillagerWorkConstants#BREED_BELOW} but at least
 *       {@link VillagerWorkConstants#BREED_MIN_ADULTS}: fetch 2 feed (own inventory first, else walk to
 *       the village chest), walk to two eligible adults and put each in love — once per species per
 *       {@link VillagerWorkConstants#BREED_COOLDOWN_TICKS};</li>
 *   <li>more than {@link VillagerWorkConstants#SLAUGHTER_ABOVE}: walk to the oldest eligible adult and
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

    private enum Phase { FETCH, FEED_A, FEED_B, KILL }

    private @Nullable Task task;
    private Phase phase = Phase.FEED_A;
    private @Nullable Species species;
    private @Nullable Animal animalA;
    private @Nullable Animal animalB;
    private @Nullable BlockPos chestPos;
    private @Nullable BlockPos bellPos;
    private @Nullable BlockPos jobSite;
    private int fed;

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
                a -> a.isAlive() && !a.isBaby() && VillagerWork.inWorkArea(site, a.position()));

        VillagerWorkState state = VillagerWork.state(body);
        List<Species> order = new ArrayList<>(List.of(Species.values()));
        Collections.shuffle(order, new java.util.Random(level.getRandom().nextLong()));

        for (Species s : order) {
            List<Animal> adults = animals.stream().filter(a -> a.getType() == s.type.get()).toList();
            int count = adults.size();
            if (count < VillagerWorkConstants.BREED_BELOW && count >= VillagerWorkConstants.BREED_MIN_ADULTS
                    && VillagerWorkState.elapsed(state.lastBreed(s.key), now, VillagerWorkConstants.BREED_COOLDOWN_TICKS)
                    && planBreed(level, body, s, adults, now)) {
                return true;
            }
        }
        if (VillagerWorkState.elapsed(state.lastSlaughter(), now, VillagerWorkConstants.SLAUGHTER_COOLDOWN_TICKS)
                && VillagerWork.freeSlots(body.getInventory()) >= 2) {
            for (Species s : order) {
                List<Animal> adults = animals.stream().filter(a -> a.getType() == s.type.get()).toList();
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

    private boolean planBreed(ServerLevel level, Villager body, Species s, List<Animal> adults, long now) {
        List<Animal> ready = adults.stream().filter(ButcherHusbandryBehavior::canBreedNow).toList();
        Animal a = null;
        Animal b = null;
        double best = Double.MAX_VALUE;
        double maxSq = VillagerWorkConstants.BREED_PARTNER_RADIUS * VillagerWorkConstants.BREED_PARTNER_RADIUS;
        for (int i = 0; i < ready.size(); i++) {
            for (int j = i + 1; j < ready.size(); j++) {
                double d = ready.get(i).distanceToSqr(ready.get(j));
                if (d <= maxSq && d < best) {
                    best = d;
                    a = ready.get(i);
                    b = ready.get(j);
                }
            }
        }
        if (a == null) {
            return false;
        }
        int own = VillagerWork.count(body.getInventory(), s.food);
        if (own < 2 && own + VillageChestService.count(level, bellPos, s.food) < 2) {
            return false;
        }
        // Nearer parent first.
        if (a.distanceToSqr(body) > b.distanceToSqr(body)) {
            Animal t = a;
            a = b;
            b = t;
        }
        task = Task.BREED;
        species = s;
        animalA = a;
        animalB = b;
        fed = 0;
        phase = own >= 2 ? Phase.FEED_A : Phase.FETCH;
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
            case FEED_A -> feed(level, body, now, animalA, Phase.FEED_B);
            case FEED_B -> feed(level, body, now, animalB, null);
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
            int need = 2 - VillagerWork.count(body.getInventory(), species.food);
            VillagerWork.setChestOpen(level, chestPos, true);
            while (need > 0) {
                ItemStack got = VillageChestService.take(level, bellPos, species.food, need);
                if (got.isEmpty()) {
                    break;
                }
                need -= got.getCount();
                VillagerWork.giveOrDrop(body, level, got);
            }
            VillagerWork.setChestOpen(level, chestPos, false);
            if (need > 0) {
                fail(body, now);
                return;
            }
            phase = Phase.FEED_A;
            beginLeg(now);
        } else if (legTimedOut(now)) {
            fail(body, now);
        } else {
            walkTo(body, chestPos, 1);
        }
    }

    private boolean stillValid(@Nullable Animal animal) {
        return animal != null && !animal.isRemoved() && animal.isAlive() && jobSite != null
                && VillagerWork.inWorkArea(jobSite, animal.position());
    }

    private void feed(ServerLevel level, Villager body, long now, @Nullable Animal target, @Nullable Phase next) {
        if (!stillValid(target) || !canBreedNow(target)) {
            fail(body, now);
            return;
        }
        ItemStack food = firstMatching(body, species.food);
        if (food.isEmpty()) {
            fail(body, now);
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
            fed++;
            if (next == null) {
                VillagerWork.setState(body, VillagerWork.state(body).withLastBreed(species.key, now));
                finish();
            } else {
                phase = next;
                beginLeg(now);
            }
        } else if (legTimedOut(now)) {
            fail(body, now);
        } else {
            walkTo(body, target, 1);
        }
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
        animalB = null;
    }
}
