package com.terrainlab.item;

import net.minecraft.world.item.Item;

/**
 * Fur cloak worn in the Curios "body" slot. Its temperature effects are applied by
 * PlayerInternalTemperature, which checks Curios for it.
 */
public class FurCloakItem extends Item {

    public FurCloakItem(Properties properties) {
        super(properties);
    }
}
