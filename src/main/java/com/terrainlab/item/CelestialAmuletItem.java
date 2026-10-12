package com.terrainlab.item;

import com.terrainlab.celestial.CelestialVariant;
import net.minecraft.world.item.Item;

/**
 * Celestial amulet worn in the Curios necklace slot. Its effects are applied by CelestialEventHandler
 * while the paired conduit is active.
 */
public class CelestialAmuletItem extends Item {

    private final CelestialVariant variant;

    public CelestialAmuletItem(CelestialVariant variant, Properties properties) {
        super(properties);
        this.variant = variant;
    }

    public CelestialVariant variant() {
        return variant;
    }
}
