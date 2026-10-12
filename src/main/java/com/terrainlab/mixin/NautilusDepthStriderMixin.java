package com.terrainlab.mixin;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies Depth Strider on a ridden nautilus's saddle to its in-water ridden speed: +33% per level,
 * up to 2x at level III. The level is read from the saddle directly, because the nautilus's ridden
 * movement ignores {@code WATER_MOVEMENT_EFFICIENCY}.
 */
@Mixin(AbstractNautilus.class)
public abstract class NautilusDepthStriderMixin {

    @Inject(
            method = "getRiddenSpeed",
            at = @At("RETURN"),
            cancellable = true,
            require = 1
    )
    private void terrainlab$depthStriderSwimSpeed(
            Player rider,
            CallbackInfoReturnable<Float> cir
    ) {
        AbstractNautilus nautilus = (AbstractNautilus) (Object) this;

        int saddleLevel = nautilus.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .get(Enchantments.DEPTH_STRIDER)
                .map(holder ->
                        EnchantmentHelper.getItemEnchantmentLevel(
                                holder,
                                nautilus.getItemBySlot(EquipmentSlot.SADDLE)
                        )
                )
                .orElse(0);

        // getRiddenSpeed also covers land movement; boost in water only.
        if (saddleLevel <= 0 || !nautilus.isInWater()) {
            return;
        }

        double efficiency = Math.min(1.0, saddleLevel / 3.0);

        float baseSpeed = cir.getReturnValue();
        float modifiedSpeed = (float) (baseSpeed * (1.0 + efficiency));

        cir.setReturnValue(modifiedSpeed);
    }
}
