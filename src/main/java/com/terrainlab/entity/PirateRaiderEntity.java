package com.terrainlab.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.level.Level;

/**
 * Pirates Cove garrison and land patrols: a Pillager with pirate gear (see PirateRaiderRenderer).
 * Pirates given a home area walk back to it; the goal is registered here so it survives reloads.
 */
public class PirateRaiderEntity extends Pillager {

    private static final int WALK_BACK_GOAL_PRIORITY = 7;

    public PirateRaiderEntity(EntityType<? extends Pillager> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(WALK_BACK_GOAL_PRIORITY, new MoveTowardsRestrictionGoal(this, 1.0));
    }
}
