package com.terrainlab.registry;

import com.terrainlab.TerrainLab;
import com.terrainlab.block.AncientCodexBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.block.SoundType;
import com.terrainlab.block.VoidGlassBlock;
import com.terrainlab.block.EnderwoodBlock;
import com.terrainlab.block.ReplicatorBlock;
import com.terrainlab.block.MagnetiteOreBlock;
import com.terrainlab.block.EndervineBodyBlock;
import com.terrainlab.block.EndervineHeadBlock;
import com.terrainlab.block.SculkFlowerBlock;
import com.terrainlab.block.NightcapMushroomBlock;
import com.terrainlab.block.FletchingTableBlock;
import com.terrainlab.block.CelestialForgeBlock;
import com.terrainlab.block.CelestialConduitBlock;
import com.terrainlab.celestial.CelestialVariant;

import java.util.EnumMap;
import java.util.Map;

/** Block registrations. */
public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(TerrainLab.MODID);

    public static final DeferredBlock<Block> REPLICATOR =
            BLOCKS.register("replicator", id ->
                    new ReplicatorBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.BLOCK, id))
                                    .mapColor(MapColor.STONE)
                                    .strength(3.5F)
                                    .sound(SoundType.STONE)
                                    .requiresCorrectToolForDrops()));

    public static final DeferredBlock<Block> ENDERVINE_BODY =
            BLOCKS.register("endervine_body", id ->
                    new EndervineBodyBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.BLOCK, id))
                                    .mapColor(MapColor.COLOR_GREEN)
                                    .strength(0.2F)
                                    .sound(SoundType.VINE)
                                    .noOcclusion()
                    ));
    public static final DeferredBlock<VoidGlassBlock> VOID_GLASS =
            BLOCKS.register("void_glass", id ->
                    new VoidGlassBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.BLOCK, id))
                                    .mapColor(MapColor.COLOR_PURPLE)
                                    .strength(0.5F)
                                    .noOcclusion()));

    public static final DeferredBlock<Block> ENDERVINE_HEAD =
            BLOCKS.register("endervine_head", id ->
                    new EndervineHeadBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.BLOCK, id))
                                    .mapColor(MapColor.COLOR_GREEN)
                                    .strength(0.2F)
                                    .sound(SoundType.VINE)
                                    .noOcclusion()
                                    .randomTicks()));

    public static final DeferredBlock<FletchingTableBlock> FLETCHING_TABLE =
            BLOCKS.register("fletching_table", id ->
                    new FletchingTableBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.BLOCK, id))
                                    .mapColor(MapColor.WOOD)
                                    .strength(2.5F)
                                    .sound(SoundType.WOOD)));

    public static final DeferredBlock<Block> ENDERWOOD =
            BLOCKS.register("enderwood", id ->
                    new EnderwoodBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.BLOCK, id))
                                    .mapColor(MapColor.COLOR_BLACK)
                                    .strength(2.0F)
                                    .sound(SoundType.WOOD)));

    public static final DeferredBlock<Block> ANCIENT_CODEX =
            BLOCKS.register("ancient_codex", id ->
                    new AncientCodexBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.BLOCK, id))
                                    .mapColor(MapColor.STONE)
                                    .strength(-1.0F, 3600000.0F)
                                    .noLootTable()
                                    .pushReaction(PushReaction.IMMOVEABLE)));

    public static final DeferredBlock<Block> MAGNETITE_ORE =
            BLOCKS.register("magnetite_ore", id ->
                    new MagnetiteOreBlock(BlockBehaviour.Properties.of()
                            .setId(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.BLOCK, id))
                            .mapColor(net.minecraft.world.level.material.MapColor.DEEPSLATE)
                            .instrument(net.minecraft.world.level.block.state.properties.NoteBlockInstrument.BASEDRUM)
                            .requiresCorrectToolForDrops()
                            .strength(4.5F, 3.0F)
                            .sound(net.minecraft.world.level.block.SoundType.DEEPSLATE)
                            .lightLevel(state -> state.getValue(MagnetiteOreBlock.LIT) ? 9 : 0)
                            .randomTicks()));

    // Sculk flower: non-collidable, faintly glowing; survival rules in SculkFlowerBlock#canSurvive.
    public static final DeferredBlock<Block> SCULK_FLOWER =
            BLOCKS.register("sculk_flower", id ->
                    new SculkFlowerBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.BLOCK, id))
                                    .mapColor(MapColor.COLOR_BLACK)
                                    .noCollision()
                                    .strength(0.1F)
                                    .sound(SoundType.SCULK_VEIN)
                                    .lightLevel(state -> 6)
                                    .randomTicks()));

    // Nightcap mushroom: non-collidable, instabreak; needs mossy cobblestone below (see NightcapMushroomBlock#canSurvive).
    public static final DeferredBlock<Block> NIGHTCAP_MUSHROOM =
            BLOCKS.register("nightcap_mushroom", id ->
                    new NightcapMushroomBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.BLOCK, id))
                                    .mapColor(MapColor.SNOW)
                                    .noCollision()
                                    .instabreak()
                                    .sound(SoundType.GRASS)));

    public static final DeferredBlock<CelestialForgeBlock> CELESTIAL_FORGE =
            BLOCKS.register("celestial_forge", id ->
                    new CelestialForgeBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.BLOCK, id))
                                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                                    .strength(5.0F)
                                    .sound(SoundType.GLASS)
                                    .requiresCorrectToolForDrops()
                                    .lightLevel(state -> 5)));

    private static final Map<CelestialVariant, DeferredBlock<CelestialConduitBlock>> CELESTIAL_CONDUITS =
            new EnumMap<>(CelestialVariant.class);

    static {
        for (CelestialVariant variant : CelestialVariant.values()) {
            // Breath uses vanilla's conduit; no custom block.
            if (variant.usesVanillaConduit()) continue;
            CELESTIAL_CONDUITS.put(variant, BLOCKS.register(variant.id() + "_conduit", id ->
                    new CelestialConduitBlock(variant,
                            BlockBehaviour.Properties.of()
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.BLOCK, id))
                                    .mapColor(MapColor.COLOR_CYAN)
                                    .strength(3.0F)
                                    .sound(SoundType.AMETHYST)
                                    // Dim (6) until fully framed, then 15 (vanilla's conduit is a constant 15).
                                    .lightLevel(state -> state.getValue(CelestialConduitBlock.ACTIVE) ? 15 : 6)
                                    .noOcclusion())));
        }
    }

    /** Null for BREATH, which uses vanilla's conduit. */
    public static DeferredBlock<CelestialConduitBlock> conduitFor(CelestialVariant variant) {
        return CELESTIAL_CONDUITS.get(variant);
    }

    public static final DeferredBlock<com.terrainlab.block.FieldStoneBlock> FIELD_STONE =
            BLOCKS.register("field_stone", id ->
                    new com.terrainlab.block.FieldStoneBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.BLOCK, id))
                                    .mapColor(MapColor.STONE)
                                    .strength(10.0F, 1200.0F) // roughly obsidian-tier
                                    .requiresCorrectToolForDrops()
                                    .sound(SoundType.STONE)
                                    // Glows while directly powered (LIT, toggled by RedstonePortalHandler).
                                    .lightLevel(state -> state.getValue(com.terrainlab.block.FieldStoneBlock.LIT) ? 12 : 0)));

    // Placed and removed only by RedstonePortalShape, like vanilla portal blocks.
    public static final DeferredBlock<com.terrainlab.block.RedstonePortalBlock> REDSTONE_PORTAL =
            BLOCKS.register("redstone_portal", id ->
                    new com.terrainlab.block.RedstonePortalBlock(
                            BlockBehaviour.Properties.of()
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.BLOCK, id))
                                    .mapColor(MapColor.COLOR_PURPLE)
                                    .noCollision()
                                    // Unbreakable by hand; only the frame logic removes portal cells.
                                    .strength(-1.0F, 3600000.0F)
                                    // Thin cross-shaped model, so no occlusion (as NetherPortalBlock).
                                    .noOcclusion()
                                    .lightLevel(state -> 11)
                                    .sound(SoundType.GLASS)
                                    .noLootTable()
                                    .pushReaction(PushReaction.IMMOVEABLE)));

    // Sandstorm sand layers. Properties mirror vanilla's snow layer (replaceable, never solid so the
    // motion-blocking heightmap looks through them, shovel for drops).
    public static final DeferredBlock<com.terrainlab.block.SandLayerBlock> SAND_LAYER =
            BLOCKS.register("sand_layer", id ->
                    new com.terrainlab.block.SandLayerBlock(sandLayerProperties(id, MapColor.SAND)));

    public static final DeferredBlock<com.terrainlab.block.SandLayerBlock> RED_SAND_LAYER =
            BLOCKS.register("red_sand_layer", id ->
                    new com.terrainlab.block.SandLayerBlock(sandLayerProperties(id, MapColor.COLOR_ORANGE)));

    private static BlockBehaviour.Properties sandLayerProperties(
            net.minecraft.resources.Identifier id, MapColor color) {
        return BlockBehaviour.Properties.of()
                .setId(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.BLOCK, id))
                .mapColor(color)
                .replaceable()
                .forceSolidOff()
                .randomTicks()
                .strength(0.2F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.SAND)
                .isViewBlocking((state, level, pos, nearPlaneBox) ->
                        state.getValue(com.terrainlab.block.SandLayerBlock.LAYERS) >= 8)
                .pushReaction(PushReaction.POPPED);
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
