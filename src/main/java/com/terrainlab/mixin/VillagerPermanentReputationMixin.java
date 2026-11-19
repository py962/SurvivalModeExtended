package com.terrainlab.mixin;

import com.terrainlab.registry.ModAttachments;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds reputation changes that never decay, kept apart from gossip (e.g. for killing the village's iron golem). */
@Mixin(Villager.class)
public abstract class VillagerPermanentReputationMixin {

    @Inject(method = "getPlayerReputation", at = @At("RETURN"), cancellable = true, require = 1)
    private void terrainlab$permanentReputation(Player player, CallbackInfoReturnable<Integer> cir) {
        Villager self = (Villager) (Object) this;
        if (!self.hasData(ModAttachments.VILLAGER_PERMANENT_REPUTATION.get())) return;
        Integer amount = self.getData(ModAttachments.VILLAGER_PERMANENT_REPUTATION.get()).get(player.getUUID());
        if (amount != null) {
            cir.setReturnValue(cir.getReturnValue() + amount);
        }
    }
}
