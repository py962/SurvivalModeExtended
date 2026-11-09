package com.terrainlab.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.terrainlab.api.BorrowingAccess;
import com.terrainlab.advancement.NomadSleepTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Excludes a borrowing player from the sleep-skip vote without changing their own
 * {@code isSleeping()} state. Targets Player because {@code isSleepingLongEnough} is declared there.
 * Also holds a player out of the vote while the Nomad warning's cancel window is open.
 */
@Mixin(Player.class)
public abstract class BorrowingSleepExclusionMixin {

    @ModifyReturnValue(method = "isSleepingLongEnough", at = @At("RETURN"))
    private boolean terrainlab$excludeFromSleepSkipWhileBorrowing(boolean original) {
        Player self = (Player) (Object) this;

        if (self instanceof BorrowingAccess access && access.terrainlab$isBorrowing()) {
            return false;
        }

        return original;
    }

    /**
     * While the Nomad warning's cancel window is open the player does not count as sleeping long enough, so
     * the night cannot end (and the sleep cannot be recorded) before they have had time to leave the bed.
     */
    @ModifyReturnValue(method = "isSleepingLongEnough", at = @At("RETURN"), require = 1)
    private boolean terrainlab$holdSleepDuringNomadWarning(boolean original) {
        Player self = (Player) (Object) this;

        if (original && self instanceof ServerPlayer serverPlayer && NomadSleepTracker.holdsSleep(serverPlayer)) {
            return false;
        }

        return original;
    }
}
