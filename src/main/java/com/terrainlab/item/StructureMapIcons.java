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
 * otherwise). Charts are always plain filled maps in 1.21.11 (no per-structure explorer map items).
 */
public final class StructureMapIcons {

    /**
     * @param decoration     the icon drawn at the structure
     * @param spyglassMarked whether the spyglass marks it when its top is visible (buried treasure never is)
     */
    public record Icon(Supplier<Holder<MapDecorationType>> decoration, boolean spyglassMarked) {
    }

    private static Icon vanilla(Holder<MapDecorationType> decoration) {
        return new Icon(() -> decoration, true);
    }

    private static Icon mod(Supplier<Holder<MapDecorationType>> decoration) {
        return new Icon(decoration, true);
    }

    private static final Map<String, Icon> EXACT = Map.ofEntries(
            Map.entry("minecraft:village_plains", vanilla(MapDecorationTypes.PLAINS_VILLAGE)),
            Map.entry("minecraft:village_desert", vanilla(MapDecorationTypes.DESERT_VILLAGE)),
            Map.entry("minecraft:village_savanna", vanilla(MapDecorationTypes.SAVANNA_VILLAGE)),
            Map.entry("minecraft:village_snowy", vanilla(MapDecorationTypes.SNOWY_VILLAGE)),
            Map.entry("minecraft:village_taiga", vanilla(MapDecorationTypes.TAIGA_VILLAGE)),
            Map.entry("minecraft:mansion", vanilla(MapDecorationTypes.WOODLAND_MANSION)),
            Map.entry("minecraft:monument", vanilla(MapDecorationTypes.OCEAN_MONUMENT)),
            Map.entry("minecraft:jungle_pyramid", vanilla(MapDecorationTypes.JUNGLE_TEMPLE)),
            Map.entry("minecraft:swamp_hut", vanilla(MapDecorationTypes.SWAMP_HUT)),
            Map.entry("minecraft:trial_chambers", vanilla(MapDecorationTypes.TRIAL_CHAMBERS)),
            Map.entry("minecraft:buried_treasure", new Icon(() -> MapDecorationTypes.RED_X, false)),
            Map.entry("minecraft:pillager_outpost", mod(ModMapDecorations.PILLAGER_OUTPOST::getDelegate)),
            Map.entry("minecraft:desert_pyramid", mod(ModMapDecorations.DESERT_PYRAMID::getDelegate)),
            Map.entry("minecraft:igloo", mod(ModMapDecorations.IGLOO::getDelegate)),
            Map.entry("minecraft:shipwreck", mod(ModMapDecorations.SHIPWRECK::getDelegate)),
            Map.entry("minecraft:shipwreck_beached", mod(ModMapDecorations.SHIPWRECK::getDelegate)),
            Map.entry("minecraft:ocean_ruin_cold", mod(ModMapDecorations.OCEAN_RUIN::getDelegate)),
            Map.entry("minecraft:ocean_ruin_warm", mod(ModMapDecorations.OCEAN_RUIN::getDelegate)),
            Map.entry("minecraft:trail_ruins", mod(ModMapDecorations.TRAIL_RUINS::getDelegate)),
            Map.entry("minecraft:end_city", mod(ModMapDecorations.END_CITY::getDelegate)),
            Map.entry(TerrainLab.MODID + ":outlaws_hideout", mod(ModMapDecorations.OUTLAWS_HIDEOUT::getDelegate)),
            Map.entry(TerrainLab.MODID + ":trappers_lodge", mod(ModMapDecorations.TRAPPERS_LODGE::getDelegate)),
            Map.entry(TerrainLab.MODID + ":pirates_cove", mod(ModMapDecorations.PIRATES_COVE::getDelegate)));

    // Structure families with one id per variant (ruined_portal_desert, ...).
    private static final List<Map.Entry<String, Icon>> PREFIXES = List.of(
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
