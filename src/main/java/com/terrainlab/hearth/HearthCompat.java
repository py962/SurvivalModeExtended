package com.terrainlab.hearth;

import com.terrainlab.cushion.CushionEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class HearthCompat {

    private HearthCompat() {
    }

    public static final Class<? extends Entity> CUSHION_CLASS = CushionEntity.class;

    public static final BlockEntityType<?> CAMPFIRE_BLOCK_ENTITY = BlockEntityType.CAMPFIRE;

    public static boolean isCushion(Entity entity) {
        return entity instanceof CushionEntity;
    }
}
