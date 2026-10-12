package com.terrainlab.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/**
 * BlockItem for the Celestial Conduit. Pair-ID transfer to the placed block entity happens in
 * CelestialConduitBlock#setPlacedBy.
 */
public class CelestialConduitBlockItem extends BlockItem {

    public CelestialConduitBlockItem(Block block, Properties properties) {
        super(block, properties);
    }
}
