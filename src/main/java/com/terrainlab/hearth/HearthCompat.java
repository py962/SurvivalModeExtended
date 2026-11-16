package com.terrainlab.hearth;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;

public final class HearthCompat {

    private HearthCompat() {
    }

    public static final Class<? extends Entity> CUSHION_CLASS = Cushion.class;

    public static final BlockEntityType<?> CAMPFIRE_BLOCK_ENTITY = BlockEntityTypes.CAMPFIRE;

    public static boolean isCushion(Entity entity) {
        return entity instanceof Cushion;
    }
}
