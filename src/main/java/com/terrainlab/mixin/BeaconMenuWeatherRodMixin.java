package com.terrainlab.mixin;

import com.terrainlab.weather.BeaconWeather;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * Server side of beacon weather control: when the payment is a breeze or blaze rod, the confirm
 * packet consumes the rod and changes the weather instead of setting beacon powers.
 */
@Mixin(BeaconMenu.class)
public abstract class BeaconMenuWeatherRodMixin {

    @Shadow @Final private ContainerLevelAccess access;

    @Shadow public abstract int getLevels();

    @Inject(method = "updateEffects", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$weatherRod(Optional<Holder<MobEffect>> primary,
                                       Optional<Holder<MobEffect>> secondary,
                                       CallbackInfo ci) {
        // The payment slot is the menu's first slot (added first in BeaconMenu's constructor).
        var payment = ((BeaconMenu) (Object) this).getSlot(0);
        ItemStack stack = payment.getItem();
        BeaconWeather.Kind kind = BeaconWeather.kindOf(stack);
        if (kind == null) {
            return;
        }
        ci.cancel();
        int levels = this.getLevels();
        this.access.execute((level, pos) -> {
            if (level instanceof ServerLevel serverLevel && BeaconWeather.problem(level, kind, levels) == null) {
                payment.remove(1);
                BeaconWeather.apply(serverLevel, pos, kind);
                com.terrainlab.advancement.ModAdvancements.awardNearby(serverLevel, pos.getCenter(), 16.0, "beacon_weather");
            }
        });
    }
}
