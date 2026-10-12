package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModMobEffects;
import com.terrainlab.temperature.PlayerTemperatureConstants;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Freezing effect: applies the powder-snow freeze state without powder snow. The powder-snow
 * drag runs on both sides because player movement is simulated on the client; Warmth cancels
 * Freezing entirely. Freezing from cold exposure is visual and drag only (no freeze damage); from potions
 * and arrows it freezes fully and hurts as in vanilla.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class FreezingEffectHandler {

    /** Freezing is active and not cancelled out by Warmth. Used by FreezingClearFreezeMixin too. */
    public static boolean isFreezing(LivingEntity living) {
        return living.hasEffect(ModMobEffects.FREEZING) && !living.hasEffect(ModMobEffects.WARMTH);
    }

    /**
     * True when the active Freezing instance is the one PlayerInternalTemperature applies for cold
     * exposure: a short refresh with no particles. A potion or tipped arrow gives a long, visible instance,
     * which wins over the short one, so a potion still hurts while the player is also cold.
     */
    private static boolean isColdExposureOnly(LivingEntity living) {
        MobEffectInstance instance = living.getEffect(ModMobEffects.FREEZING);
        return instance != null
                && !instance.isVisible()
                && instance.getDuration() <= PlayerTemperatureConstants.EFFECT_REFRESH_TICKS;
    }

    @SubscribeEvent
    public static void onLivingTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (!isFreezing(living)) return;

        living.makeStuckInBlock(Blocks.POWDER_SNOW.defaultBlockState(), new Vec3(0.9F, 1.5, 0.9F));

        if (living.level().isClientSide()) return; // ticksFrozen is synced from the server

        living.setIsInPowderSnow(true);

        // Freezing from cold exposure stops one tick short of fully frozen: frost overlay and drag, but
        // vanilla's freeze damage (which needs fully frozen) never starts; temperature damage comes from
        // PlayerInternalTemperature instead. Freezing from potions, arrows or commands freezes fully and
        // hurts as before. Real powder snow freezes through vanilla's own code, unaffected by this cap.
        int cap = isColdExposureOnly(living)
                ? living.getTicksRequiredToFreeze() - 1
                : living.getTicksRequiredToFreeze();
        if (living.getTicksFrozen() < cap) {
            living.setTicksFrozen(living.getTicksFrozen() + 1);
        } else if (living.getTicksFrozen() > cap) {
            // E.g. a potion just ran out while still cold: drop back below fully frozen. Real powder snow
            // re-freezes fully later in the same tick, so its damage is unaffected.
            living.setTicksFrozen(cap);
        }
    }
}
