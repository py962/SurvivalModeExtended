package com.terrainlab.registry;

import com.mojang.serialization.MapCodec;
import com.terrainlab.TerrainLab;
import com.terrainlab.trade.MerchantPickCondition;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Custom loot condition types, used by the data-driven villager trades. */
public final class ModLootConditions {

    private ModLootConditions() {}

    private static final DeferredRegister<MapCodec<? extends LootItemCondition>> CONDITIONS =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, TerrainLab.MODID);

    /** {@code terrainlab:merchant_pick}, see {@link MerchantPickCondition}. */
    public static final DeferredHolder<MapCodec<? extends LootItemCondition>, MapCodec<MerchantPickCondition>> MERCHANT_PICK =
            CONDITIONS.register("merchant_pick", () -> MerchantPickCondition.MAP_CODEC);

    public static void register(IEventBus modEventBus) {
        CONDITIONS.register(modEventBus);
    }
}
