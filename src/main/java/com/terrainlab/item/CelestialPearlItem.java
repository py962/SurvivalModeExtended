package com.terrainlab.item;

import com.terrainlab.celestial.CelestialVariant;
import net.minecraft.world.item.Item;

/**
 * One of the eight Celestial Forge pearls: a crafting intermediate whose pair
 * (ModDataComponents.CELESTIAL_PAIR_ID) is crafted into a conduit and an amulet.
 */
public class CelestialPearlItem extends Item {

    private final CelestialVariant variant;

    public CelestialPearlItem(CelestialVariant variant, Properties properties) {
        super(properties);
        this.variant = variant;
    }

    public CelestialVariant variant() {
        return variant;
    }
}
