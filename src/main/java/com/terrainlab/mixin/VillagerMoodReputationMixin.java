package com.terrainlab.mixin;

import com.terrainlab.registry.ModAttachments;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An unhappy villager thinks less of every player: -5 reputation for each discomfort it currently
 * has (cold or hot, fearful, tired, trapped, hungry, injured), read from its mood flags. Applied live on top
 * of gossip, so it raises that villager's prices only while the problem lasts and stores nothing.
 */
@Mixin(Villager.class)
public abstract class VillagerMoodReputationMixin {

    @Unique
    private static final int TERRAINLAB$REPUTATION_PER_DISCOMFORT = 5;

    @Inject(method = "getPlayerReputation", at = @At("RETURN"), cancellable = true, require = 1)
    private void terrainlab$moodPenalty(Player player, CallbackInfoReturnable<Integer> cir) {
        int flags = ((Villager) (Object) this).getData(ModAttachments.VILLAGER_MOOD_FLAGS.get());
        int discomforts = Integer.bitCount(flags);
        if (discomforts > 0) {
            cir.setReturnValue(cir.getReturnValue() - TERRAINLAB$REPUTATION_PER_DISCOMFORT * discomforts);
        }
    }
}
