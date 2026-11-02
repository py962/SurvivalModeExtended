package com.terrainlab.forestfloor;

import com.mojang.serialization.Codec;
import com.terrainlab.TerrainLab;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Registration for the forest-floor feature (fallen leaves, baseline mushrooms and flowers). */
public final class ForestFloor {
    private ForestFloor() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TerrainLab.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, TerrainLab.MODID);

    public static final DeferredBlock<FallenLeavesBlock> FALLEN_LEAVES = BLOCKS.registerBlock("fallen_leaves",
            FallenLeavesBlock::new, props -> props
                    .mapColor(MapColor.COLOR_ORANGE)
                    .replaceable()
                    .noCollision()
                    .noOcclusion()
                    .instabreak()
                    .sound(SoundType.LEAF_LITTER)
                    .pushReaction(PushReaction.DESTROY)
                    .noLootTable());

    /** Season-run key of the last time baseline mushrooms/flowers were added to a chunk. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Long>> EXTRAS_RUN = ATTACHMENTS.register(
            "forest_floor_extras_run",
            () -> AttachmentType.builder(() -> -1L).serialize(Codec.LONG.fieldOf("run")).build());

    /** Pumpkins placed for the autumn in temperate biomes, removed again at midwinter. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<java.util.List<Long>>> AUTUMN_PUMPKINS = ATTACHMENTS.register(
            "autumn_pumpkins",
            () -> AttachmentType.<java.util.List<Long>>builder(() -> java.util.List.of())
                    .serialize(Codec.LONG.listOf().fieldOf("positions")).build());

    /** Spring carpet flowers this chunk placed, so only those are ever removed again. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<java.util.List<Long>>> SPRING_CARPET = ATTACHMENTS.register(
            "spring_carpet",
            () -> AttachmentType.<java.util.List<Long>>builder(() -> java.util.List.of())
                    .serialize(Codec.LONG.listOf().fieldOf("positions")).build());

    /** Spring flower carpet: forests at 60% of open grass, plains/meadow/marsh at 40%. */
    public static final TagKey<Biome> SPRING_CARPET_DENSE =
            TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(TerrainLab.MODID, "spring_carpet_dense"));
    public static final TagKey<Biome> SPRING_CARPET_BIOMES =
            TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(TerrainLab.MODID, "spring_carpet"));
    public static final ResourceKey<Biome> MARSH =
            ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(TerrainLab.MODID, "marsh"));

    public static final TagKey<Biome> AUTUMN_PUMPKIN_BIOMES =
            TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(TerrainLab.MODID, "autumn_pumpkins"));
    public static final TagKey<Biome> SEASONAL_OAKS =
            TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(TerrainLab.MODID, "seasonal_oaks"));
    public static final TagKey<Biome> FOREST_FLOOR_EXTRAS =
            TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(TerrainLab.MODID, "forest_floor_extras"));

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ATTACHMENTS.register(modEventBus);
        NeoForge.EVENT_BUS.register(ForestFloorService.class);
    }
}
