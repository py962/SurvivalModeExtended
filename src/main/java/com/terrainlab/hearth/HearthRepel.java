package com.terrainlab.hearth;

import com.terrainlab.TerrainLab;
import com.terrainlab.village.VillageId;
import com.terrainlab.village.VillageMembershipService;
import com.terrainlab.village.VillageSavedData;
import com.terrainlab.village.VillageTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** The gathering hearth of a tier 3+ village keeps ordinary hostiles 12 blocks away while lit. */
public final class HearthRepel {

    private HearthRepel() {
    }

    public static final int RADIUS = 12;

    public static final TagKey<EntityType<?>> REPELLED = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "hearth_repelled"));

    private static final long CACHE_TICKS = 100L;

    private record Cached(long gameTime, boolean active) {
    }

    private static final Map<GlobalPos, Cached> ACTIVE = new HashMap<>();

    public static boolean isRepelled(EntityType<?> type) {
        return type.builtInRegistryHolder().is(REPELLED);
    }

    /** Nearest repelling hearth within RADIUS of pos, or null. */
    public static @Nullable BlockPos find(ServerLevel level, BlockPos pos) {
        return level.getPoiManager().findClosest(
                holder -> holder.value() == ModHearth.HEARTH_POI.get(),
                hearth -> isActive(level, hearth),
                pos, RADIUS, PoiManager.Occupancy.ANY).orElse(null);
    }

    private static boolean isActive(ServerLevel level, BlockPos hearth) {
        GlobalPos key = GlobalPos.of(level.dimension(), hearth);
        long now = level.getGameTime();
        Cached cached = ACTIVE.get(key);
        if (cached == null || now - cached.gameTime() >= CACHE_TICKS || now < cached.gameTime()) {
            cached = new Cached(now, servesVillage(level, hearth));
            ACTIVE.put(key, cached);
        }
        return cached.active();
    }

    // Only the hearth the village actually gathers at counts, not any hearth inside it.
    private static boolean servesVillage(ServerLevel level, BlockPos hearth) {
        Optional<BlockPos> bell = VillageMembershipService.findMemberBell(level, hearth);
        if (bell.isEmpty()) {
            return false;
        }
        VillageTier tier = VillageSavedData.get(level)
                .getOrCreate(new VillageId(level.dimension(), bell.get())).getTier();
        if (tier.level() < VillageTier.TIER_3_VILLAGE.level()) {
            return false;
        }
        return HearthService.findHearth(level, bell.get()).filter(hearth::equals).isPresent();
    }
}
