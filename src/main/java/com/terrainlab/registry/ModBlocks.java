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
import com.terrainlab.block.ModWoodTypes;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.WoodType;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

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

    // ------------------------------------------------------------------------------------------
    // Wood sets: Pine, Azalea and Enderwood. Properties mirror vanilla's spruce set (Blocks.java).
    // Enderwood has no logs: the existing ENDERWOOD block is its planks, and like the nether
    // woods the set is not flammable. Strippables, flammability, flower pot and sign block
    // entity hookups live in event.WoodSetEvents; client registration in client.WoodSetsClient.
    // ------------------------------------------------------------------------------------------

    // Pine (pale sage green)
    public static final DeferredBlock<RotatedPillarBlock> PINE_LOG = log("pine_log", MapColor.GLOW_LICHEN, MapColor.TERRACOTTA_GREEN);
    public static final DeferredBlock<RotatedPillarBlock> PINE_WOOD = wood("pine_wood", MapColor.TERRACOTTA_GREEN);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_PINE_LOG = log("stripped_pine_log", MapColor.GLOW_LICHEN, MapColor.GLOW_LICHEN);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_PINE_WOOD = wood("stripped_pine_wood", MapColor.GLOW_LICHEN);
    public static final DeferredBlock<Block> PINE_PLANKS = planks("pine_planks", MapColor.GLOW_LICHEN);
    public static final DeferredBlock<StairBlock> PINE_STAIRS = stairs("pine_stairs", PINE_PLANKS);
    public static final DeferredBlock<SlabBlock> PINE_SLAB = slab("pine_slab", MapColor.GLOW_LICHEN, true);
    public static final DeferredBlock<FenceBlock> PINE_FENCE = fence("pine_fence", MapColor.GLOW_LICHEN, true);
    public static final DeferredBlock<FenceGateBlock> PINE_FENCE_GATE = fenceGate("pine_fence_gate", ModWoodTypes.PINE, MapColor.GLOW_LICHEN, true);
    public static final DeferredBlock<DoorBlock> PINE_DOOR = door("pine_door", ModWoodTypes.PINE_SET, MapColor.GLOW_LICHEN, true);
    public static final DeferredBlock<TrapDoorBlock> PINE_TRAPDOOR = trapdoor("pine_trapdoor", ModWoodTypes.PINE_SET, MapColor.GLOW_LICHEN, true);
    public static final DeferredBlock<ButtonBlock> PINE_BUTTON = button("pine_button", ModWoodTypes.PINE_SET);
    public static final DeferredBlock<PressurePlateBlock> PINE_PRESSURE_PLATE = pressurePlate("pine_pressure_plate", ModWoodTypes.PINE_SET, MapColor.GLOW_LICHEN, true);
    public static final DeferredBlock<StandingSignBlock> PINE_SIGN = sign("pine_sign", ModWoodTypes.PINE, MapColor.GLOW_LICHEN, true);
    public static final DeferredBlock<WallSignBlock> PINE_WALL_SIGN = wallSign("pine_wall_sign", ModWoodTypes.PINE, PINE_SIGN, MapColor.GLOW_LICHEN, true);
    public static final DeferredBlock<CeilingHangingSignBlock> PINE_HANGING_SIGN = hangingSign("pine_hanging_sign", ModWoodTypes.PINE, MapColor.GLOW_LICHEN, true);
    public static final DeferredBlock<WallHangingSignBlock> PINE_WALL_HANGING_SIGN = wallHangingSign("pine_wall_hanging_sign", ModWoodTypes.PINE, PINE_HANGING_SIGN, MapColor.GLOW_LICHEN, true);
    // Sage colour is baked into the leaf texture (no block colour handler); particles match it.
    public static final DeferredBlock<UntintedParticleLeavesBlock> PINE_LEAVES =
            BLOCKS.registerBlock("pine_leaves",
                    p -> new UntintedParticleLeavesBlock(0.01F,
                            ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, 0xFF768E68), p),
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.PLANT)
                            .strength(0.2F)
                            .randomTicks()
                            .sound(SoundType.GRASS)
                            .noOcclusion()
                            .isValidSpawn(Blocks::ocelotOrParrot)
                            .isSuffocating((state, level, pos) -> false)
                            .isViewBlocking((state, level, pos) -> false)
                            .ignitedByLava()
                            .pushReaction(PushReaction.DESTROY)
                            .isRedstoneConductor((state, level, pos) -> false));
    public static final DeferredBlock<SaplingBlock> PINE_SAPLING =
            BLOCKS.registerBlock("pine_sapling",
                    p -> new SaplingBlock(ModWoodTypes.PINE_GROWER, p),
                    () -> BlockBehaviour.Properties.of()
                            .mapColor(MapColor.PLANT)
                            .noCollision()
                            .randomTicks()
                            .instabreak()
                            .sound(SoundType.GRASS)
                            .pushReaction(PushReaction.DESTROY));
    // Registered on the vanilla empty pot in WoodSetEvents (common setup).
    public static final DeferredBlock<FlowerPotBlock> POTTED_PINE_SAPLING =
            BLOCKS.registerBlock("potted_pine_sapling",
                    p -> new FlowerPotBlock(() -> (FlowerPotBlock) Blocks.FLOWER_POT, PINE_SAPLING, p),
                    () -> BlockBehaviour.Properties.of()
                            .instabreak()
                            .noOcclusion()
                            .pushReaction(PushReaction.DESTROY));

    // Azalea (pale blue-slate mauve); grown by vanilla's azalea_tree feature (data override).
    public static final DeferredBlock<RotatedPillarBlock> AZALEA_LOG = log("azalea_log", MapColor.TERRACOTTA_LIGHT_BLUE, MapColor.TERRACOTTA_BLUE);
    public static final DeferredBlock<RotatedPillarBlock> AZALEA_WOOD = wood("azalea_wood", MapColor.TERRACOTTA_BLUE);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_AZALEA_LOG = log("stripped_azalea_log", MapColor.TERRACOTTA_LIGHT_BLUE, MapColor.TERRACOTTA_LIGHT_BLUE);
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_AZALEA_WOOD = wood("stripped_azalea_wood", MapColor.TERRACOTTA_LIGHT_BLUE);
    public static final DeferredBlock<Block> AZALEA_PLANKS = planks("azalea_planks", MapColor.TERRACOTTA_LIGHT_BLUE);
    public static final DeferredBlock<StairBlock> AZALEA_STAIRS = stairs("azalea_stairs", AZALEA_PLANKS);
    public static final DeferredBlock<SlabBlock> AZALEA_SLAB = slab("azalea_slab", MapColor.TERRACOTTA_LIGHT_BLUE, true);
    public static final DeferredBlock<FenceBlock> AZALEA_FENCE = fence("azalea_fence", MapColor.TERRACOTTA_LIGHT_BLUE, true);
    public static final DeferredBlock<FenceGateBlock> AZALEA_FENCE_GATE = fenceGate("azalea_fence_gate", ModWoodTypes.AZALEA, MapColor.TERRACOTTA_LIGHT_BLUE, true);
    public static final DeferredBlock<DoorBlock> AZALEA_DOOR = door("azalea_door", ModWoodTypes.AZALEA_SET, MapColor.TERRACOTTA_LIGHT_BLUE, true);
    public static final DeferredBlock<TrapDoorBlock> AZALEA_TRAPDOOR = trapdoor("azalea_trapdoor", ModWoodTypes.AZALEA_SET, MapColor.TERRACOTTA_LIGHT_BLUE, true);
    public static final DeferredBlock<ButtonBlock> AZALEA_BUTTON = button("azalea_button", ModWoodTypes.AZALEA_SET);
    public static final DeferredBlock<PressurePlateBlock> AZALEA_PRESSURE_PLATE = pressurePlate("azalea_pressure_plate", ModWoodTypes.AZALEA_SET, MapColor.TERRACOTTA_LIGHT_BLUE, true);
    public static final DeferredBlock<StandingSignBlock> AZALEA_SIGN = sign("azalea_sign", ModWoodTypes.AZALEA, MapColor.TERRACOTTA_LIGHT_BLUE, true);
    public static final DeferredBlock<WallSignBlock> AZALEA_WALL_SIGN = wallSign("azalea_wall_sign", ModWoodTypes.AZALEA, AZALEA_SIGN, MapColor.TERRACOTTA_LIGHT_BLUE, true);
    public static final DeferredBlock<CeilingHangingSignBlock> AZALEA_HANGING_SIGN = hangingSign("azalea_hanging_sign", ModWoodTypes.AZALEA, MapColor.TERRACOTTA_LIGHT_BLUE, true);
    public static final DeferredBlock<WallHangingSignBlock> AZALEA_WALL_HANGING_SIGN = wallHangingSign("azalea_wall_hanging_sign", ModWoodTypes.AZALEA, AZALEA_HANGING_SIGN, MapColor.TERRACOTTA_LIGHT_BLUE, true);

    // Enderwood (near-black); ENDERWOOD above is the planks.
    public static final DeferredBlock<StairBlock> ENDERWOOD_STAIRS = stairs("enderwood_stairs", ENDERWOOD);
    public static final DeferredBlock<SlabBlock> ENDERWOOD_SLAB = slab("enderwood_slab", MapColor.COLOR_BLACK, false);
    public static final DeferredBlock<FenceBlock> ENDERWOOD_FENCE = fence("enderwood_fence", MapColor.COLOR_BLACK, false);
    public static final DeferredBlock<FenceGateBlock> ENDERWOOD_FENCE_GATE = fenceGate("enderwood_fence_gate", ModWoodTypes.ENDERWOOD, MapColor.COLOR_BLACK, false);
    public static final DeferredBlock<DoorBlock> ENDERWOOD_DOOR = door("enderwood_door", ModWoodTypes.ENDERWOOD_SET, MapColor.COLOR_BLACK, false);
    public static final DeferredBlock<TrapDoorBlock> ENDERWOOD_TRAPDOOR = trapdoor("enderwood_trapdoor", ModWoodTypes.ENDERWOOD_SET, MapColor.COLOR_BLACK, false);
    public static final DeferredBlock<ButtonBlock> ENDERWOOD_BUTTON = button("enderwood_button", ModWoodTypes.ENDERWOOD_SET);
    public static final DeferredBlock<PressurePlateBlock> ENDERWOOD_PRESSURE_PLATE = pressurePlate("enderwood_pressure_plate", ModWoodTypes.ENDERWOOD_SET, MapColor.COLOR_BLACK, false);
    public static final DeferredBlock<StandingSignBlock> ENDERWOOD_SIGN = sign("enderwood_sign", ModWoodTypes.ENDERWOOD, MapColor.COLOR_BLACK, false);
    public static final DeferredBlock<WallSignBlock> ENDERWOOD_WALL_SIGN = wallSign("enderwood_wall_sign", ModWoodTypes.ENDERWOOD, ENDERWOOD_SIGN, MapColor.COLOR_BLACK, false);
    public static final DeferredBlock<CeilingHangingSignBlock> ENDERWOOD_HANGING_SIGN = hangingSign("enderwood_hanging_sign", ModWoodTypes.ENDERWOOD, MapColor.COLOR_BLACK, false);
    public static final DeferredBlock<WallHangingSignBlock> ENDERWOOD_WALL_HANGING_SIGN = wallHangingSign("enderwood_wall_hanging_sign", ModWoodTypes.ENDERWOOD, ENDERWOOD_HANGING_SIGN, MapColor.COLOR_BLACK, false);

    private static BlockBehaviour.Properties woodBase(MapColor color, boolean flammable) {
        BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                .mapColor(color)
                .instrument(NoteBlockInstrument.BASS);
        return flammable ? props.ignitedByLava() : props;
    }

    private static DeferredBlock<RotatedPillarBlock> log(String name, MapColor top, MapColor side) {
        return BLOCKS.registerBlock(name, RotatedPillarBlock::new, () -> BlockBehaviour.Properties.of()
                .mapColor(state -> state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y ? top : side)
                .instrument(NoteBlockInstrument.BASS)
                .strength(2.0F)
                .sound(SoundType.WOOD)
                .ignitedByLava());
    }

    private static DeferredBlock<RotatedPillarBlock> wood(String name, MapColor color) {
        return BLOCKS.registerBlock(name, RotatedPillarBlock::new,
                () -> woodBase(color, true).strength(2.0F).sound(SoundType.WOOD));
    }

    private static DeferredBlock<Block> planks(String name, MapColor color) {
        return BLOCKS.registerBlock(name, Block::new,
                () -> woodBase(color, true).strength(2.0F, 3.0F).sound(SoundType.WOOD));
    }

    private static DeferredBlock<StairBlock> stairs(String name, Supplier<? extends Block> base) {
        return BLOCKS.registerBlock(name, p -> new StairBlock(base.get().defaultBlockState(), p),
                () -> BlockBehaviour.Properties.ofLegacyCopy(base.get()));
    }

    private static DeferredBlock<SlabBlock> slab(String name, MapColor color, boolean flammable) {
        return BLOCKS.registerBlock(name, SlabBlock::new,
                () -> woodBase(color, flammable).strength(2.0F, 3.0F).sound(SoundType.WOOD));
    }

    private static DeferredBlock<FenceBlock> fence(String name, MapColor color, boolean flammable) {
        return BLOCKS.registerBlock(name, FenceBlock::new,
                () -> woodBase(color, flammable).strength(2.0F, 3.0F).sound(SoundType.WOOD));
    }

    private static DeferredBlock<FenceGateBlock> fenceGate(String name, WoodType type, MapColor color, boolean flammable) {
        return BLOCKS.registerBlock(name, p -> new FenceGateBlock(type, p),
                () -> woodBase(color, flammable).forceSolidOn().strength(2.0F, 3.0F));
    }

    private static DeferredBlock<DoorBlock> door(String name, BlockSetType set, MapColor color, boolean flammable) {
        return BLOCKS.registerBlock(name, p -> new DoorBlock(set, p),
                () -> woodBase(color, flammable).strength(3.0F).noOcclusion().pushReaction(PushReaction.DESTROY));
    }

    private static DeferredBlock<TrapDoorBlock> trapdoor(String name, BlockSetType set, MapColor color, boolean flammable) {
        return BLOCKS.registerBlock(name, p -> new TrapDoorBlock(set, p),
                () -> woodBase(color, flammable).strength(3.0F).noOcclusion()
                        .isValidSpawn((state, level, pos, entityType) -> false));
    }

    private static DeferredBlock<ButtonBlock> button(String name, BlockSetType set) {
        return BLOCKS.registerBlock(name, p -> new ButtonBlock(set, 30, p),
                () -> BlockBehaviour.Properties.of().noCollision().strength(0.5F).pushReaction(PushReaction.DESTROY));
    }

    private static DeferredBlock<PressurePlateBlock> pressurePlate(String name, BlockSetType set, MapColor color, boolean flammable) {
        return BLOCKS.registerBlock(name, p -> new PressurePlateBlock(set, p),
                () -> woodBase(color, flammable).forceSolidOn().noCollision().strength(0.5F)
                        .pushReaction(PushReaction.DESTROY));
    }

    private static DeferredBlock<StandingSignBlock> sign(String name, WoodType type, MapColor color, boolean flammable) {
        return BLOCKS.registerBlock(name, p -> new StandingSignBlock(type, p),
                () -> woodBase(color, flammable).forceSolidOn().noCollision().strength(1.0F));
    }

    // Wall variants drop and are named as their standing sign (vanilla's wallVariant).
    private static DeferredBlock<WallSignBlock> wallSign(String name, WoodType type,
            Supplier<? extends Block> standing, MapColor color, boolean flammable) {
        return BLOCKS.registerBlock(name, p -> new WallSignBlock(type, p),
                () -> woodBase(color, flammable)
                        .overrideLootTable(standing.get().getLootTable())
                        .overrideDescription(standing.get().getDescriptionId())
                        .forceSolidOn().noCollision().strength(1.0F));
    }

    private static DeferredBlock<CeilingHangingSignBlock> hangingSign(String name, WoodType type, MapColor color, boolean flammable) {
        return BLOCKS.registerBlock(name, p -> new CeilingHangingSignBlock(type, p),
                () -> woodBase(color, flammable).forceSolidOn().noCollision().strength(1.0F));
    }

    private static DeferredBlock<WallHangingSignBlock> wallHangingSign(String name, WoodType type,
            Supplier<? extends Block> ceiling, MapColor color, boolean flammable) {
        return BLOCKS.registerBlock(name, p -> new WallHangingSignBlock(type, p),
                () -> woodBase(color, flammable)
                        .overrideLootTable(ceiling.get().getLootTable())
                        .overrideDescription(ceiling.get().getDescriptionId())
                        .forceSolidOn().noCollision().strength(1.0F));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
