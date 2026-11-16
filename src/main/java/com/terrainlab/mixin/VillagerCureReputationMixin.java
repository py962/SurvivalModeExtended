package com.terrainlab.mixin;

import com.terrainlab.registry.ModAttachments;
import com.terrainlab.season.Season;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Curing a zombie villager is worth +50 reputation with that villager, fading evenly to nothing over
 * one calendar state, instead of vanilla's permanent cure gossip. A new cure restarts it at 50.
 */
@Mixin(Villager.class)
public abstract class VillagerCureReputationMixin {

    @Unique
    private static final int TERRAINLAB$CURE_REPUTATION = 50;

    @Inject(method = "onReputationEventFrom", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$recordCure(ReputationEventType type, Entity source, CallbackInfo ci) {
        if (type != ReputationEventType.ZOMBIE_VILLAGER_CURED) return;
        Villager self = (Villager) (Object) this;
        self.getData(ModAttachments.VILLAGER_CURED_BY.get()).put(source.getUUID(), self.level().getGameTime());
        ci.cancel();
    }

    @Inject(method = "getPlayerReputation", at = @At("RETURN"), cancellable = true, require = 1)
    private void terrainlab$cureBonus(Player player, CallbackInfoReturnable<Integer> cir) {
        Villager self = (Villager) (Object) this;
        if (!self.hasData(ModAttachments.VILLAGER_CURED_BY.get())) return;
        Long curedAt = self.getData(ModAttachments.VILLAGER_CURED_BY.get()).get(player.getUUID());
        if (curedAt == null) return;
        long span = Season.stateLengthTicks();
        long elapsed = self.level().getGameTime() - curedAt;
        if (span <= 0 || elapsed >= span) return;
        int bonus = (int) Math.round(TERRAINLAB$CURE_REPUTATION * (span - Math.max(0L, elapsed)) / (double) span);
        cir.setReturnValue(cir.getReturnValue() + bonus);
    }
}
