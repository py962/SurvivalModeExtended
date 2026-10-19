package com.terrainlab.item;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModMapDecorations;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The one structure -> map icon table, shared by the spyglass structure markers and the trail
 * guides' charts. Each entry names the map decoration (vanilla's where one exists, TerrainLab's
 * otherwise) and the map item a chart to that structure is made as: vanilla's matching explorer
 * map item, or a plain filled map for structures vanilla has no map item for.
 */
public final class StructureMapIcons {

    /**
     * @param decoration     the icon drawn at the structure
     * @param mapItem        the item a chart to the structure is made as
     * @param spyglassMarked whether the spyglass marks it when its top is visible (buried treasure never is)
     */
    public record Icon(Supplier<Holder<MapDecorationType>> decoration, Item mapItem, boolean spyglassMarked) {
    }

    private static Icon vanilla(Holder<MapDecorationType> decoration, Item mapItem) {
        return new Icon(() -> decoration, mapItem, true);
    }

    private static Icon mod(Supplier<Holder<MapDecorationType>> decoration) {
        return new Icon(decoration, Items.FILLED_MAP, true);
    }

    private static final Map<String, Icon> EXACT = Map.ofEntries(
            Map.entry("minecraft:village_plains", vanilla(MapDecorationTypes.PLAINS_VILLAGE, Items.PLAINS_VILLAGE_MAP)),
            Map.entry("minecraft:village_desert", vanilla(MapDecorationTypes.DESERT_VILLAGE, Items.DESERT_VILLAGE_MAP)),
            Map.entry("minecraft:village_savanna", vanilla(MapDecorationTypes.SAVANNA_VILLAGE, Items.SAVANNA_VILLAGE_MAP)),
            Map.entry("minecraft:village_snowy", vanilla(MapDecorationTypes.SNOWY_VILLAGE, Items.SNOWY_VILLAGE_MAP)),
            Map.entry("minecraft:village_taiga", vanilla(MapDecorationTypes.TAIGA_VILLAGE, Items.TAIGA_VILLAGE_MAP)),
            Map.entry("minecraft:mansion", vanilla(MapDecorationTypes.WOODLAND_MANSION, Items.WOODLAND_MANSION_MAP)),
            Map.entry("minecraft:monument", vanilla(MapDecorationTypes.OCEAN_MONUMENT, Items.OCEAN_MONUMENT_MAP)),
            Map.entry("minecraft:jungle_pyramid", vanilla(MapDecorationTypes.JUNGLE_TEMPLE, Items.JUNGLE_PYRAMID_MAP)),
            Map.entry("minecraft:desert_pyramid", vanilla(MapDecorationTypes.DESERT_PYRAMID, Items.DESERT_PYRAMID_MAP)),
            Map.entry("minecraft:swamp_hut", vanilla(MapDecorationTypes.SWAMP_HUT, Items.SWAMP_HUT_MAP)),
            Map.entry("minecraft:trial_chambers", vanilla(MapDecorationTypes.TRIAL_CHAMBERS, Items.BURIED_TRIAL_CHAMBERS_MAP)),
            Map.entry("minecraft:ancient_city", vanilla(MapDecorationTypes.ANCIENT_CITY, Items.BURIED_ANCIENT_CITY_MAP)),
            Map.entry("minecraft:ocean_ruin_warm", vanilla(MapDecorationTypes.OCEAN_RUIN_WARM, Items.WARM_OCEAN_RUINS_MAP)),
            Map.entry("minecraft:buried_treasure", new Icon(() -> MapDecorationTypes.RED_X, Items.BURIED_TREASURE_MAP, false)),
            Map.entry("minecraft:pillager_outpost", mod(ModMapDecorations.PILLAGER_OUTPOST::getDelegate)),
            Map.entry("minecraft:igloo", mod(ModMapDecorations.IGLOO::getDelegate)),
            Map.entry("minecraft:shipwreck", mod(ModMapDecorations.SHIPWRECK::getDelegate)),
            Map.entry("minecraft:shipwreck_beached", mod(ModMapDecorations.SHIPWRECK::getDelegate)),
            Map.entry("minecraft:ocean_ruin_cold", mod(ModMapDecorations.OCEAN_RUIN::getDelegate)),
            Map.entry("minecraft:trail_ruins", mod(ModMapDecorations.TRAIL_RUINS::getDelegate)),
            Map.entry("minecraft:end_city", mod(ModMapDecorations.END_CITY::getDelegate)),
            Map.entry(TerrainLab.MODID + ":outlaws_hideout", mod(ModMapDecorations.OUTLAWS_HIDEOUT::getDelegate)),
            Map.entry(TerrainLab.MODID + ":trappers_lodge", mod(ModMapDecorations.TRAPPERS_LODGE::getDelegate)),
            Map.entry(TerrainLab.MODID + ":pirates_cove", mod(ModMapDecorations.PIRATES_COVE::getDelegate)));

    // Structure families with one id per variant (abandoned_camp_forest, mineshaft_mesa, ruined_portal_desert, ...).
    private static final List<Map.Entry<String, Icon>> PREFIXES = List.of(
            Map.entry("minecraft:abandoned_camp", vanilla(MapDecorationTypes.ABANDONED_CAMP, Items.ABANDONED_CAMP_MAP)),
            Map.entry("minecraft:mineshaft", vanilla(MapDecorationTypes.MINESHAFT, Items.BURIED_MINESHAFT_MAP)),
            Map.entry("minecraft:ruined_portal", mod(ModMapDecorations.RUINED_PORTAL::getDelegate)));

    private StructureMapIcons() {
    }

    /** The icon for a structure id, or null if it has none. */
    public static @Nullable Icon forStructure(@Nullable Identifier id) {
        if (id == null) {
            return null;
        }
        String key = id.toString();
        Icon icon = EXACT.get(key);
        if (icon != null) {
            return icon;
        }
        for (Map.Entry<String, Icon> prefix : PREFIXES) {
            if (key.startsWith(prefix.getKey())) {
                return prefix.getValue();
            }
        }
        return null;
    }

    /** The icon of the first structure in {@code tag} that has one, or null if none does. */
    public static @Nullable Icon forTag(RegistryAccess access, TagKey<Structure> tag) {
        Optional<HolderSet.Named<Structure>> structures = access.lookupOrThrow(Registries.STRUCTURE).get(tag);
        if (structures.isEmpty()) {
            return null;
        }
        for (Holder<Structure> structure : structures.get()) {
            Icon icon = forStructure(structure.unwrapKey().map(ResourceKey::identifier).orElse(null));
            if (icon != null) {
                return icon;
            }
        }
        return null;
    }
}
