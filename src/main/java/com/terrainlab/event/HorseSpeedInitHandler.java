package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.attachment.HorseSprintConfig;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.animal.equine.Horse;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;

/**
 * Scales each horse's naturally rolled movement speed down to trot range once,
 * preserving breeding variance; sprinting restores the upper range.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class HorseSpeedInitHandler {

    /** Vanilla's horse speed range (AbstractHorse.generateSpeed with 0 and 1). */
    private static final double MIN_NATURAL_SPEED = 0.1125;
    private static final double MAX_NATURAL_SPEED = 0.3375;

    /**
     * Re-rolls a horse foal's natural speed from its parents' unscaled speeds
     * (vanilla rolls from the already-scaled values), applies the trot scale
     * once, and marks the foal initialised so onJoinLevel skips it.
     */
    @SubscribeEvent
    public static void onBabySpawn(BabyEntitySpawnEvent event) {
        if (!(event.getChild() instanceof Horse foal)
                || !(event.getParentA() instanceof Horse parentA)
                || !(event.getParentB() instanceof Horse parentB)) {
            return;
        }
        AttributeInstance speedAttr = foal.getAttributes().getInstance(Attributes.MOVEMENT_SPEED);
        if (speedAttr == null) return;

        double natural = offspringValue(naturalSpeed(parentA), naturalSpeed(parentB), foal);
        speedAttr.setBaseValue(natural * HorseSprintConfig.TROT_SCALE);
        foal.setData(ModAttachments.HORSE_SPEED_INITIALIZED.get(), true);
    }

    /** A horse's speed before the trot scale was applied to it. */
    private static double naturalSpeed(Horse horse) {
        double base = horse.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        return horse.getData(ModAttachments.HORSE_SPEED_INITIALIZED.get())
                ? base / HorseSprintConfig.TROT_SCALE
                : base;
    }

    /** Copy of vanilla's AbstractHorse.createOffspringAttribute (package-private there). */
    private static double offspringValue(double a, double b, Horse foal) {
        a = Math.max(MIN_NATURAL_SPEED, Math.min(MAX_NATURAL_SPEED, a));
        b = Math.max(MIN_NATURAL_SPEED, Math.min(MAX_NATURAL_SPEED, b));
        var random = foal.getRandom();
        double spread = 0.15 * (MAX_NATURAL_SPEED - MIN_NATURAL_SPEED);
        double range = Math.abs(a - b) + spread * 2.0;
        double mean = (a + b) / 2.0;
        double offset = (random.nextDouble() + random.nextDouble() + random.nextDouble()) / 3.0 - 0.5;
        double value = mean + range * offset;
        if (value > MAX_NATURAL_SPEED) return MAX_NATURAL_SPEED - (value - MAX_NATURAL_SPEED);
        if (value < MIN_NATURAL_SPEED) return MIN_NATURAL_SPEED + (MIN_NATURAL_SPEED - value);
        return value;
    }

    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Horse horse)) return;

        // Scale only once: the event also fires on every chunk reload and dimension change.
        if (horse.getData(ModAttachments.HORSE_SPEED_INITIALIZED.get())) return;

        AttributeInstance speedAttr = horse.getAttributes().getInstance(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            double naturalSpeed = speedAttr.getBaseValue();
            speedAttr.setBaseValue(naturalSpeed * HorseSprintConfig.TROT_SCALE);
        }

        horse.setData(ModAttachments.HORSE_SPEED_INITIALIZED.get(), true);
    }
}
