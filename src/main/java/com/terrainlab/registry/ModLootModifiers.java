package com.terrainlab.registry;

import com.mojang.serialization.MapCodec;
import com.terrainlab.TerrainLab;
import com.terrainlab.season.plants.SeasonalAppleLootModifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Global loot modifier codecs; the modifiers themselves live in data/terrainlab/loot_modifiers. */
public final class ModLootModifiers {

    private ModLootModifiers() {}

    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, TerrainLab.MODID);

    /** {@code terrainlab:seasonal_apples}, see {@link SeasonalAppleLootModifier}. */
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<SeasonalAppleLootModifier>> SEASONAL_APPLES =
            SERIALIZERS.register("seasonal_apples", () -> SeasonalAppleLootModifier.CODEC);

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
