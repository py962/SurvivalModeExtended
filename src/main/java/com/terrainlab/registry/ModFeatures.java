package com.terrainlab.registry;

import com.terrainlab.TerrainLab;
import com.terrainlab.worldgen.VoidGlassFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
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
import com.terrainlab.worldgen.SuspiciousSandPatchFeature;
import net.minecraft.world.level.levelgen.feature.configurations.UnderwaterMagmaConfiguration;
/** Worldgen feature registrations. */
public class ModFeatures {

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, TerrainLab.MODID);

    public static final DeferredHolder<Feature<?>, SlimeMossPatchFeature> SLIME_MOSS_PATCH =
            FEATURES.register("slime_moss_patch",
                    () -> new SlimeMossPatchFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, LargeSpringFeature> LARGE_SPRING =
            FEATURES.register("large_spring",
                    () -> new LargeSpringFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, EndervineFeature> ENDERVINE =
            FEATURES.register("endervine",
                    () -> new EndervineFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, VoidGlassFeature> VOID_DUST =
            FEATURES.register("void_glass",
                    () -> new VoidGlassFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, ObsidianIslandFeature> OBSIDIAN_ISLAND =
            FEATURES.register("obsidian_island",
                    () -> new ObsidianIslandFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, SwampFloodFeature> SWAMP_FLOOD =
            FEATURES.register("swamp_flood",
                    () -> new SwampFloodFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, InlandRiverFloodFeature> INLAND_RIVER_FLOOD =
            FEATURES.register("inland_river_flood",
                    () -> new InlandRiverFloodFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, MagnetiteOreFeature> magnetite_ORE =
            FEATURES.register("magnetite_ore",
                    () -> new MagnetiteOreFeature(UnderwaterMagmaConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, SeaLevelBarrierGapFeature> SEA_LEVEL_BARRIER_GAP =
            FEATURES.register("sea_level_barrier_gap",
                    () -> new SeaLevelBarrierGapFeature(NoneFeatureConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, SuspiciousSandPatchFeature> SUSPICIOUS_SAND_PATCH =
            FEATURES.register("suspicious_sand_patch",
                    () -> new SuspiciousSandPatchFeature(NoneFeatureConfiguration.CODEC));

    // Tree placers backported from 26.3's poplar (see worldgen.poplar).
    public static final DeferredRegister<net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType<?>> TRUNK_PLACERS =
            DeferredRegister.create(Registries.TRUNK_PLACER_TYPE, TerrainLab.MODID);
    public static final DeferredRegister<net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType<?>> FOLIAGE_PLACERS =
            DeferredRegister.create(Registries.FOLIAGE_PLACER_TYPE, TerrainLab.MODID);
    public static final DeferredHolder<net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType<?>,
            net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType<com.terrainlab.worldgen.poplar.PoplarTrunkPlacer>> POPLAR_TRUNK_PLACER =
            TRUNK_PLACERS.register("poplar_trunk_placer",
                    () -> new net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType<>(com.terrainlab.worldgen.poplar.PoplarTrunkPlacer.CODEC));
    public static final DeferredHolder<net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType<?>,
            net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType<com.terrainlab.worldgen.poplar.PoplarFoliagePlacer>> POPLAR_FOLIAGE_PLACER =
            FOLIAGE_PLACERS.register("poplar_foliage_placer",
                    () -> new net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType<>(com.terrainlab.worldgen.poplar.PoplarFoliagePlacer.CODEC));

    public static void register(IEventBus eventBus) {
        FEATURES.register(eventBus);
        TRUNK_PLACERS.register(eventBus);
        FOLIAGE_PLACERS.register(eventBus);
    }
}
