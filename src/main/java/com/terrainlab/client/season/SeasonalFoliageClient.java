package com.terrainlab.client.season;

import com.terrainlab.TerrainLab;
import com.terrainlab.client.season.SeasonalLeaves.Look;
import com.terrainlab.client.season.SeasonalLeaves.Tree;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.BlockAndTintGetter;
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
import org.jspecify.annotations.Nullable;

/**
 * Registration for seasonal leaf colours (client only; no world or block-state changes):
 * <ul>
 *   <li>a biome-mask {@link net.minecraft.world.level.ColorResolver} so meshing threads can ask "seasonal biome?";</li>
 *   <li>block colours: oak turns russet (otherwise vanilla's foliage tint), birch tint follows its look for
 *       falling-leaf / break particles (otherwise vanilla's fixed birch tint);</li>
 *   <li>texture variants (standalone models) swapped in by {@link SeasonalLeavesModel}, which wraps the
 *       vanilla birch and cherry leaves models;</li>
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

    /** Oaks only turn in these biomes: data/terrainlab/tags/worldgen/biome/seasonal_oaks.json. */
    private static final net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> SEASONAL_OAKS =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME,
                    Identifier.fromNamespaceAndPath(TerrainLab.MODID, "seasonal_oaks"));
    private static final Map<net.minecraft.world.level.biome.Biome, Boolean> OAK_BIOMES =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Green where oaks are seasonal, red elsewhere; blended and cached by vanilla like a biome tint. */
    static final net.minecraft.world.level.ColorResolver OAK_MASK = (biome, x, z) ->
            OAK_BIOMES.computeIfAbsent(biome, SeasonalFoliageClient::isOakBiome) ? 0xFF00FF00 : 0xFFFF0000;

    private static boolean isOakBiome(net.minecraft.world.level.biome.Biome biome) {
        var level = Minecraft.getInstance().level;
        return level != null && level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
                .wrapAsHolder(biome).is(SEASONAL_OAKS);
    }

    private static boolean isSeasonalOak(@Nullable BlockAndTintGetter level, @Nullable BlockPos pos) {
        if (level == null || pos == null) return false;
        int mask = level.getBlockTint(pos, OAK_MASK);
        return ((mask >> 8) & 0xFF) > ((mask >> 16) & 0xFF);
    }
    /** Birch particle tints for looks whose block texture is pre-coloured. */
    private static final int BIRCH_FROSTY = 0xFFE4ECF2;
    /** Per shade, matching the gold, lemon and amber textures. */
    private static final int[] BIRCH_YELLOW = {0xFFF2CC3A, 0xFFF6E05A, 0xFF8D6214};

    /** Vanilla's leaf tints (BlockColors.createDefault), used outside the seasonal looks. */
    private static final int VANILLA_BIRCH = -8345771;
    private static final int VANILLA_FOLIAGE_DEFAULT = -12012264;

    private static final StandaloneModelKey<BlockModelPart> BIRCH_FROSTY_MODEL = key("birch_leaves_frosty");
    private static final StandaloneModelKey<BlockModelPart> BIRCH_FLOWERING_MODEL = key("birch_leaves_flowering");
    private static final StandaloneModelKey<BlockModelPart> BIRCH_YELLOW_MODEL = key("birch_leaves_yellow");
    private static final StandaloneModelKey<BlockModelPart> BIRCH_LEMON_MODEL = key("birch_leaves_lemon");
    private static final StandaloneModelKey<BlockModelPart> BIRCH_AMBER_MODEL = key("birch_leaves_amber");
    private static final StandaloneModelKey<BlockModelPart> CHERRY_CRIMSON_MODEL = key("cherry_leaves_crimson");

    private static StandaloneModelKey<BlockModelPart> key(String name) {
        return new StandaloneModelKey<>(() -> "terrainlab:seasonal/" + name);
    }

    private static Identifier seasonalModel(String name) {
        return Identifier.fromNamespaceAndPath(TerrainLab.MODID, "block/seasonal/" + name);
    }

    @SubscribeEvent
    static void onRegisterColorResolvers(RegisterColorHandlersEvent.ColorResolvers event) {
        event.register(SeasonalLeaves.BIOME_MASK);
        event.register(OAK_MASK);
    }

    @SubscribeEvent
    static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(new SeasonalTint(Tree.OAK), Blocks.OAK_LEAVES);
        event.register(new SeasonalTint(Tree.BIRCH), Blocks.BIRCH_LEAVES);
        // Cherry: no block colour; crimson is a texture.
    }

    @SubscribeEvent
    static void onRegisterStandaloneModels(ModelEvent.RegisterStandalone event) {
        register(event, BIRCH_FROSTY_MODEL, seasonalModel("birch_leaves_frosty"));
        register(event, BIRCH_FLOWERING_MODEL, seasonalModel("birch_leaves_flowering"));
        register(event, BIRCH_YELLOW_MODEL, seasonalModel("birch_leaves_yellow"));
        register(event, BIRCH_LEMON_MODEL, seasonalModel("birch_leaves_lemon"));
        register(event, BIRCH_AMBER_MODEL, seasonalModel("birch_leaves_amber"));
        register(event, CHERRY_CRIMSON_MODEL, seasonalModel("cherry_leaves_crimson"));
    }

    private static void register(ModelEvent.RegisterStandalone event, StandaloneModelKey<BlockModelPart> key, Identifier model) {
        event.register(key, SimpleUnbakedStandaloneModel.simpleModelWrapper(model));
    }

    @SubscribeEvent
    static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        var baked = event.getBakingResult().standaloneModels();
        Map<BlockState, BlockStateModel> models = event.getBakingResult().blockStateModels();

        Map<Look, BlockModelPart[]> birch = new EnumMap<>(Look.class);
        put(birch, Look.FROSTY, baked.get(BIRCH_FROSTY_MODEL));
        put(birch, Look.FLOWERING, baked.get(BIRCH_FLOWERING_MODEL));
        // Shade order follows SeasonalLeaves.shadeAt: base, lighter, deeper.
        put(birch, Look.YELLOW, baked.get(BIRCH_YELLOW_MODEL), baked.get(BIRCH_LEMON_MODEL), baked.get(BIRCH_AMBER_MODEL));
        wrap(models, Blocks.BIRCH_LEAVES, Tree.BIRCH, birch);

        Map<Look, BlockModelPart[]> cherry = new EnumMap<>(Look.class);
        put(cherry, Look.CRIMSON, baked.get(CHERRY_CRIMSON_MODEL));
        wrap(models, Blocks.CHERRY_LEAVES, Tree.CHERRY, cherry);
        // Oak needs no texture swap: its seasonal look is purely a tint (shaded per block).
    }

    /** One part for a plain look, or one per shade (base, lighter, deeper) for a shaded autumn look. */
    private static void put(Map<Look, BlockModelPart[]> map, Look look, @Nullable BlockModelPart... parts) {
        List<BlockModelPart> found = new ArrayList<>(parts.length);
        for (BlockModelPart part : parts) {
            if (part != null) {
                found.add(part);
            } else {
                TerrainLab.LOGGER.warn("Seasonal leaves: missing baked model for {}", look);
            }
        }
        if (!found.isEmpty()) {
            map.put(look, found.toArray(BlockModelPart[]::new));
        }
    }

    private static void wrap(Map<BlockState, BlockStateModel> models, Block block, Tree tree, Map<Look, BlockModelPart[]> variants) {
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
        OAK_BIOMES.clear();
        SeasonalLeafRerender.reset();
    }

    /** Vanilla's leaves colour, with the seasonal colour substituted where the look calls for it. */
    private record SeasonalTint(Tree tree) implements BlockColor {

        @Override
        public int getColor(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
            Look look = SeasonalLeaves.lookAt(level, pos, this.tree);
            switch (look) {
                case BROWN:
                    if (!isSeasonalOak(level, pos)) break;
                    return OAK_BROWN[pos != null ? SeasonalLeaves.shadeAt(pos) : 0];
                case FROSTY:
                    if (this.tree == Tree.BIRCH) return BIRCH_FROSTY;
                    break;
                case YELLOW:
                    return BIRCH_YELLOW[pos != null ? SeasonalLeaves.shadeAt(pos) : 0];
                default:
                    break;
            }
            if (this.tree == Tree.BIRCH) {
                return VANILLA_BIRCH;
            }
            return level != null && pos != null
                    ? BiomeColors.getAverageFoliageColor(level, pos)
                    : VANILLA_FOLIAGE_DEFAULT;
        }
    }
}
