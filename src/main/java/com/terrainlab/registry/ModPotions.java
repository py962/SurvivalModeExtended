package com.terrainlab.registry;

import com.terrainlab.TerrainLab;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Potion registrations. Durations are in ticks. */
public class ModPotions {

    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, TerrainLab.MODID);

    public static final DeferredHolder<Potion, Potion> PHASING =
            POTIONS.register("phasing", () -> new Potion("phasing",
                    new MobEffectInstance(ModMobEffects.PHASING, 400))); // 20s

    public static final DeferredHolder<Potion, Potion> PHASE_BLOCKING =
            POTIONS.register("phase_blocking", () -> new Potion("phase_blocking",
                    new MobEffectInstance(ModMobEffects.PHASE_BLOCKING, 400)));

    public static final DeferredHolder<Potion, Potion> GRIP =
            POTIONS.register("grip", () -> new Potion("grip",
                    new MobEffectInstance(ModMobEffects.GRIP, 3600))); // 3 min, vanilla base duration

    public static final DeferredHolder<Potion, Potion> SLIPPERY =
            POTIONS.register("slippery", () -> new Potion("slippery",
                    new MobEffectInstance(ModMobEffects.SLIPPERY, 3600)));

    // Shorter than the usual 3 min: a borrow session is passive and cannot be ended early.
    public static final DeferredHolder<Potion, Potion> BORROWING =
            POTIONS.register("borrowing", () -> new Potion("borrowing",
                    new MobEffectInstance(ModMobEffects.BORROWING, 1200))); // 60s

    // Instant; Glowstone amplifies the blast instead of extending duration.
    public static final DeferredHolder<Potion, Potion> EXPLOSIVE =
            POTIONS.register("explosive", () -> new Potion("explosive",
                    new MobEffectInstance(ModMobEffects.EXPLOSIVE, 1)));

    public static final DeferredHolder<Potion, Potion> WARMTH =
            POTIONS.register("warmth", () -> new Potion("warmth",
                    new MobEffectInstance(ModMobEffects.WARMTH, 3600)));

    public static final DeferredHolder<Potion, Potion> FREEZING =
            POTIONS.register("freezing", () -> new Potion("freezing",
                    new MobEffectInstance(ModMobEffects.FREEZING, 3600)));

    public static final DeferredHolder<Potion, Potion> SPECTRAL_VISION =
            POTIONS.register("spectral_vision", () -> new Potion("spectral_vision",
                    new MobEffectInstance(ModMobEffects.SPECTRAL_VISION, 3600)));

    // Long variants (Redstone, see ModBrewingRecipes). They reuse the base potion name, and so its
    // translation key, like vanilla's long_* potions. 3-minute potions go to 8 minutes, shorter ones
    // double. Explosive has no long variant.

    public static final DeferredHolder<Potion, Potion> LONG_PHASING =
            POTIONS.register("long_phasing", () -> new Potion("phasing",
                    new MobEffectInstance(ModMobEffects.PHASING, 800))); // 40s

    public static final DeferredHolder<Potion, Potion> LONG_PHASE_BLOCKING =
            POTIONS.register("long_phase_blocking", () -> new Potion("phase_blocking",
                    new MobEffectInstance(ModMobEffects.PHASE_BLOCKING, 800))); // 40s

    public static final DeferredHolder<Potion, Potion> LONG_GRIP =
            POTIONS.register("long_grip", () -> new Potion("grip",
                    new MobEffectInstance(ModMobEffects.GRIP, 9600))); // 8min

    public static final DeferredHolder<Potion, Potion> LONG_SLIPPERY =
            POTIONS.register("long_slippery", () -> new Potion("slippery",
                    new MobEffectInstance(ModMobEffects.SLIPPERY, 9600))); // 8min

    public static final DeferredHolder<Potion, Potion> LONG_BORROWING =
            POTIONS.register("long_borrowing", () -> new Potion("borrowing",
                    new MobEffectInstance(ModMobEffects.BORROWING, 2400))); // 2min

    public static final DeferredHolder<Potion, Potion> LONG_WARMTH =
            POTIONS.register("long_warmth", () -> new Potion("warmth",
                    new MobEffectInstance(ModMobEffects.WARMTH, 9600))); // 8min

    public static final DeferredHolder<Potion, Potion> LONG_FREEZING =
            POTIONS.register("long_freezing", () -> new Potion("freezing",
                    new MobEffectInstance(ModMobEffects.FREEZING, 9600))); // 8min

    public static final DeferredHolder<Potion, Potion> LONG_SPECTRAL_VISION =
            POTIONS.register("long_spectral_vision", () -> new Potion("spectral_vision",
                    new MobEffectInstance(ModMobEffects.SPECTRAL_VISION, 9600))); // 8min

    // Glowstone: level II, blast radius 9 instead of 6 (ExplosiveMobEffect: 6 + 3 per amplifier).
    public static final DeferredHolder<Potion, Potion> STRONG_EXPLOSIVE =
            POTIONS.register("strong_explosive", () -> new Potion("explosive",
                    new MobEffectInstance(ModMobEffects.EXPLOSIVE, 1, 1)));

    // Blindness and Nausea are deliberately not potions: they come from suspicious stews, and the
    // Fletcher's tipped arrows carry them directly (see FletcherTrades).

    public static void register(IEventBus modEventBus) {
        POTIONS.register(modEventBus);
    }
}
