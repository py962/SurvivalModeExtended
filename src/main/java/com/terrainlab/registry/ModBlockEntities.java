package com.terrainlab.registry;

import com.terrainlab.TerrainLab;
import com.terrainlab.blockentity.AncientCodexBlockEntity;
import com.terrainlab.blockentity.ReplicatorBlockEntity;
import com.terrainlab.blockentity.CelestialForgeBlockEntity;
import com.terrainlab.blockentity.CelestialConduitBlockEntity;
import com.terrainlab.celestial.CelestialVariant;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.terrainlab.blockentity.FletchingTableBlockEntity;

/** Block entity type registrations. */
public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TerrainLab.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FletchingTableBlockEntity>> FLETCHING_TABLE =
            BLOCK_ENTITIES.register("fletching_table", () ->
                    new BlockEntityType<>(
                            FletchingTableBlockEntity::new,
                            false,
                            ModBlocks.FLETCHING_TABLE.get()
                    ));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReplicatorBlockEntity>> REPLICATOR_BE =
            BLOCK_ENTITIES.register("replicator", () ->
                    new BlockEntityType<>(
                            ReplicatorBlockEntity::new,
                            false,
                            ModBlocks.REPLICATOR.get()
                    ));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AncientCodexBlockEntity>> ANCIENT_CODEX_BE =
            BLOCK_ENTITIES.register("ancient_codex", () ->
                    new BlockEntityType<>(
                            AncientCodexBlockEntity::new,
                            false,
                            ModBlocks.ANCIENT_CODEX.get()
                    ));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CelestialForgeBlockEntity>> CELESTIAL_FORGE_BE =
            BLOCK_ENTITIES.register("celestial_forge", () ->
                    new BlockEntityType<>(
                            CelestialForgeBlockEntity::new,
                            false,
                            ModBlocks.CELESTIAL_FORGE.get()
                    ));

    // One shared type for all custom conduit blocks (Breath uses vanilla's conduit);
    // the block entity derives its variant from the block.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CelestialConduitBlockEntity>> CELESTIAL_CONDUIT_BE =
            BLOCK_ENTITIES.register("celestial_conduit", () ->
                    new BlockEntityType<>(
                            CelestialConduitBlockEntity::new,
                            false,
                            conduitBlocksArray()
                    ));

    private static Block[] conduitBlocksArray() {
        return java.util.Arrays.stream(CelestialVariant.values())
                .filter(variant -> !variant.usesVanillaConduit())
                .map(variant -> (Block) ModBlocks.conduitFor(variant).get())
                .toArray(Block[]::new);
    }

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
