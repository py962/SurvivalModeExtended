package com.terrainlab.mixin;

import com.terrainlab.compat.PiglinWardUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Lingering Awkward Potion clouds ward piglins standing inside them, re-checked every 5 ticks for
 * the cloud's lifetime.
 * <p>
 * Injected at {@code tick} HEAD as a standalone check, because vanilla's entity-application branch
 * is skipped for potions without effects. Re-applying the ward is idempotent.
 */
@Mixin(AreaEffectCloud.class)
public abstract class LingeringPotionPiglinWardMixin {

    private static final int TIME_BETWEEN_APPLICATIONS = 5; // ticks; same cadence as vanilla

    @Inject(method = "tick", at = @At("HEAD"))
    private void terrainlab$wardPiglinsInCloud(CallbackInfo ci) {
        AreaEffectCloud self = (AreaEffectCloud) (Object) this;

        if (!(self.level() instanceof ServerLevel)) {
            return;
        }
        if (self.tickCount < self.getWaitTime()) {
            return; // still in the cloud's initial wait, as in vanilla
        }
        if (self.tickCount % TIME_BETWEEN_APPLICATIONS != 0) {
            return;
        }

        PotionContents contents = self.get(DataComponents.POTION_CONTENTS);
        if (!PiglinWardUtil.isAwkwardPotion(contents)) {
            return;
        }

        float radius = self.getRadius();
        List<LivingEntity> nearby = self.level().getEntitiesOfClass(LivingEntity.class, self.getBoundingBox());
        for (LivingEntity entity : nearby) {
            if (entity instanceof AbstractPiglin piglin) {
                double dx = entity.getX() - self.getX();
                double dz = entity.getZ() - self.getZ();
                if (dx * dx + dz * dz <= (double) (radius * radius)) {
                    PiglinWardUtil.applyWard(piglin, self.getOwner());
                }
            }
        }
    }
}
