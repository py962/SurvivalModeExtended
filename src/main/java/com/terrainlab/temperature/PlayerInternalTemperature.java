package com.terrainlab.temperature;

import com.terrainlab.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import com.terrainlab.registry.ModMobEffects;
import net.minecraft.world.effect.MobEffectInstance;

import static com.terrainlab.temperature.PlayerTemperatureConstants.*;

/**
 * Server-side player internal temperature: drifts each tick towards the local temperature (ambient
 * plus wetness cooling and on-fire heat), applies temperature damage and Freezing/Heatstroke
 * effects, and syncs the value to the client.
 * <p>
 * Wetness is 1.0 when submerged, {@link #RAIN_WETNESS} in full rain, and otherwise decays; it also
 * cancels the fur cloak's insulation proportionally. A worn fur cloak sheds rain (no rain wetness)
 * but gives no protection when submerged. Easy difficulty skips damage and effects but
 * keeps drift and the damage accumulator running.
 */
public final class PlayerInternalTemperature {

    private static final boolean TEMP_DAMAGE_DIAGNOSTICS = false;

    /** Wetness when fully exposed to rain; -10C of cooling at COOLING_STRENGTH 20C. */
    private static final float RAIN_WETNESS = 0.5F;

    public static final double FIRE_PROTECTION_HEATING_RATE_PER_LEVEL = 0.05;

    private PlayerInternalTemperature() {
    }

