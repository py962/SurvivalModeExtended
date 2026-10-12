package com.terrainlab.registry;

import com.terrainlab.TerrainLab;
import com.terrainlab.enchantment.LavaSourcePredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicateType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers custom block predicate types (Registries.BLOCK_PREDICATE_TYPE is a
 * static, non-datapack registry).
 */
public final class ModBlockPredicateTypes {

    private ModBlockPredicateTypes() {}

    private static final DeferredRegister<BlockPredicateType<?>> BLOCK_PREDICATE_TYPES =
            DeferredRegister.create(Registries.BLOCK_PREDICATE_TYPE, TerrainLab.MODID);

    /** Backs data/minecraft/enchantment/frost_walker.json's lava->obsidian entry. */
    public static final DeferredHolder<BlockPredicateType<?>, BlockPredicateType<LavaSourcePredicate>> LAVA_SOURCE =
            BLOCK_PREDICATE_TYPES.register("lava_source", () -> () -> LavaSourcePredicate.CODEC);

    public static void register(IEventBus modEventBus) {
        BLOCK_PREDICATE_TYPES.register(modEventBus);
    }
}
