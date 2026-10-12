package com.terrainlab.mixin;

import com.terrainlab.item.OminousBannerItem;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every vanilla ominous banner comes from Raid.getOminousBannerInstance: patrol and raid captains'
 * helmets, the captain check, raiders picking banners up, /raid and the creative tab. Returning the
 * mod's banner here swaps all of them at once and keeps those checks consistent.
 */
@Mixin(Raid.class)
public abstract class RaidOminousBannerMixin {

    @Inject(method = "getOminousBannerInstance", at = @At("HEAD"), cancellable = true, require = 1)
    private static void terrainlab$modOminousBanner(HolderGetter<BannerPattern> patterns,
                                                    CallbackInfoReturnable<ItemStack> cir) {
        cir.setReturnValue(OminousBannerItem.createStack(patterns));
    }
}
