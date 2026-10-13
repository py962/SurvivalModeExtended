package com.terrainlab.mixin;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.CopperGolemAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.terrainlab.ai.CopperGolemTorchBehavior;

/**
 * Splices {@link CopperGolemTorchBehavior} into the copper golem's IDLE activity at priority -1,
 * ahead of vanilla's TransportItemsBetweenContainers (priority 0). Brain iterates priorities in
 * ascending order, so the torch behaviour is always offered first in a tick.
 */
@Mixin(CopperGolemAi.class)
public abstract class CopperGolemAiMixin {

    @ModifyArg(
            method = "initIdleActivity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/ActivityData;create(Lnet/minecraft/world/entity/schedule/Activity;Lcom/google/common/collect/ImmutableList;)Lnet/minecraft/world/entity/ai/ActivityData;"
            ),
            index = 1
    )
    private static ImmutableList<? extends Pair<Integer, ? extends BehaviorControl<? super CopperGolem>>> terrainlab$addTorchBehavior(
            ImmutableList<? extends Pair<Integer, ? extends BehaviorControl<? super CopperGolem>>> original) {
        return ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super CopperGolem>>>builder()
                .add(Pair.of(-1, new CopperGolemTorchBehavior()))
                .addAll(original)
                .build();
    }
}
