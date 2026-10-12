package com.terrainlab.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.terrainlab.api.BorrowingAccess;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Excludes a borrowing player from the sleep-skip vote without changing their own
 * {@code isSleeping()} state. Targets Player because {@code isSleepingLongEnough} is declared there.
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
}
