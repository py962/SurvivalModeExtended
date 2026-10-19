
package com.terrainlab.village;

import com.terrainlab.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only snapshot of a village for the bell GUI: population, tier inputs
 * and a per-villager row of profession, level and mood states.
 * Contains no menu or networking code.
 */
public final class VillageInfoSnapshot {

    private VillageInfoSnapshot() {
    }

    public record Row(
            String professionKey,
            int level,
            boolean scared,
            boolean tired,
            boolean trapped,
            boolean hungry,
            VillagerThermalState thermalState
    ) {
    }

    /**
     * Village summary. Happiness (0..1) and trade quality are computed live
     * with the same services the daily tier check uses.
     */
    public record Snapshot(
            BlockPos bellPos,
            String villageType,
            int population,
            double happiness,
            double dayAverageHappiness,
            int tradeQuality,
            VillageTier tier,
            List<Row> villagers
    ) {
    }

    /**
     * Finds the nearest discovered village bell to fromPos,
     * or returns empty if no bell is found.
     */
    public static Optional<Snapshot> captureNearest(
            ServerLevel level,
            BlockPos fromPos
    ) {
        List<BlockPos> bells =
                VillageDiscoveryService.discoverBellPositions(level);

        BlockPos nearest = bells.stream()
                .min(Comparator.comparingDouble(b -> b.distSqr(fromPos)))
                .orElse(null);

        return nearest == null
                ? Optional.empty()
                : Optional.of(capture(level, nearest));
    }

    /**
     * Captures a snapshot for a specific, already-known village bell.
     * The village type uses Minecraft's existing biome-to-villager-type
     * mapping at the bell position.
     */
    public static Snapshot capture(ServerLevel level, BlockPos bellPos) {
        VillageId id = new VillageId(level.dimension(), bellPos);
        VillageRecord record = VillageSavedData.get(level).getOrCreate(id);

        ResourceKey<VillagerType> villagerType =
                VillagerType.byBiome(level.getBiome(bellPos));

        String villageType = villagerType.identifier().getPath();

        Map<BlockPos, List<Villager>> membership =
                VillageMembershipService.assignVillagersToNearestVillage(
                        level,
                        List.of(bellPos)
                );

        List<Villager> members =
                membership.getOrDefault(bellPos, List.of());

        long gameTime = level.getGameTime();

        List<Row> rows = new ArrayList<>();

        for (Villager villager : members) {
            String professionKey =
                    VillagerProfessionAccess.getProfessionKey(villager);

            int level_ =
                    VillagerProfessionAccess.getLevel(villager);

            boolean scared =
                    VillagerFearService.isFearful(villager, gameTime);

            boolean tired =
                    VillagerTiredService.isTired(villager, gameTime);

            boolean trapped =
                    VillagerTrappedService.isTrapped(villager);

            VillagerThermalState thermal =
                    villager.getData(
                            ModAttachments.VILLAGER_THERMAL_STATE.get()
                    );

            boolean hungry =
                    VillagerMoodService.isHungry(villager);

            rows.add(new Row(
                    professionKey,
                    level_,
                    scared,
                    tired,
                    trapped,
                    hungry,
                    thermal
            ));
        }

        double happiness =
                VillageHappinessService.computeHappiness(members, gameTime);

        int tradeQuality =
                VillageTradeQualityService.computeScore(members);

        return new Snapshot(
                bellPos,
                villageType,
                members.size(),
                happiness,
                record.getDayAverageHappiness(),
                tradeQuality,
                record.getTier(),
                rows
        );
    }
}
