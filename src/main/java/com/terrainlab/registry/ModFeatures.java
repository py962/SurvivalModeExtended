package com.terrainlab.registry;

import com.mojang.serialization.MapCodec;
import com.terrainlab.TerrainLab;
import com.terrainlab.worldgen.VoidGlassFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.terrainlab.worldgen.EndervineFeature;
import com.terrainlab.worldgen.ObsidianIslandFeature;
import com.terrainlab.worldgen.SwampFloodFeature;
import com.terrainlab.worldgen.SlimeMossPatchFeature;
import com.terrainlab.worldgen.LargeSpringFeature;
import com.terrainlab.worldgen.InlandRiverFloodFeature;
import com.terrainlab.worldgen.MagnetiteOreFeature;
import com.terrainlab.worldgen.SeaLevelBarrierGapFeature;

/**
 * Worldgen feature type registrations. Since 26.x a feature "type" is just the codec of a
 * {@link Feature} implementation, registered in {@link Registries#FEATURE_TYPE}; the configured
 * instances live in data/terrainlab/worldgen/feature.
 */
public class ModFeatures {

    public static final DeferredRegister<MapCodec<? extends Feature>> FEATURES =
            DeferredRegister.create(Registries.FEATURE_TYPE, TerrainLab.MODID);

    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<SlimeMossPatchFeature>> SLIME_MOSS_PATCH =
            FEATURES.register("slime_moss_patch", () -> SlimeMossPatchFeature.CODEC);

    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<LargeSpringFeature>> LARGE_SPRING =
            FEATURES.register("large_spring", () -> LargeSpringFeature.CODEC);

    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<EndervineFeature>> ENDERVINE =
            FEATURES.register("endervine", () -> EndervineFeature.CODEC);

    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<VoidGlassFeature>> VOID_DUST =
            FEATURES.register("void_glass", () -> VoidGlassFeature.CODEC);

    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<ObsidianIslandFeature>> OBSIDIAN_ISLAND =
            FEATURES.register("obsidian_island", () -> ObsidianIslandFeature.CODEC);

    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<SwampFloodFeature>> SWAMP_FLOOD =
            FEATURES.register("swamp_flood", () -> SwampFloodFeature.CODEC);

    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<InlandRiverFloodFeature>> INLAND_RIVER_FLOOD =
            FEATURES.register("inland_river_flood", () -> InlandRiverFloodFeature.CODEC);

    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<MagnetiteOreFeature>> magnetite_ORE =
            FEATURES.register("magnetite_ore", () -> MagnetiteOreFeature.CODEC);

    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<SeaLevelBarrierGapFeature>> SEA_LEVEL_BARRIER_GAP =
            FEATURES.register("sea_level_barrier_gap", () -> SeaLevelBarrierGapFeature.CODEC);

    public static void register(IEventBus eventBus) {
        FEATURES.register(eventBus);
    }
}
