package com.terrainlab.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * World-global per-player milestones: first visit to each dimension and the one-time End return bonus.
 * Always fetched from the overworld, since these facts are not dimension-scoped.
 */
public class PlayerMilestoneData extends SavedData {

    private final Map<UUID, PlayerMilestones> byPlayer = new HashMap<>();

    private static final class PlayerMilestones {
        final Set<ResourceKey<Level>> visitedDimensions = new HashSet<>();
        boolean endReturnBonusGranted = false;
    }

    private record Entry(UUID playerId, Set<ResourceKey<Level>> visitedDimensions, boolean endReturnBonusGranted) {
        // Codec stays List-typed; a HashSet xmap here breaks RecordCodecBuilder's inference for Entry::visitedDimensions.
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("player_id").forGetter(Entry::playerId),
                ResourceKey.codec(Registries.DIMENSION).listOf().fieldOf("visited_dimensions")
                        .forGetter(e -> List.copyOf(e.visitedDimensions())),
                Codec.BOOL.fieldOf("end_return_bonus_granted").forGetter(Entry::endReturnBonusGranted)
        ).apply(instance, (playerId, dims, granted) -> new Entry(playerId, new HashSet<>(dims), granted)));
    }

    public static final SavedDataType<PlayerMilestoneData> TYPE = new SavedDataType<>(
            "terrainlab_player_milestones",
            PlayerMilestoneData::new,
            level -> RecordCodecBuilder.create(instance -> instance.group(
                    RecordCodecBuilder.point(level),
                    Entry.CODEC.listOf().fieldOf("players").forGetter(
                            (PlayerMilestoneData d) -> d.byPlayer.entrySet().stream()
                                    .map(en -> new Entry(en.getKey(), en.getValue().visitedDimensions, en.getValue().endReturnBonusGranted))
                                    .toList())
            ).apply(instance, (lvl, entries) -> {
                PlayerMilestoneData data = new PlayerMilestoneData(lvl);
                for (Entry e : entries) {
                    PlayerMilestones m = new PlayerMilestones();
                    m.visitedDimensions.addAll(e.visitedDimensions());
                    m.endReturnBonusGranted = e.endReturnBonusGranted();
                    data.byPlayer.put(e.playerId(), m);
                }
                return data;
            }))
    );

    public PlayerMilestoneData() {
    }

    public PlayerMilestoneData(ServerLevel level) {
        // level is unused; the constructor shape matches the SavedDataType factory.
    }

    public static PlayerMilestoneData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Returns true (and permanently records it) only the first time this player visits this dimension. */
    public boolean tryClaimDimensionVisit(UUID playerId, ResourceKey<Level> dimension) {
        PlayerMilestones m = byPlayer.computeIfAbsent(playerId, k -> new PlayerMilestones());
        boolean added = m.visitedDimensions.add(dimension);
        if (added) {
            setDirty();
        }
        return added;
    }

    /** Returns true (and permanently records it) only the first time this player claims the End-return bonus. */
    public boolean tryClaimEndReturn(UUID playerId) {
        PlayerMilestones m = byPlayer.computeIfAbsent(playerId, k -> new PlayerMilestones());
        if (m.endReturnBonusGranted) {
            return false;
        }
        m.endReturnBonusGranted = true;
        setDirty();
        return true;
    }
}
