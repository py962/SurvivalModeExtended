package com.terrainlab.mixin;

import com.terrainlab.compat.PiglinWardUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Awkward potion wards piglins against zombification, delivered by feeding, splash or lingering cloud. Each nested
 * class is a separate mixin; the lingering path is LingeringPotionPiglinWardMixin. Shared logic is in PiglinWardUtil.
 */
public final class PiglinWardMixins {

    private PiglinWardMixins() {
    }

    /** Feeding: runs at the head of {@code mobInteract}, before vanilla bartering. */
    @Mixin(Piglin.class)
    public static abstract class Feed {

        @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
        private void terrainlab$feedAwkwardPotion(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
            ItemStack stack = player.getItemInHand(hand);
            if (PiglinWardUtil.isAwkwardPotion(stack)) {
                AbstractPiglin piglin = (AbstractPiglin) (Object) this;
                if (piglin.level() instanceof ServerLevel) {
                    PiglinWardUtil.applyWard(piglin, player);
                    stack.consume(1, player);
                    cir.setReturnValue(InteractionResult.SUCCESS);
                }
            }
        }
    }

    /**
     * Thrown potions: vanilla {@code onHit} only calls {@code onHitAsPotion} for potions with effects, so an
     * Awkward potion is routed there explicitly. The splash and lingering ward logic then runs as usual.
     */
    @Mixin(AbstractThrownPotion.class)
    public static abstract class ThrownAwkward {

        @Shadow
        protected abstract void onHitAsPotion(ServerLevel level, ItemStack stack, HitResult hitResult);

        @Inject(method = "onHit", at = @At("TAIL"))
        private void terrainlab$hitAsAwkwardPotion(HitResult hitResult, CallbackInfo ci) {
            AbstractThrownPotion self = (AbstractThrownPotion) (Object) this;
            if (!(self.level() instanceof ServerLevel serverLevel)) {
                return;
            }

            ItemStack stack = self.getItem();
            PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            // Potions with effects already went through onHitAsPotion in vanilla.
            if (contents.hasEffects() || !PiglinWardUtil.isAwkwardPotion(contents)) {
                return;
            }

            this.onHitAsPotion(serverLevel, stack, hitResult);
        }
    }

    /** Splash: rescans the same 4/2/4 box vanilla's {@code onHitAsPotion} uses. */
    @Mixin(ThrownSplashPotion.class)
    public static abstract class Splash {

        @Inject(method = "onHitAsPotion", at = @At("TAIL"))
        private void terrainlab$wardPiglinsInSplash(ServerLevel level, ItemStack stack, HitResult hitResult, CallbackInfo ci) {
            if (!PiglinWardUtil.isAwkwardPotion(stack)) {
                return;
            }

            ThrownSplashPotion self = (ThrownSplashPotion) (Object) this;
            AABB aabb = self.getBoundingBox().move(hitResult.getLocation().subtract(self.position()));
            AABB searchBox = aabb.inflate(4.0, 2.0, 4.0);

            List<LivingEntity> nearby = self.level().getEntitiesOfClass(LivingEntity.class, searchBox);
            for (LivingEntity entity : nearby) {
                if (entity instanceof AbstractPiglin piglin) {
                    PiglinWardUtil.applyWard(piglin, self.getOwner());
                }
            }
        }
    }
}
