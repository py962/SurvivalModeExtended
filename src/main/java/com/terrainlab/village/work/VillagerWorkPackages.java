package com.terrainlab.village.work;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;

/**
 * Adds the profession work behaviours to the vanilla villager activity packages (see
 * VillagerGoalPackagesWorkMixin). Priority 4: after vanilla's walk-back-to-job-site (2) and before
 * the random work/stroll RunOne (5); all of them start only without a WALK_TARGET.
 */
public final class VillagerWorkPackages {

    private static final int PRIORITY = 4;

    private VillagerWorkPackages() {
    }

    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> extendWork(
            Holder<VillagerProfession> profession,
            ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> original) {
        ImmutableList.Builder<Pair<Integer, ? extends BehaviorControl<? super Villager>>> out = ImmutableList.builder();
        out.addAll(original);
        if (profession.is(VillagerProfession.FARMER)) {
            out.add(Pair.of(PRIORITY, new DepositProduceBehavior(VillagerProfession.FARMER, Activity.WORK, VillagerProducePlans.FARMER)));
            out.add(Pair.of(PRIORITY, new FarmerExtraHarvestBehavior()));
        } else if (profession.is(VillagerProfession.BUTCHER)) {
            out.add(Pair.of(PRIORITY, new DepositProduceBehavior(VillagerProfession.BUTCHER, Activity.WORK, VillagerProducePlans.BUTCHER)));
            out.add(Pair.of(PRIORITY, new ButcherHusbandryBehavior()));
        } else if (profession.is(VillagerProfession.FISHERMAN)) {
            out.add(Pair.of(PRIORITY, new DepositProduceBehavior(VillagerProfession.FISHERMAN, Activity.WORK, VillagerProducePlans.FISHERMAN_WORK)));
            out.add(Pair.of(PRIORITY, new FishermanFishingBehavior()));
        } else {
            return original;
        }
        return out.build();
    }

    /** The MEET package (no profession parameter): fishermen hand in leftover catch after work. */
    public static ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> extendMeet(
            ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>> original) {
        return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Villager>>>builder()
                .addAll(original)
                .add(Pair.of(PRIORITY, new DepositProduceBehavior(VillagerProfession.FISHERMAN, Activity.MEET,
                        VillagerProducePlans.FISHERMAN_END_OF_WORK)))
                .build();
    }
}
