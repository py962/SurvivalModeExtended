package com.terrainlab.registry;

import com.terrainlab.TerrainLab;
import com.terrainlab.celestial.CelestialVariant;
import com.terrainlab.effect.PhasingMobEffect;
import com.terrainlab.effect.PhaseBlockingMobEffect;
import com.terrainlab.effect.GripMobEffect;
import com.terrainlab.effect.SlipperyMobEffect;
import com.terrainlab.effect.BorrowingMobEffect;
import com.terrainlab.effect.ExplosiveMobEffect;
import com.terrainlab.effect.WarmthMobEffect;
import com.terrainlab.effect.FreezingMobEffect;
import com.terrainlab.effect.SpectralVisionMobEffect;
import com.terrainlab.effect.CelestialMarkerMobEffect;
import net.minecraft.core.registries.Registries;
import com.terrainlab.effect.HeatstrokeMobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/** Mob effect registrations, including the cosmetic Celestial amulet/conduit marker effects. */
public class ModMobEffects {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, TerrainLab.MODID);

    // Pass through any breakable block.
    public static final DeferredHolder<MobEffect, PhasingMobEffect> PHASING =
            MOB_EFFECTS.register("phasing", PhasingMobEffect::new);

    // Applied to mobs (vex, ender dragon) to suppress their noclip movement.
    public static final DeferredHolder<MobEffect, PhaseBlockingMobEffect> PHASE_BLOCKING =
            MOB_EFFECTS.register("phase_blocking", PhaseBlockingMobEffect::new);

    // Climb any solid block like a ladder.
    public static final DeferredHolder<MobEffect, GripMobEffect> GRIP =
            MOB_EFFECTS.register("grip", GripMobEffect::new);

    // Every block behaves like blue ice.
    public static final DeferredHolder<MobEffect, SlipperyMobEffect> SLIPPERY =
            MOB_EFFECTS.register("slippery", SlipperyMobEffect::new);

    // While asleep, passively view through a random nearby mob; can wake at any time.
    public static final DeferredHolder<MobEffect, BorrowingMobEffect> BORROWING =
            MOB_EFFECTS.register("borrowing", BorrowingMobEffect::new);

    // Explosive arrow payload; not meant as a drinkable effect.
    public static final DeferredHolder<MobEffect, ExplosiveMobEffect> EXPLOSIVE =
            MOB_EFFECTS.register("explosive", ExplosiveMobEffect::new);

    // Immunity to cold effects and damage.
    public static final DeferredHolder<MobEffect, WarmthMobEffect> WARMTH =
            MOB_EFFECTS.register("warmth", WarmthMobEffect::new);

    // Immediately applies the powder-snow freezing state.
    public static final DeferredHolder<MobEffect, FreezingMobEffect> FREEZING =
            MOB_EFFECTS.register("freezing", FreezingMobEffect::new);

    // Hostile mobs within range glow through walls.
    public static final DeferredHolder<MobEffect, SpectralVisionMobEffect> SPECTRAL_VISION =
            MOB_EFFECTS.register("spectral_vision", SpectralVisionMobEffect::new);

    public static final DeferredHolder<MobEffect, HeatstrokeMobEffect> HEATSTROKE = MOB_EFFECTS.register(
            "heatstroke", () -> new HeatstrokeMobEffect(MobEffectCategory.HARMFUL, 0xCC2222));

    // Cosmetic Celestial marker effects, one amulet and one conduit marker per variant, tinted with
    // the variant colour. BENEFICIAL, like vanilla's Conduit Power. Breath has no conduit marker:
    // vanilla's Conduit Power icon fills that role (see CelestialEffects#conduitIconFor).
    public static final DeferredHolder<MobEffect, CelestialMarkerMobEffect> BREATH_AMULET = MOB_EFFECTS.register(
            "breath_amulet", () -> new CelestialMarkerMobEffect(MobEffectCategory.BENEFICIAL, CelestialVariant.BREATH.tintColor()));

    public static final DeferredHolder<MobEffect, CelestialMarkerMobEffect> LIFE_AMULET = MOB_EFFECTS.register(
            "life_amulet", () -> new CelestialMarkerMobEffect(MobEffectCategory.BENEFICIAL, CelestialVariant.LIFE.tintColor()));
    public static final DeferredHolder<MobEffect, CelestialMarkerMobEffect> LIFE_CONDUIT = MOB_EFFECTS.register(
            "life_conduit", () -> new CelestialMarkerMobEffect(MobEffectCategory.BENEFICIAL, CelestialVariant.LIFE.tintColor()));

    public static final DeferredHolder<MobEffect, CelestialMarkerMobEffect> DEATH_AMULET = MOB_EFFECTS.register(
            "death_amulet", () -> new CelestialMarkerMobEffect(MobEffectCategory.BENEFICIAL, CelestialVariant.DEATH.tintColor()));
    public static final DeferredHolder<MobEffect, CelestialMarkerMobEffect> DEATH_CONDUIT = MOB_EFFECTS.register(
            "death_conduit", () -> new CelestialMarkerMobEffect(MobEffectCategory.BENEFICIAL, CelestialVariant.DEATH.tintColor()));

    public static final DeferredHolder<MobEffect, CelestialMarkerMobEffect> SHIELD_AMULET = MOB_EFFECTS.register(
            "shield_amulet", () -> new CelestialMarkerMobEffect(MobEffectCategory.BENEFICIAL, CelestialVariant.SHIELD.tintColor()));
    public static final DeferredHolder<MobEffect, CelestialMarkerMobEffect> SHIELD_CONDUIT = MOB_EFFECTS.register(
            "shield_conduit", () -> new CelestialMarkerMobEffect(MobEffectCategory.BENEFICIAL, CelestialVariant.SHIELD.tintColor()));

    public static final DeferredHolder<MobEffect, CelestialMarkerMobEffect> PEACE_AMULET = MOB_EFFECTS.register(
            "peace_amulet", () -> new CelestialMarkerMobEffect(MobEffectCategory.BENEFICIAL, CelestialVariant.PEACE.tintColor()));
    public static final DeferredHolder<MobEffect, CelestialMarkerMobEffect> PEACE_CONDUIT = MOB_EFFECTS.register(
            "peace_conduit", () -> new CelestialMarkerMobEffect(MobEffectCategory.BENEFICIAL, CelestialVariant.PEACE.tintColor()));

    public static final DeferredHolder<MobEffect, CelestialMarkerMobEffect> EYE_AMULET = MOB_EFFECTS.register(
            "eye_amulet", () -> new CelestialMarkerMobEffect(MobEffectCategory.BENEFICIAL, CelestialVariant.EYE.tintColor()));
    public static final DeferredHolder<MobEffect, CelestialMarkerMobEffect> EYE_CONDUIT = MOB_EFFECTS.register(
            "eye_conduit", () -> new CelestialMarkerMobEffect(MobEffectCategory.BENEFICIAL, CelestialVariant.EYE.tintColor()));

    public static final DeferredHolder<MobEffect, CelestialMarkerMobEffect> LUCK_AMULET = MOB_EFFECTS.register(
            "luck_amulet", () -> new CelestialMarkerMobEffect(MobEffectCategory.BENEFICIAL, CelestialVariant.LUCK.tintColor()));
    public static final DeferredHolder<MobEffect, CelestialMarkerMobEffect> LUCK_CONDUIT = MOB_EFFECTS.register(
            "luck_conduit", () -> new CelestialMarkerMobEffect(MobEffectCategory.BENEFICIAL, CelestialVariant.LUCK.tintColor()));

    public static final DeferredHolder<MobEffect, CelestialMarkerMobEffect> END_AMULET = MOB_EFFECTS.register(
            "end_amulet", () -> new CelestialMarkerMobEffect(MobEffectCategory.BENEFICIAL, CelestialVariant.END.tintColor()));
    public static final DeferredHolder<MobEffect, CelestialMarkerMobEffect> END_CONDUIT = MOB_EFFECTS.register(
            "end_conduit", () -> new CelestialMarkerMobEffect(MobEffectCategory.BENEFICIAL, CelestialVariant.END.tintColor()));

    // Per-variant lookups for CelestialEffects (CONDUIT_MARKERS has no BREATH entry).
    public static final Map<CelestialVariant, DeferredHolder<MobEffect, CelestialMarkerMobEffect>> AMULET_MARKERS = buildAmuletMarkers();
    public static final Map<CelestialVariant, DeferredHolder<MobEffect, CelestialMarkerMobEffect>> CONDUIT_MARKERS = buildConduitMarkers();

    private static Map<CelestialVariant, DeferredHolder<MobEffect, CelestialMarkerMobEffect>> buildAmuletMarkers() {
        Map<CelestialVariant, DeferredHolder<MobEffect, CelestialMarkerMobEffect>> map = new EnumMap<>(CelestialVariant.class);
        map.put(CelestialVariant.BREATH, BREATH_AMULET);
        map.put(CelestialVariant.LIFE, LIFE_AMULET);
        map.put(CelestialVariant.DEATH, DEATH_AMULET);
        map.put(CelestialVariant.SHIELD, SHIELD_AMULET);
        map.put(CelestialVariant.PEACE, PEACE_AMULET);
        map.put(CelestialVariant.EYE, EYE_AMULET);
        map.put(CelestialVariant.LUCK, LUCK_AMULET);
        map.put(CelestialVariant.END, END_AMULET);
        return map;
    }

    private static Map<CelestialVariant, DeferredHolder<MobEffect, CelestialMarkerMobEffect>> buildConduitMarkers() {
        Map<CelestialVariant, DeferredHolder<MobEffect, CelestialMarkerMobEffect>> map = new EnumMap<>(CelestialVariant.class);
        map.put(CelestialVariant.LIFE, LIFE_CONDUIT);
        map.put(CelestialVariant.DEATH, DEATH_CONDUIT);
        map.put(CelestialVariant.SHIELD, SHIELD_CONDUIT);
        map.put(CelestialVariant.PEACE, PEACE_CONDUIT);
        map.put(CelestialVariant.EYE, EYE_CONDUIT);
        map.put(CelestialVariant.LUCK, LUCK_CONDUIT);
        map.put(CelestialVariant.END, END_CONDUIT);
        return map;
    }

    public static void register(IEventBus modEventBus) {
        MOB_EFFECTS.register(modEventBus);
    }
}
