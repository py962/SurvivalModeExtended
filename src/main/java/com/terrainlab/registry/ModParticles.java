package com.terrainlab.registry;

import com.terrainlab.TerrainLab;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Particle types: villager comfort indicators (client/VillagerMoodClient; "trapped"
 * uses vanilla's ANGRY_VILLAGER) and sandstorm particles.
 */
public final class ModParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, TerrainLab.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VILLAGER_COLD =
            PARTICLE_TYPES.register("villager_cold", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VILLAGER_BREATH =
            PARTICLE_TYPES.register("villager_breath", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VILLAGER_HOT =
            PARTICLE_TYPES.register("villager_hot", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VILLAGER_FEAR =
            PARTICLE_TYPES.register("villager_fear", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VILLAGER_TIRED =
            PARTICLE_TYPES.register("villager_tired", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VILLAGER_HUNGRY =
            PARTICLE_TYPES.register("villager_hungry", () -> new SimpleParticleType(false));

    // Sandstorm grains and dust clouds, tinted per biome by SandstormParticle's provider.
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SANDSTORM_GRAIN =
            PARTICLE_TYPES.register("sandstorm_grain", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SANDSTORM_PUFF =
            PARTICLE_TYPES.register("sandstorm_puff", () -> new SimpleParticleType(false));

    private ModParticles() {
    }

    public static void register(IEventBus eventBus) {
        PARTICLE_TYPES.register(eventBus);
    }
}