    public static void tick(ServerPlayer player) {
        // Paused in creative/spectator: the value holds.
        if (player.isCreative() || player.isSpectator()) {
            return;
        }

        // Standing in any portal shields from heat and cold: the value holds and effects clear.
        if (isInPortal(player)) {
            clearExposureEffect(player, ModMobEffects.FREEZING);
            clearExposureEffect(player, ModMobEffects.HEATSTROKE);
            return;
        }

        double internalTemp = player.getData(ModAttachments.PLAYER_INTERNAL_TEMP.get());

        Vec3 posVec = player.position();
        BlockPos pos = BlockPos.containing(posVec.x(), posVec.y(), posVec.z());

        var season = com.terrainlab.season.SeasonManager.getCurrentTemperatureSeason(
                player.level().getGameTime(),
                getSeasonalOffsetServerSide(player)
        );

        // Day/night blend by the warped sun, ramping smoothly around sunset and sunrise.
        double nightFraction = com.terrainlab.season.SolarTime.nightFraction(player.level());

        double ambientTemp = LocalTemperatureService.getAmbientTemperature(
                player.level(),
                pos,
                season,
                nightFraction
        );

        // 1.0 = the whole 3x3 sample sees the sky; same model as the ambient calculation.
        double skyExposure = SkyExposureService.getExposure(player.level(), pos);

        boolean immersedNow = player.isEyeInFluid(FluidTags.WATER);

        float wetness = player.getData(ModAttachments.PLAYER_WETNESS.get());

        boolean rainingNow = player.level().isRainingAt(pos);
        boolean fullyExposedToRain = rainingNow && skyExposure >= 1.0;
        boolean furWorn = isFurWorn(player);

        if (immersedNow) {
            wetness = 1.0F;
        } else if (fullyExposedToRain && !furWorn) {
            wetness = RAIN_WETNESS;
        } else {
            wetness = (float) WaterImmersionEffect.decay(wetness, 1);
        }

        player.setData(ModAttachments.PLAYER_WETNESS.get(), wetness);

        double localTemp = ambientTemp
                + WaterImmersionEffect.coolingContribution(wetness);

        if (player.isOnFire()) {
            localTemp += ON_FIRE_HEAT_CONTRIBUTION;
        }

        double delta = localTemp - internalTemp;

        double rate = BASE_DRIFT_RATE;

        int fireProtection = fireProtectionLevels(player);
        if (fireProtection > 0 && delta > 0) {
            rate *= Math.max(0.0, 1.0 - fireProtection * FIRE_PROTECTION_HEATING_RATE_PER_LEVEL);
        }

        if (furWorn) {
            double baseFurMod = delta < 0
                    ? FUR_COOLING_RATE_MODIFIER
                    : FUR_HEATING_RATE_MODIFIER;

            double effectiveFurMod =
                    baseFurMod + wetness * (1.0 - baseFurMod);

            rate *= effectiveFurMod;
        }

        boolean warmthActive = hasWarmthEffect(player);
        boolean fireResistActive = player.hasEffect(MobEffects.FIRE_RESISTANCE);

        boolean wouldCool = delta < 0;
        boolean wouldHeat = delta > 0;

        if (warmthActive) {
            player.clearFreeze();

            if (wouldCool && internalTemp < COMFORTABLE_RESTING_VALUE) {
                internalTemp = Math.min(
                        COMFORTABLE_RESTING_VALUE,
                        internalTemp + EFFECT_RESTORE_RATE
                );
            }
        } else if (fireResistActive && wouldHeat) {
            if (internalTemp > COMFORTABLE_RESTING_VALUE) {
                internalTemp = Math.max(
                        COMFORTABLE_RESTING_VALUE,
                        internalTemp - EFFECT_RESTORE_RATE
                );
            }
        } else {
            internalTemp += delta * rate;
        }

        player.setData(
                ModAttachments.PLAYER_INTERNAL_TEMP.get(),
                (float) internalTemp
        );

        tickHealthDamage(player, internalTemp);
        tickTemperatureEffects(player, internalTemp);

        WaterDrinkHandler.tick(player);

        if (player.level().getGameTime() % 10 == 0) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    player,
                    new com.terrainlab.network.PlayerInternalTempPacket(
                            (float) internalTemp
                    )
            );
        }
    }

    /**
     * Instant change from food or drink, applied regardless of active effects. A value starting inside
     * [FOOD_PUSH_MIN_TEMP, FOOD_PUSH_MAX_TEMP] is clamped to that band; one starting outside is not.
     */
    private static boolean isInPortal(ServerPlayer player) {
        var box = player.getBoundingBox();
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX - 1.0E-7, box.maxY - 1.0E-7, box.maxZ - 1.0E-7))) {
            if (player.level().getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.Portal) {
                return true;
            }
        }
        return false;
    }

    public static void applyInstantPush(ServerPlayer player, double amount) {
        double internalTemp =
                player.getData(ModAttachments.PLAYER_INTERNAL_TEMP.get());

        boolean startedInsideBand =
                internalTemp >= FOOD_PUSH_MIN_TEMP
                        && internalTemp <= FOOD_PUSH_MAX_TEMP;

        internalTemp += amount;

        if (startedInsideBand) {
            internalTemp = Math.max(
                    FOOD_PUSH_MIN_TEMP,
                    Math.min(FOOD_PUSH_MAX_TEMP, internalTemp)
            );
        }

        player.setData(
                ModAttachments.PLAYER_INTERNAL_TEMP.get(),
                (float) internalTemp
        );
    }

    private static void tickHealthDamage(
            ServerPlayer player,
            double internalTemp
    ) {
        // Easy: no temperature damage; drift and sync continue.
        if (player.level().getDifficulty() == Difficulty.EASY) {
            return;
        }

        double severity =
                Math.abs(internalTemp - COMFORTABLE_RESTING_VALUE);

        double damageRate =
                Math.max(0.0, severity - DAMAGE_SEVERITY_THRESHOLD);

        damageRate =
                damageRate * damageRate * DAMAGE_SCALE_FACTOR;

        if (TEMP_DAMAGE_DIAGNOSTICS
                && severity > DAMAGE_SEVERITY_THRESHOLD) {

            // com.terrainlab.TerrainLab.LOGGER.info(
                    // "[TEMP_DAMAGE] difficulty={}, internalTemp={}, severity={}, threshold={}, damageRate={}, accumulatorThreshold={}, ticksToOneHalfHeart={}",
                    // player.level().getDifficulty(),
                    // internalTemp,
                    // severity,
                    // DAMAGE_SEVERITY_THRESHOLD,
                    // damageRate,
                    // DAMAGE_ACCUMULATOR_THRESHOLD_PER_HALF_HEART,
                    // damageRate > 0
                            // ? DAMAGE_ACCUMULATOR_THRESHOLD_PER_HALF_HEART / damageRate
                            // : Double.POSITIVE_INFINITY
            // );
        }

        if (damageRate <= 0.0) {
            return;
        }

        double accumulator =
                player.getData(
                        ModAttachments.PLAYER_HEAT_DAMAGE_ACCUMULATOR.get()
                );

        accumulator += damageRate;

        boolean isOverheating =
                internalTemp > COMFORTABLE_RESTING_VALUE;

        var damageSource = isOverheating
                ? player.damageSources().hotFloor()
                : player.damageSources().freeze();

        while (accumulator >= DAMAGE_ACCUMULATOR_THRESHOLD_PER_HALF_HEART) {
            accumulator -= DAMAGE_ACCUMULATOR_THRESHOLD_PER_HALF_HEART;

            boolean applied = player.hurtServer(
                    player.level(),
                    damageSource,
                    1.0F
            );

            if (TEMP_DAMAGE_DIAGNOSTICS) {
                // com.terrainlab.TerrainLab.LOGGER.info(
                        // "[TEMP_DAMAGE] hurtServer called, damageSource={}, applied={}, gameMode={}",
                        // damageSource,
                        // applied,
                        // player.gameMode.getGameModeForPlayer()
                // );
            }
        }

        player.setData(
                ModAttachments.PLAYER_HEAT_DAMAGE_ACCUMULATOR.get(),
                (float) accumulator
        );
    }

    private static void tickTemperatureEffects(
            ServerPlayer player,
            double internalTemp
    ) {
        boolean applies = player.level().getDifficulty() != Difficulty.EASY
                && !player.isCreative() && !player.isSpectator();

        // Warmth suppresses Freezing (see FreezingEffectHandler.isFreezing).
        if (applies && internalTemp <= FREEZING_EFFECT_THRESHOLD && !hasWarmthEffect(player)) {
            player.addEffect(new MobEffectInstance(ModMobEffects.FREEZING, EFFECT_REFRESH_TICKS, 0, false, false, true));
        } else {
            clearExposureEffect(player, ModMobEffects.FREEZING);
        }

        if (applies && internalTemp >= HEATSTROKE_EFFECT_THRESHOLD) {
            player.addEffect(new MobEffectInstance(ModMobEffects.HEATSTROKE, EFFECT_REFRESH_TICKS, 0, false, false, true));
        } else {
            clearExposureEffect(player, ModMobEffects.HEATSTROKE);
        }
    }

    /** Removes only the hidden refresh instance applied here, never a potion's. */
    private static void clearExposureEffect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        MobEffectInstance instance = player.getEffect(effect);
        if (instance != null && !instance.isVisible() && !instance.isAmbient()
                && instance.getDuration() <= EFFECT_REFRESH_TICKS) {
            player.removeEffect(effect);
        }
    }

    /** Summed over the four armour slots. */
    private static int fireProtectionLevels(ServerPlayer player) {
        var fireProtection = player.registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FIRE_PROTECTION);
        int total = 0;
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (slot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR) {
                total += net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                        fireProtection, player.getItemBySlot(slot));
            }
        }
        return total;
    }

    private static boolean isFurWorn(ServerPlayer player) {
        return com.terrainlab.compat.CuriosCompat.isWorn(
                player,
                "body",
                com.terrainlab.registry.ModItems.FUR_CLOAK.get()
        );
    }

    private static boolean hasWarmthEffect(ServerPlayer player) {
        return player.hasEffect(
                com.terrainlab.registry.ModMobEffects.WARMTH
        );
    }

    private static long getSeasonalOffsetServerSide(ServerPlayer player) {
        return com.terrainlab.data.SeasonSavedData
                .get(player.level())
                .getSeasonalOffset();
    }
}
