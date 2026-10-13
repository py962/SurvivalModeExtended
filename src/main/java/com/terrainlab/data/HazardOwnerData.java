package com.terrainlab.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-level record of who placed each lava source and campfire, so a passive or neutral mob that
 * dies in one can be charged to that player (see {@code MobExperienceHandler}). Also records who
 * placed each dispenser, so lava a dispenser places is charged to that player. The overworld's
 * instance also holds XP penalties owed by players who were offline when they were incurred.
 * Entries are only added for blocks placed after this was introduced, and are dropped lazily when
 * the block at the position is no longer lava or a campfire.
 */
public class HazardOwnerData extends SavedData {

    private final Map<Long, UUID> owners = new HashMap<>();
    private final Map<Long, UUID> dispenserOwners = new HashMap<>();
    private final Map<UUID, Integer> pendingPenalties = new HashMap<>();

    private record OwnerEntry(long pos, UUID owner) {
        static final Codec<OwnerEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("pos").forGetter(OwnerEntry::pos),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(OwnerEntry::owner)
        ).apply(instance, OwnerEntry::new));
    }

    private record PendingEntry(UUID player, int amount) {
        static final Codec<PendingEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("player").forGetter(PendingEntry::player),
                Codec.INT.fieldOf("amount").forGetter(PendingEntry::amount)
        ).apply(instance, PendingEntry::new));
    }

    public static final SavedDataType<HazardOwnerData> TYPE = new SavedDataType<>(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(com.terrainlab.TerrainLab.MODID, "hazard_owners"),
            HazardOwnerData::new,
            level -> RecordCodecBuilder.create(instance -> instance.group(
                    RecordCodecBuilder.point(level),
                    OwnerEntry.CODEC.listOf().fieldOf("owners").forGetter(
                            (HazardOwnerData d) -> d.owners.entrySet().stream()
                                    .map(e -> new OwnerEntry(e.getKey(), e.getValue())).toList()),
                    OwnerEntry.CODEC.listOf().optionalFieldOf("dispensers", java.util.List.of()).forGetter(
                            (HazardOwnerData d) -> d.dispenserOwners.entrySet().stream()
                                    .map(e -> new OwnerEntry(e.getKey(), e.getValue())).toList()),
                    PendingEntry.CODEC.listOf().optionalFieldOf("pending", java.util.List.of()).forGetter(
                            (HazardOwnerData d) -> d.pendingPenalties.entrySet().stream()
                                    .map(e -> new PendingEntry(e.getKey(), e.getValue())).toList())
            ).apply(instance, (lvl, owners, dispensers, pending) -> {
                HazardOwnerData data = new HazardOwnerData(lvl);
                for (OwnerEntry e : owners) {
                    data.owners.put(e.pos(), e.owner());
                }
                for (OwnerEntry e : dispensers) {
                    data.dispenserOwners.put(e.pos(), e.owner());
                }
                for (PendingEntry e : pending) {
                    data.pendingPenalties.put(e.player(), e.amount());
                }
                return data;
            }))
    );

    public HazardOwnerData() {
    }

    public HazardOwnerData(ServerLevel level) {
        // Level-aware constructor required by the SavedDataType factory; the level is unused.
    }

    public static HazardOwnerData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public void setOwner(BlockPos pos, UUID owner) {
        if (!owner.equals(this.owners.put(pos.asLong(), owner))) {
            setDirty();
        }
    }

    public UUID getOwner(BlockPos pos) {
        return this.owners.get(pos.asLong());
    }

    public void removeOwner(BlockPos pos) {
        if (this.owners.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    public void setDispenserOwner(BlockPos pos, UUID owner) {
        if (!owner.equals(this.dispenserOwners.put(pos.asLong(), owner))) {
            setDirty();
        }
    }

    public UUID getDispenserOwner(BlockPos pos) {
        return this.dispenserOwners.get(pos.asLong());
    }

    public void removeDispenserOwner(BlockPos pos) {
        if (this.dispenserOwners.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    public void addPendingPenalty(UUID player, int amount) {
        this.pendingPenalties.merge(player, amount, Integer::sum);
        setDirty();
    }

    /** Removes and returns the XP owed by this player, or 0. */
    public int takePendingPenalty(UUID player) {
        Integer owed = this.pendingPenalties.remove(player);
        if (owed == null) {
            return 0;
        }
        setDirty();
        return owed;
    }
}
