package com.terrainlab.block;

import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;

import java.util.Optional;

/**
 * Wood types, block set types and tree growers for the Pine, Azalea and Enderwood wood sets.
 *
 * <p>Names are namespaced ("terrainlab:pine") because vanilla parses WoodType names as
 * identifiers to find the sign textures (entity/signs/pine.png, entity/signs/hanging/pine.png)
 * and the hanging sign edit screen background (gui/hanging_signs/pine.png).
 *
 * <p>This class is loaded from ModBlocks' static init, i.e. during mod construction, so the
 * types are registered before vanilla collects WoodType.values() for sign models and layers.
 * The client still calls Sheets.addWoodType for each (see WoodSetsClient).
 */
public final class ModWoodTypes {

    public static final BlockSetType PINE_SET = BlockSetType.register(new BlockSetType("terrainlab:pine"));
    public static final BlockSetType AZALEA_SET = BlockSetType.register(new BlockSetType("terrainlab:azalea"));
    public static final BlockSetType ENDERWOOD_SET = BlockSetType.register(new BlockSetType("terrainlab:enderwood"));

    public static final WoodType PINE = WoodType.register(new WoodType("terrainlab:pine", PINE_SET));
    public static final WoodType AZALEA = WoodType.register(new WoodType("terrainlab:azalea", AZALEA_SET));
    public static final WoodType ENDERWOOD = WoodType.register(new WoodType("terrainlab:enderwood", ENDERWOOD_SET));

    /** Grows vanilla's pine (single sapling) or mega pine (2x2), which now use pine logs and leaves. */
    public static final TreeGrower PINE_GROWER = new TreeGrower(
            "terrainlab:pine",
            Optional.of(TreeFeatures.MEGA_PINE),
            Optional.of(TreeFeatures.PINE),
            Optional.empty());

    private ModWoodTypes() {
    }
}
