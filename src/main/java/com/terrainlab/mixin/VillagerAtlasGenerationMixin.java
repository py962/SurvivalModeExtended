package com.terrainlab.mixin;

import com.terrainlab.item.AtlasGenerationQueue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Queues Atlas pre-generation at a cartographer's position when its trades update at master level. */
@Mixin(Villager.class)
public class VillagerAtlasGenerationMixin {

    @Inject(
            method = "updateTrades",
            at = @At("TAIL")
    )
    private void terrainlab$startAtlasGenerationAtMaster(
            ServerLevel level,
            CallbackInfo ci
    ) {
        Villager villager = (Villager) (Object) this;

        VillagerData data = villager.getVillagerData();

        if (!data.profession().is(VillagerProfession.CARTOGRAPHER)) {
            return;
        }

        if (data.level() != 5) {
            return;
        }

        AtlasGenerationQueue.queuePreGeneratedAtlas(
                level,
                villager.blockPosition()
        );
    }
}
