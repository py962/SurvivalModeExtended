package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.entity.GraniteGolemEntity;
import com.terrainlab.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/**
 * Granite Golem: built from a T of granite capped with a carved pumpkin, and follows
 * nearby players holding gravel, stepping up or breaking through blocks in its way.
 */
public class ModGraniteGolemHandler {

    private static final double FOLLOW_SPEED = 0.15;
    private static final double FOLLOW_RANGE = 32.0;
    private static final double STOP_DISTANCE = 3.0;

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        BlockState placed = event.getPlacedBlock();

        if (!placed.is(Blocks.CARVED_PUMPKIN)) return;

        // TerrainLab.LOGGER.info("Carved pumpkin placed at {} - checking pattern", pos);

        BlockPos centre = pos.below();
        BlockPos stem = centre.below();

        // TerrainLab.LOGGER.info("  centre: {}", level.getBlockState(centre).getBlock().getDescriptionId());
        // TerrainLab.LOGGER.info("  stem: {}", level.getBlockState(stem).getBlock().getDescriptionId());
        // TerrainLab.LOGGER.info("  west: {}", level.getBlockState(centre.west()).getBlock().getDescriptionId());
        // TerrainLab.LOGGER.info("  east: {}", level.getBlockState(centre.east()).getBlock().getDescriptionId());
        // TerrainLab.LOGGER.info("  north: {}", level.getBlockState(centre.north()).getBlock().getDescriptionId());
        // TerrainLab.LOGGER.info("  south: {}", level.getBlockState(centre.south()).getBlock().getDescriptionId());

        if (isGraniteGolemPattern(level, pos)) {
            // TerrainLab.LOGGER.info("Pattern matched! Spawning granite golem.");
            spawnGraniteGolem(level, pos);
        } else {
            // TerrainLab.LOGGER.info("Pattern did not match.");
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 5 != 0) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (Player player : level.players()) {
                boolean holdingGravel =
                        player.getMainHandItem().is(Items.GRAVEL) ||
                                player.getOffhandItem().is(Items.GRAVEL);

                if (!holdingGravel) continue;

                List<GraniteGolemEntity> golems = level.getEntitiesOfClass(
                        GraniteGolemEntity.class,
                        new AABB(player.blockPosition()).inflate(FOLLOW_RANGE));

                for (GraniteGolemEntity golem : golems) {
                    if (golem.getGolemState() != GraniteGolemEntity.STATE_IDLE) continue;

                    double distSq = golem.distanceToSqr(player);

                    if (distSq < STOP_DISTANCE * STOP_DISTANCE) {
                        faceEntity(golem, player);
                        continue;
                    }

                    Vec3 toPlayer = player.position().subtract(golem.position()).normalize();
                    double newX = golem.getX() + toPlayer.x * FOLLOW_SPEED;
                    double newZ = golem.getZ() + toPlayer.z * FOLLOW_SPEED;
                    double newY = golem.getY();

                    BlockPos targetFeet = BlockPos.containing(newX, newY, newZ);
                    BlockPos targetHead = targetFeet.above();

                    if (!level.getBlockState(targetFeet).canBeReplaced()) {
                        // Try a one-block step up before breaking through.
                        BlockPos steppedFeet = targetFeet.above();
                        BlockPos steppedHead = steppedFeet.above();

                        if (level.getBlockState(steppedFeet).canBeReplaced()
                                && level.getBlockState(steppedHead).canBeReplaced()) {
                            newY += 1.0;
                            targetHead = steppedHead;
                        } else if (canBreak(level, targetFeet)) {
                            level.destroyBlock(targetFeet, true);
                        } else {
                            faceEntity(golem, player);
                            continue;
                        }
                    }

                    if (!level.getBlockState(targetHead).canBeReplaced()) {
                        if (!canBreak(level, targetHead)) {
                            faceEntity(golem, player);
                            continue;
                        }
                        level.destroyBlock(targetHead, true);
                    }

                    golem.setPos(newX, newY, newZ);
                    golem.setDeltaMovement(toPlayer.x * FOLLOW_SPEED,
                            golem.getDeltaMovement().y, toPlayer.z * FOLLOW_SPEED);
                    faceEntity(golem, player);
                }
            }
        }
    }

    /**
     * Ordinary blocks only: never unbreakable blocks (bedrock, portal frames, barriers) or
     * blocks with contents, and nothing at all when mob griefing is off.
     */
    private static boolean canBreak(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return level.getGameRules().get(GameRules.MOB_GRIEFING)
                && state.getDestroySpeed(level, pos) >= 0.0F
                && !state.hasBlockEntity();
    }

    private static void faceEntity(GraniteGolemEntity golem, Player player) {
        Vec3 toPlayer = player.position().subtract(golem.position());
        float yaw = (float)(Math.atan2(toPlayer.z, toPlayer.x) * (180.0 / Math.PI)) - 90.0f;
        golem.setYRot(yaw);
        golem.yHeadRot = yaw;
        golem.yBodyRot = yaw;
    }

    private static boolean isGraniteGolemPattern(ServerLevel level, BlockPos pumpkinPos) {
        BlockPos centre = pumpkinPos.below();
        BlockPos stem = centre.below();

        if (!isGranite(level, centre)) return false;
        if (!isGranite(level, stem)) return false;

        // Arms east-west
        if (isGranite(level, centre.west()) && isGranite(level, centre.east())) return true;
        // Arms north-south
        if (isGranite(level, centre.north()) && isGranite(level, centre.south())) return true;

        return false;
    }

    private static boolean isGranite(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.GRANITE)
                || level.getBlockState(pos).is(Blocks.POLISHED_GRANITE);
    }

    private static void spawnGraniteGolem(ServerLevel level, BlockPos pumpkinPos) {
        BlockPos centre = pumpkinPos.below();
        BlockPos stem = centre.below();

        float spawnYaw;
        boolean armsEastWest = isGranite(level, centre.west()) && isGranite(level, centre.east());
        if (armsEastWest) {
            spawnYaw = 0.0f;   // face south
        } else {
            spawnYaw = 90.0f;  // arms north-south, face west
        }

        level.removeBlock(pumpkinPos, false);
        level.removeBlock(centre, false);
        if (armsEastWest) {
            level.removeBlock(centre.west(), false);
            level.removeBlock(centre.east(), false);
        } else {
            level.removeBlock(centre.north(), false);
            level.removeBlock(centre.south(), false);
        }
        level.removeBlock(stem, false);

        GraniteGolemEntity golem = ModEntities.GRANITE_GOLEM.get()
                .create(level, EntitySpawnReason.EVENT);
        if (golem != null) {
            golem.snapTo(centre.getX() + 0.5, stem.getY(),
                    centre.getZ() + 0.5, spawnYaw, 0.0f);
            level.addFreshEntity(golem);
            // Same advancement trigger vanilla fires for iron/snow/copper golems.
            for (net.minecraft.server.level.ServerPlayer player : level.getEntitiesOfClass(
                    net.minecraft.server.level.ServerPlayer.class, golem.getBoundingBox().inflate(5.0))) {
                net.minecraft.advancements.triggers.CriteriaTriggers.SUMMONED_ENTITY.trigger(player, golem);
            }
            // TerrainLab.LOGGER.info("Granite golem spawned at {}", centre);
        } else {
            TerrainLab.LOGGER.error("Failed to create granite golem entity!");
        }
    }
}
