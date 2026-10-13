package com.terrainlab.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Global registry of active Redstone Portals across all dimensions. Pairing is not stored;
 * it is resolved when an entity enters a portal, as the nearest other entry on the same
 * channel by raw coordinate distance, regardless of dimension.
 * <p>
 * Always stored on the overworld's DataStorage so every dimension shares one instance.
 */
public class PortalNetworkSavedData extends SavedData {

    public record PortalEntry(ResourceKey<Level> dimension, BlockPos pos, int channel) {
        public static final Codec<PortalEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(PortalEntry::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(PortalEntry::pos),
                Codec.INT.fieldOf("channel").forGetter(PortalEntry::channel)
        ).apply(instance, PortalEntry::new));
    }

    public static final SavedDataType<PortalNetworkSavedData> TYPE = new SavedDataType<>(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(com.terrainlab.TerrainLab.MODID, "portal_network"),
            level -> new PortalNetworkSavedData(),
            level -> PortalEntry.CODEC.listOf()
                    .xmap(PortalNetworkSavedData::fromEntries, PortalNetworkSavedData::copyEntries)
    );

    private final List<PortalEntry> entries = new ArrayList<>();

    public PortalNetworkSavedData() {
    }

    private static PortalNetworkSavedData fromEntries(List<PortalEntry> loaded) {
        PortalNetworkSavedData data = new PortalNetworkSavedData();
        data.entries.addAll(loaded);
        return data;
    }

    private static List<PortalEntry> copyEntries(PortalNetworkSavedData data) {
        return new ArrayList<>(data.entries);
    }

    /** Always the overworld's instance, whichever level is passed. */
    public static PortalNetworkSavedData get(ServerLevel anyLevel) {
        return anyLevel.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Registers or updates this portal's channel. Replaces any existing entry at the same position+dimension. */
    public void activate(ResourceKey<Level> dimension, BlockPos pos, int channel) {
        entries.removeIf(e -> e.dimension().equals(dimension) && e.pos().equals(pos));
        entries.add(new PortalEntry(dimension, pos, channel));
        setDirty();
    }

    public void deactivate(ResourceKey<Level> dimension, BlockPos pos) {
        if (entries.removeIf(e -> e.dimension().equals(dimension) && e.pos().equals(pos))) {
            setDirty();
        }
    }

    /**
     * Nearest other active portal on the same channel, by raw coordinate distance across all
     * dimensions (no scaling, no same-dimension preference). Pairing need not be symmetric when
     * three or more portals share a channel.
     */
    public Optional<PortalEntry> findNearest(ResourceKey<Level> fromDimension, BlockPos fromPos, int channel) {
        PortalEntry nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (PortalEntry entry : entries) {
            if (entry.channel() != channel) continue;
            if (entry.dimension().equals(fromDimension) && entry.pos().equals(fromPos)) continue; // exclude self
            double distSq = entry.pos().distSqr(fromPos);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = entry;
            }
        }
        return Optional.ofNullable(nearest);
    }
}
