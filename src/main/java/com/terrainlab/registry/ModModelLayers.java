package com.terrainlab.registry;

import com.terrainlab.TerrainLab;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;

/** Client-only model layer locations for entity and equipment models. */
public class ModModelLayers {

    public static final ModelLayerLocation HOUSEBOAT = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "houseboat"), "main");

    public static final ModelLayerLocation HOUSEBOAT_WATER_PATCH = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "houseboat"), "water_patch");

    public static final ModelLayerLocation GRANITE_GOLEM = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "granite_golem"), "main");

    public static final ModelLayerLocation GHOST_FISH = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "ghost_fish"), "main");

    public static final ModelLayerLocation CARIBOU = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "caribou"), "main");
    public static final ModelLayerLocation CARIBOU_BABY = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "caribou_baby"), "main");

    // Adult goat with the extra "body_fluff" part; babies reuse vanilla's GOAT_BABY layer.
    public static final ModelLayerLocation GOAT = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "goat"), "main");

    public static final ModelLayerLocation HOWDAH = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "howdah"), "main");

    public static final ModelLayerLocation OWL = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "owl"), "main");

    public static final ModelLayerLocation GOOSE = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "goose"), "main");

    public static final ModelLayerLocation VULTURE = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "vulture"), "main");

    public static final ModelLayerLocation ELEPHANT = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "elephant"), "main");
    public static final ModelLayerLocation ELEPHANT_BABY = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "elephant_baby"), "main");

    // Fur cloak body mesh, rendered through Curios.
    public static final ModelLayerLocation FUR_CLOAK = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "fur_cloak"), "main");

    // One pendant mesh shared by all celestial amulet variants; only the texture differs.
    public static final ModelLayerLocation CELESTIAL_AMULET = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "celestial_amulet"), "main");

    // Pillager raider gear layers (see the *RaiderRenderer classes).
    public static final ModelLayerLocation PIRATE_BANDANA = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "pirate_raider"), "bandana");
    public static final ModelLayerLocation PIRATE_EYEPATCH = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "pirate_raider"), "eyepatch");
    public static final ModelLayerLocation OUTLAW_RAIDER_BODY = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "outlaw_raider"), "body");
    public static final ModelLayerLocation OUTLAW_BANDANA = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "outlaw_raider"), "bandana");
    public static final ModelLayerLocation TRAPPER_HAT = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "trapper_raider"), "hat");
}
