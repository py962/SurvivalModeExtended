package com.terrainlab.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.level.Level;

/**
 * Trapper's Lodge garrison: a vanilla Pillager with its own renderer layer (fur hat) and spawn rules.
 */
public class TrapperRaiderEntity extends Pillager {

    public TrapperRaiderEntity(EntityType<? extends Pillager> entityType, Level level) {
        super(entityType, level);
    }
}
