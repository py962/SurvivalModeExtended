package com.terrainlab.registry;

import com.terrainlab.TerrainLab;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Map icons for structures that vanilla has no icon for (see {@link com.terrainlab.item.StructureMapIcons}). Sprites live in
 * {@code assets/terrainlab/textures/map/decorations/}, which the vanilla map decoration atlas picks up.
 */
public final class ModMapDecorations {

    public static final DeferredRegister<MapDecorationType> MAP_DECORATIONS =
            DeferredRegister.create(Registries.MAP_DECORATION_TYPE, TerrainLab.MODID);

    public static final DeferredHolder<MapDecorationType, MapDecorationType> PILLAGER_OUTPOST = register("pillager_outpost");
    public static final DeferredHolder<MapDecorationType, MapDecorationType> IGLOO = register("igloo");
    public static final DeferredHolder<MapDecorationType, MapDecorationType> SHIPWRECK = register("shipwreck");
    public static final DeferredHolder<MapDecorationType, MapDecorationType> RUINED_PORTAL = register("ruined_portal");
    public static final DeferredHolder<MapDecorationType, MapDecorationType> OCEAN_RUIN = register("ocean_ruin");
    public static final DeferredHolder<MapDecorationType, MapDecorationType> TRAIL_RUINS = register("trail_ruins");
    public static final DeferredHolder<MapDecorationType, MapDecorationType> END_CITY = register("end_city");
    public static final DeferredHolder<MapDecorationType, MapDecorationType> OUTLAWS_HIDEOUT = register("outlaws_hideout");
    public static final DeferredHolder<MapDecorationType, MapDecorationType> TRAPPERS_LODGE = register("trappers_lodge");
    public static final DeferredHolder<MapDecorationType, MapDecorationType> PIRATES_COVE = register("pirates_cove");

    private ModMapDecorations() {
    }

    // Same flags as vanilla's structure icons: shown in item frames, no map tint, counted.
    private static DeferredHolder<MapDecorationType, MapDecorationType> register(String name) {
        return MAP_DECORATIONS.register(name, () -> new MapDecorationType(
                Identifier.fromNamespaceAndPath(TerrainLab.MODID, name), true, true));
    }

    public static void register(IEventBus modEventBus) {
        MAP_DECORATIONS.register(modEventBus);
    }
}
