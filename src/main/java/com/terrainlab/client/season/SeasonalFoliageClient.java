package com.terrainlab.client.season;

import com.terrainlab.TerrainLab;
import com.terrainlab.client.season.SeasonalLeaves.Look;
import com.terrainlab.client.season.SeasonalLeaves.Tree;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * Registration for seasonal leaf colours (client only; no world or block-state changes):
 * <ul>
 *   <li>a biome-mask {@link net.minecraft.world.level.ColorResolver} so meshing threads can ask "seasonal biome?";</li>
 *   <li>tint sources: oak turns russet (wrapping vanilla's foliage tint), birch tint follows its look for
 *       falling-leaf / break particles, poplar gets a fixed fresh green for its green variant;</li>
 *   <li>texture variants (standalone models) swapped in by {@link SeasonalLeavesModel}, which wraps the
 *       vanilla birch, cherry and poplar leaves models;</li>
 *   <li>the client tick that snapshots the season clock and drives {@link SeasonalLeafRerender}.</li>
 * </ul>
 */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public final class SeasonalFoliageClient {

    private SeasonalFoliageClient() {
    }

    /**
     * Oak in its brown seasons, multiplied onto the greyscale oak texture; indexed by
     * {@link SeasonalLeaves#shadeAt}: russet brown (base), lighter tan/ochre, deep russet red-brown.
     */
    private static final int[] OAK_BROWN = {0xFFC07838, 0xFFD4A04E, 0xFF9C4426};
    /** Birch particle tints for looks whose block texture is pre-coloured. */
    private static final int BIRCH_FROSTY = 0xFFE4ECF2;
    /** Per shade, matching the gold, lemon and amber textures. */
    private static final int[] BIRCH_YELLOW = {0xFFF2CC3A, 0xFFF6E05A, 0xFFDC8C24};
    /** Poplar summer green: a fixed fresh green, so poplars don't turn grey-green in taigas' muted foliage tint. */
    private static final int POPLAR_GREEN = 0xFF64C034;

    private static final StandaloneModelKey<BlockStateModelPart> BIRCH_FROSTY_MODEL = key("birch_leaves_frosty");
    private static final StandaloneModelKey<BlockStateModelPart> BIRCH_FLOWERING_MODEL = key("birch_leaves_flowering");
    private static final StandaloneModelKey<BlockStateModelPart> BIRCH_YELLOW_MODEL = key("birch_leaves_yellow");
    private static final StandaloneModelKey<BlockStateModelPart> BIRCH_LEMON_MODEL = key("birch_leaves_lemon");
    private static final StandaloneModelKey<BlockStateModelPart> BIRCH_AMBER_MODEL = key("birch_leaves_amber");
    private static final StandaloneModelKey<BlockStateModelPart> CHERRY_CRIMSON_MODEL = key("cherry_leaves_crimson");
    private static final StandaloneModelKey<BlockStateModelPart> POPLAR_FROSTY_MODEL = key("poplar_leaves_frosty");
    private static final StandaloneModelKey<BlockStateModelPart> POPLAR_GREEN_MODEL = key("poplar_leaves_green");
    private static final StandaloneModelKey<BlockStateModelPart> POPLAR_ORANGE_MODEL = key("orange_poplar_leaves");
    private static final StandaloneModelKey<BlockStateModelPart> POPLAR_RED_MODEL = key("red_poplar_leaves");
    private static final StandaloneModelKey<BlockStateModelPart> POPLAR_DARK_ORANGE_MODEL = key("poplar_leaves_dark_orange");

    private static StandaloneModelKey<BlockStateModelPart> key(String name) {
        return new StandaloneModelKey<>(() -> "terrainlab:seasonal/" + name);
    }

    private static Identifier seasonalModel(String name) {
        return Identifier.fromNamespaceAndPath(TerrainLab.MODID, "block/seasonal/" + name);
    }

    @SubscribeEvent
    static void onRegisterColorResolvers(RegisterColorHandlersEvent.ColorResolvers event) {
        event.register(SeasonalLeaves.BIOME_MASK);
    }

    @SubscribeEvent
    static void onRegisterBlockTints(RegisterColorHandlersEvent.BlockTintSources event) {
        List<BlockTintSource> oakVanilla = event.getBlockColors().getTintSources(Blocks.OAK_LEAVES.defaultBlockState());
        List<BlockTintSource> birchVanilla = event.getBlockColors().getTintSources(Blocks.BIRCH_LEAVES.defaultBlockState());
        if (!oakVanilla.isEmpty()) {
            event.register(List.of(new SeasonalTint(oakVanilla.getFirst(), Tree.OAK)), Blocks.OAK_LEAVES);
        }
        if (!birchVanilla.isEmpty()) {
            event.register(List.of(new SeasonalTint(birchVanilla.getFirst(), Tree.BIRCH)), Blocks.BIRCH_LEAVES);
        }
        // Vanilla poplar models carry no tintindex, so this only colours the green variant (and its particles).
        event.register(List.of(new SeasonalTint(null, Tree.POPLAR)),
                Blocks.RED_POPLAR_LEAVES, Blocks.ORANGE_POPLAR_LEAVES, Blocks.YELLOW_POPLAR_LEAVES);
        // Cherry: no tint source. Its vanilla model has tintindex 0 but no source, i.e. white; crimson is a texture.
    }

    @SubscribeEvent
    static void onRegisterStandaloneModels(ModelEvent.RegisterStandalone event) {
        register(event, BIRCH_FROSTY_MODEL, seasonalModel("birch_leaves_frosty"));
        register(event, BIRCH_FLOWERING_MODEL, seasonalModel("birch_leaves_flowering"));
        register(event, BIRCH_YELLOW_MODEL, seasonalModel("birch_leaves_yellow"));
        register(event, BIRCH_LEMON_MODEL, seasonalModel("birch_leaves_lemon"));
        register(event, BIRCH_AMBER_MODEL, seasonalModel("birch_leaves_amber"));
        register(event, CHERRY_CRIMSON_MODEL, seasonalModel("cherry_leaves_crimson"));
        register(event, POPLAR_FROSTY_MODEL, seasonalModel("poplar_leaves_frosty"));
        register(event, POPLAR_GREEN_MODEL, seasonalModel("poplar_leaves_green"));
        register(event, POPLAR_ORANGE_MODEL, Identifier.withDefaultNamespace("block/orange_poplar_leaves"));
        register(event, POPLAR_RED_MODEL, Identifier.withDefaultNamespace("block/red_poplar_leaves"));
        register(event, POPLAR_DARK_ORANGE_MODEL, seasonalModel("poplar_leaves_dark_orange"));
    }

    private static void register(ModelEvent.RegisterStandalone event, StandaloneModelKey<BlockStateModelPart> key, Identifier model) {
        event.register(key, SimpleUnbakedStandaloneModel.simpleModelWrapper(model));
    }

    @SubscribeEvent
    static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        var baked = event.getBakingResult().standaloneModels();
        Map<BlockState, BlockStateModel> models = event.getBakingResult().blockStateModels();

        Map<Look, BlockStateModelPart[]> birch = new EnumMap<>(Look.class);
        put(birch, Look.FROSTY, baked.get(BIRCH_FROSTY_MODEL));
        put(birch, Look.FLOWERING, baked.get(BIRCH_FLOWERING_MODEL));
        // Shade order follows SeasonalLeaves.shadeAt: base, lighter, deeper.
        put(birch, Look.YELLOW, baked.get(BIRCH_YELLOW_MODEL), baked.get(BIRCH_LEMON_MODEL), baked.get(BIRCH_AMBER_MODEL));
        wrap(models, Blocks.BIRCH_LEAVES, Tree.BIRCH, birch);

        Map<Look, BlockStateModelPart[]> cherry = new EnumMap<>(Look.class);
        put(cherry, Look.CRIMSON, baked.get(CHERRY_CRIMSON_MODEL));
        wrap(models, Blocks.CHERRY_LEAVES, Tree.CHERRY, cherry);

        // All three poplar leaf blocks look alike in each season; in autumn each block picks vanilla's
        // orange or red poplar leaves, or the mod's darker orange, by position.
        Map<Look, BlockStateModelPart[]> poplar = new EnumMap<>(Look.class);
        put(poplar, Look.FROSTY, baked.get(POPLAR_FROSTY_MODEL));
        put(poplar, Look.GREEN, baked.get(POPLAR_GREEN_MODEL));
        put(poplar, Look.ORANGE, baked.get(POPLAR_ORANGE_MODEL), baked.get(POPLAR_DARK_ORANGE_MODEL), baked.get(POPLAR_RED_MODEL));
        wrap(models, Blocks.ORANGE_POPLAR_LEAVES, Tree.POPLAR, poplar);
        wrap(models, Blocks.RED_POPLAR_LEAVES, Tree.POPLAR, poplar);
        wrap(models, Blocks.YELLOW_POPLAR_LEAVES, Tree.POPLAR, poplar);
        // Oak needs no texture swap: its seasonal look is purely a tint (shaded per block).
    }

    /** One part for a plain look, or one per shade (base, lighter, deeper) for a shaded autumn look. */
    private static void put(Map<Look, BlockStateModelPart[]> map, Look look, BlockStateModelPart... parts) {
        List<BlockStateModelPart> found = new ArrayList<>(parts.length);
        for (BlockStateModelPart part : parts) {
            if (part != null) {
                found.add(part);
            } else {
                TerrainLab.LOGGER.warn("Seasonal leaves: missing baked model for {}", look);
            }
        }
        if (!found.isEmpty()) {
            map.put(look, found.toArray(BlockStateModelPart[]::new));
        }
    }

    private static void wrap(Map<BlockState, BlockStateModel> models, Block block, Tree tree, Map<Look, BlockStateModelPart[]> variants) {
        if (variants.isEmpty()) {
            return;
        }
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            BlockStateModel model = models.get(state);
            if (model != null && !(model instanceof SeasonalLeavesModel)) {
                models.put(state, new SeasonalLeavesModel(model, tree, variants));
            }
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        SeasonalLeaves.updateClock(minecraft.level);
        SeasonalLeafRerender.tick(minecraft);
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SeasonalLeaves.resetSession();
        SeasonalLeafRerender.reset();
    }

    /** Wraps a vanilla leaves tint (or none) and substitutes the seasonal colour where the look calls for it. */
    private record SeasonalTint(BlockTintSource vanilla, Tree tree) implements BlockTintSource {

        @Override
        public int color(BlockState state) {
            return this.vanilla != null ? this.vanilla.color(state) : -1;
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            Look look = SeasonalLeaves.lookAt(level, pos, this.tree);
            switch (look) {
                case BROWN:
                    return OAK_BROWN[SeasonalLeaves.shadeAt(pos)];
                case FROSTY:
                    if (this.tree == Tree.BIRCH) return BIRCH_FROSTY;
                    break;
                case YELLOW:
                    return BIRCH_YELLOW[SeasonalLeaves.shadeAt(pos)];
                case GREEN:
                    return POPLAR_GREEN;
                default:
                    break;
            }
            return this.vanilla != null ? this.vanilla.colorInWorld(state, level, pos) : -1;
        }
    }
}
