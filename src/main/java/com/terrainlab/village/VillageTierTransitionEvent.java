package com.terrainlab.village;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.neoforged.bus.api.Event;

import java.util.List;

/**
 * Posted on the NeoForge event bus when a village's daily check changes its tier. Rewards are
 * granted by listeners (see VillageEconomyHandler).
 */
public class VillageTierTransitionEvent extends Event {

    private final ServerLevel level;
    private final VillageId villageId;
    private final VillageTier previousTier;
    private final VillageTier newTier;
    private final List<Villager> members;

    public VillageTierTransitionEvent(ServerLevel level, VillageId villageId,
                                       VillageTier previousTier, VillageTier newTier,
                                       List<Villager> members) {
        this.level = level;
        this.villageId = villageId;
        this.previousTier = previousTier;
        this.newTier = newTier;
        this.members = members;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public VillageId getVillageId() {
        return villageId;
    }

    public VillageTier getPreviousTier() {
        return previousTier;
    }

    public VillageTier getNewTier() {
        return newTier;
    }

    public boolean isLevelUp() {
        return newTier.level() > previousTier.level();
    }

    public List<Villager> getMembers() {
        return members;
    }
}
