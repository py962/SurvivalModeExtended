        package com.terrainlab.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.terrainlab.TerrainLab;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** Custom enchantment effect component types (marker effects referenced from enchantment JSON). */
public final class ModEnchantmentEffects {

    private ModEnchantmentEffects() {}

    private static final DeferredRegister.DataComponents EFFECT_COMPONENTS =
            DeferredRegister.createDataComponents(
                    Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE,
                    TerrainLab.MODID
            );

    /** Marker effect for Overstacking. */
    public record Overstacking() {
        public static final MapCodec<Overstacking> CODEC =
                MapCodec.unit(new Overstacking());
    }

    /** Marker effect for Recall. */
    public record Recall() {
        public static final MapCodec<Recall> CODEC =
                MapCodec.unit(new Recall());
    }

    public static final Supplier<DataComponentType<Overstacking>> OVERSTACKING =
            EFFECT_COMPONENTS.registerComponentType(
                    "overstacking",
                    builder -> builder.persistent(Overstacking.CODEC.codec())
            );

    public static final Supplier<DataComponentType<Recall>> RECALL =
            EFFECT_COMPONENTS.registerComponentType(
                    "recall",
                    builder -> builder.persistent(Recall.CODEC.codec())
            );

    public static void register(IEventBus modEventBus) {
        EFFECT_COMPONENTS.register(modEventBus);
    }
}
