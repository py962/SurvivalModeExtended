package com.terrainlab.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashSet;
import java.util.Set;

/**
 * Server-wide record of which villager biome types have already paid out the one-time
 * "first tier-3 village" bonus. Always accessed through the overworld's data storage.
 */
public class VillageBiomeMilestoneData extends SavedData {

    private final Set<String> awardedVillagerTypes = new HashSet<>();

    public static final SavedDataType<VillageBiomeMilestoneData> TYPE = new SavedDataType<>(
            "terrainlab_village_biome_milestones",
            VillageBiomeMilestoneData::new,
            level -> RecordCodecBuilder.create(instance -> instance.group(
                    RecordCodecBuilder.point(level),
                    Codec.STRING.listOf().fieldOf("awarded_villager_types").forGetter(
                            (VillageBiomeMilestoneData d) -> d.awardedVillagerTypes.stream().toList())
            ).apply(instance, (lvl, names) -> {
                VillageBiomeMilestoneData data = new VillageBiomeMilestoneData(lvl);
                data.awardedVillagerTypes.addAll(names);
                return data;
            }))
    );

    public VillageBiomeMilestoneData() {
    }

    public VillageBiomeMilestoneData(ServerLevel level) {
        // Level-aware constructor required by the SavedDataType factory; the level is unused.
    }

    public static VillageBiomeMilestoneData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Records {@code type} as claimed. Returns true only the first time it is claimed anywhere
     * on the server. Stored by registry identifier (e.g. "minecraft:desert").
     */
    public boolean tryClaim(ResourceKey<VillagerType> type) {
        boolean added = awardedVillagerTypes.add(type.identifier().toString());
        if (added) {
            setDirty();
        }
        return added;
    }
}
