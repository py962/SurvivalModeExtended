package com.terrainlab.raid;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Extra raiders added on top of each vanilla raid wave: outlaws on horses and pirates in boats.
 *
 * <p>Each wave gets as many extras as it has pillagers before bonuses (4, 3, 3, 4, 4, 4, 2...). The
 * pirate share depends on how close the raid centre is to open water: up to {@link #MAX_PIRATE_SHARE}
 * at the shore, falling to none at {@link #WATER_RANGE} blocks. The rest are outlaws. Pirates stay in
 * their boats and fight from the water (steering in PirateBoatSteeringHandler).
 */
public final class RaidOutlawsPirates {
    /** Vanilla's pillagers per wave before bonuses, indexed by wave number (later waves reuse the last). */
    private static final int[] EXTRAS_PER_WAVE = {0, 4, 3, 3, 4, 4, 4, 2};

    /** Open water further than this from the raid centre brings no pirates. */
    private static final int WATER_RANGE = 64;
    private static final double MAX_PIRATE_SHARE = 0.8;
    private static final int PIRATES_PER_BOAT = 2;
    private static final int BOAT_SCATTER = 10;

    private RaidOutlawsPirates() {}

    public static void spawnExtras(Raid raid, ServerLevel level, BlockPos landPos) {
        int wave = raid.getGroupsSpawned();
        int extras = EXTRAS_PER_WAVE[Math.min(wave, EXTRAS_PER_WAVE.length - 1)];
        if (extras <= 0) return;

        BlockPos centre = raid.getCenter();
        BlockPos water = nearestOpenWater(level, centre);
        int pirates = 0;
        if (water != null) {
            double dx = water.getX() - centre.getX();
            double dz = water.getZ() - centre.getZ();
            double share = MAX_PIRATE_SHARE * Math.max(0.0, 1.0 - Math.sqrt(dx * dx + dz * dz) / WATER_RANGE);
            pirates = (int) Math.round(extras * share);
        }

        int placed = pirates > 0 ? spawnPirates(raid, level, wave, water, pirates) : 0;
        for (int i = placed; i < extras; i++) {
            spawnOutlaw(raid, level, wave, landPos);
        }
    }

    private static void spawnOutlaw(Raid raid, ServerLevel level, int wave, BlockPos pos) {
        Raider outlaw = ModEntities.OUTLAW_RAIDER.get().create(level, EntitySpawnReason.EVENT);
        Horse horse = EntityType.HORSE.create(level, EntitySpawnReason.EVENT);
        if (outlaw == null || horse == null) {
            TerrainLab.LOGGER.warn("Could not create a raid outlaw or horse at {}", pos);
            return;
        }
        raid.joinRaid(level, wave, outlaw, pos, false);
        if (outlaw.getCurrentRaid() != raid) return;

        horse.setPos(outlaw.getX(), outlaw.getY(), outlaw.getZ());
        horse.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
        horse.setTamed(false);
        horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
        horse.setPersistenceRequired();
        level.addFreshEntity(horse);
        // Forced: a raider on a horse is not a vanilla-allowed pairing.
        outlaw.startRiding(horse, true, true);
    }

    /** Returns how many pirates were actually placed. */
    private static int spawnPirates(Raid raid, ServerLevel level, int wave, BlockPos water, int pirates) {
        RandomSource random = level.getRandom();
        int placed = 0;
        while (placed < pirates) {
            BlockPos spot = scatterOnWater(level, water, random);
            Boat boat = EntityType.OAK_BOAT.create(level, EntitySpawnReason.EVENT);
            if (boat == null) break;
            float yaw = random.nextFloat() * 360.0F;
            boat.setPos(spot.getX() + 0.5, spot.getY() + 1, spot.getZ() + 0.5);
            boat.setYRot(yaw);
            level.addFreshEntity(boat);

            int aboard = 0;
            for (int seat = 0; seat < PIRATES_PER_BOAT && placed < pirates; seat++) {
                Raider pirate = ModEntities.PIRATE_RAIDER.get().create(level, EntitySpawnReason.EVENT);
                if (pirate == null) break;
                raid.joinRaid(level, wave, pirate, spot, false);
                if (pirate.getCurrentRaid() != raid) break;
                // Forced: a pirate in a boat is not a vanilla-allowed pairing.
                pirate.startRiding(boat, true, true);
                placed++;
                aboard++;
            }
            if (aboard == 0) {
                boat.discard();
                break;
            }
        }
        return placed;
    }

    private static BlockPos scatterOnWater(ServerLevel level, BlockPos water, RandomSource random) {
        for (int attempt = 0; attempt < 16; attempt++) {
            int x = water.getX() + random.nextInt(BOAT_SCATTER * 2 + 1) - BOAT_SCATTER;
            int z = water.getZ() + random.nextInt(BOAT_SCATTER * 2 + 1) - BOAT_SCATTER;
            BlockPos surface = surfaceWater(level, x, z);
            if (surface != null) return surface;
        }
        return water;
    }

    /** Nearest open water surface to {@code centre} within {@link #WATER_RANGE}, searched in square rings. */
    private static BlockPos nearestOpenWater(ServerLevel level, BlockPos centre) {
        for (int r = 0; r <= WATER_RANGE; r += 2) {
            BlockPos best = null;
            double bestDistSq = Double.MAX_VALUE;
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    BlockPos found = surfaceWater(level, centre.getX() + dx, centre.getZ() + dz);
                    if (found == null) continue;
                    double distSq = (double) dx * dx + (double) dz * dz;
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = found;
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /** The top water block at (x, z) if it and its 8 neighbours are open water a boat can sit on. */
    private static BlockPos surfaceWater(ServerLevel level, int x, int z) {
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) return null;
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                BlockPos p = new BlockPos(x + ox, y, z + oz);
                if (!level.getBlockState(p).is(Blocks.WATER) || !level.getBlockState(p.above()).isAir()) {
                    return null;
                }
            }
        }
        return new BlockPos(x, y, z);
    }
}
