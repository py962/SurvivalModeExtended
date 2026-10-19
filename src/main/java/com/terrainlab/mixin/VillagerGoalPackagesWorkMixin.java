package com.terrainlab.mixin;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import com.terrainlab.village.work.VillagerWorkPackages;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Appends the farmer / butcher / fisherman work behaviours ({@code village.work}) to the WORK package,
 * and the fisherman's after-work catch delivery to the MEET package. Villager's brain provider calls
 * these once per brain, so each villager gets its own behaviour instances; the brain is rebuilt on a
 * profession change.
 */
@Mixin(VillagerGoalPackages.class)
public abstract class VillagerGoalPackagesWorkMixin {

    @Inject(method = "getWorkPackage", at = @At("RETURN"), cancellable = true)
    private static void terrainlab$addWorkBehaviours(Holder<VillagerProfession> profession, float speedModifier,
            CallbackInfoReturnable<ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>>> cir) {
        cir.setReturnValue(VillagerWorkPackages.extendWork(profession, cir.getReturnValue()));
    }

    @Inject(method = "getMeetPackage", at = @At("RETURN"), cancellable = true)
    private static void terrainlab$addMeetBehaviours(Holder<VillagerProfession> profession, float speedModifier,
            CallbackInfoReturnable<ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Villager>>>> cir) {
        cir.setReturnValue(VillagerWorkPackages.extendMeet(cir.getReturnValue()));
    }
}
