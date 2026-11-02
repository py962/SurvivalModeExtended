
package com.terrainlab.event;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;
import net.minecraft.world.entity.EntityTypes;

/**
 * Enderman behaviours:
 *
 * 1. Placing chorus summons Endermen anchored to that spot.
 * 2. Three player-placed blocks forming a straight line with equal
 *    spacing, placed within three seconds, start a mimicry sequence.
 * 3. Nearby Endermen each place one block, continuing the pattern.
 */
public class ModEndermanHandler {

    // Original history and sequence limits: unchanged.
    private static final int HISTORY_LENGTH = 5;
    private static final int TASK_STEPS = 16;

    // Player pattern requirements.
    private static final int PLAYER_PATTERN_BLOCKS = 3;
    private static final long PATTERN_TIMEOUT_TICKS = 60L;

    private static final int WAIT_TIMEOUT = 3;
    private static final int SEARCH_RADIUS = 48;
    private static final int MAX_PATTERN_DIST = 3;

    private static final double ENDERMAN_WALK_SPEED = 1.0D;
    private static final double PLACEMENT_DISTANCE_SQ = 3.0D;

    private static final double TELEPORT_IMMEDIATELY_DIST_SQ = 8.0D * 8.0D;
    private static final int WALK_TIMEOUT = 3;
    private static final double TELEPORT_REACH_SQ = 9.0D;

    // Original player and Enderman histories.
    private static final Map<UUID, List<BlockPos>> playerBlockHistory =
            new HashMap<>();

    private static final Map<UUID, List<BlockPos>> endermanBlockHistory =
            new HashMap<>();

    // Separate three-block pattern history.
    private static final Map<UUID, List<BlockPos>> playerPatternHistory =
            new HashMap<>();

    // Time at which each player's current pattern started.
    private static final Map<UUID, Long> playerFirstBlockTime =
            new HashMap<>();

    public static final Map<UUID, BlockPos> chorusAnchors =
            new HashMap<>();

    private static final Map<UUID, MimicryTask> mimicryTasks =
            new HashMap<>();

    private static final Map<Long, MimicrySequence> mimicrySequences =
            new HashMap<>();

    private static long nextSequenceId = 1L;

    // Protected placements, mapped to their placement time per dimension.
    private static final Map<ResourceKey<Level>, Map<BlockPos, Long>>
            protectedPlacements = new HashMap<>();

    private static final long PLACEMENT_PROTECTION_TICKS = 24000L;

    /** True if Endermen must not pick up the block at this position. */
    public static boolean isProtected(Level level, BlockPos pos) {

        Map<BlockPos, Long> placements =
                protectedPlacements.get(level.dimension());

        if (placements == null) {
            return false;
        }

        Long placedAt = placements.get(pos);

        return placedAt != null
                && level.getGameTime() - placedAt <= PLACEMENT_PROTECTION_TICKS;
    }

    private static void protect(Level level, BlockPos pos) {

        protectedPlacements
                .computeIfAbsent(level.dimension(), k -> new HashMap<>())
                .put(pos.immutable(), level.getGameTime());
    }

    private static final List<BlockState> ENDERMAN_BLOCKS = List.of(
            Blocks.DIRT.defaultBlockState(),
            Blocks.GRASS_BLOCK.defaultBlockState(),
            Blocks.STONE.defaultBlockState(),
            Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.GRAVEL.defaultBlockState(),
            Blocks.SAND.defaultBlockState(),
            Blocks.END_STONE.defaultBlockState()
    );

    private static class MimicrySequence {

        private final long id;
        private final ServerLevel level;

        private final Set<UUID> participatingEndermen =
                new HashSet<>();

        private int remainingSteps;

        private MimicrySequence(long id, ServerLevel level) {

            this.id = id;
            this.level = level;
            this.remainingSteps = TASK_STEPS;
        }
    }

    private static class MimicryTask {

        private final long sequenceId;

        private final int dx;
        private final int dy;
        private final int dz;

        private BlockPos target;

        private int waitTicks;
        private int travelPasses;

        private MimicryTask(
                long sequenceId,
                BlockPos target,
                int dx,
                int dy,
                int dz) {

            this.sequenceId = sequenceId;
            this.target = target;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.waitTicks = 0;
            this.travelPasses = 0;
        }
    }

    private static class PendingAssignment {

        private final ServerLevel level;
        private final MimicrySequence sequence;
        private final BlockPos lastPos;

        private final int dx;
        private final int dy;
        private final int dz;

        private PendingAssignment(
                ServerLevel level,
                MimicrySequence sequence,
                BlockPos lastPos,
                int dx,
                int dy,
                int dz) {

            this.level = level;
            this.sequence = sequence;
            this.lastPos = lastPos;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {

        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = event.getPlacedBlock();

        // Only players start new sequences.
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (state.is(Blocks.CHORUS_PLANT)
                || state.is(Blocks.CHORUS_FLOWER)) {

            summonEndermen(level, pos);
        }

        protect(level, pos);

        UUID uuid = player.getUUID();

        // Preserve the original five-position player history.
        List<BlockPos> history =
                playerBlockHistory.computeIfAbsent(
                        uuid,
                        k -> new ArrayList<>()
                );

        history.add(pos.immutable());

        if (history.size() > HISTORY_LENGTH) {
            history.remove(0);
        }

        // Process the separate three-block mimicry pattern.
        processPlayerPattern(level, player, pos);
    }

    /**
     * Processes the player's three-block pattern.
     *
     * The three blocks must:
     * - Be placed within PATTERN_TIMEOUT_TICKS.
     * - Have identical displacement vectors.
     * - Have a non-zero spacing of at most MAX_PATTERN_DIST.
     *
     * This supports horizontal, vertical and diagonal lines.
     */
    private static void processPlayerPattern(
            ServerLevel level,
            Player player,
            BlockPos pos) {

        UUID uuid = player.getUUID();

        List<BlockPos> pattern =
                playerPatternHistory.computeIfAbsent(
                        uuid,
                        k -> new ArrayList<>()
                );

        long currentTime = level.getGameTime();

        // Start a new pattern if necessary.
        if (pattern.isEmpty()) {

            pattern.add(pos.immutable());

            playerFirstBlockTime.put(uuid, currentTime);

            return;
        }

        long firstBlockTime =
                playerFirstBlockTime.getOrDefault(
                        uuid,
                        currentTime
                );

        // Timeout: the latest placement becomes the new first block.
        if (currentTime - firstBlockTime > PATTERN_TIMEOUT_TICKS) {

            pattern.clear();
            pattern.add(pos.immutable());

            playerFirstBlockTime.put(uuid, currentTime);

            return;
        }

        // Second block: establish the direction and spacing.
        if (pattern.size() == 1) {

            BlockPos first = pattern.get(0);

            int dx = pos.getX() - first.getX();
            int dy = pos.getY() - first.getY();
            int dz = pos.getZ() - first.getZ();

            int distance =
                    Math.abs(dx) + Math.abs(dy) + Math.abs(dz);

            // Reject identical positions or excessive spacing.
            if (distance == 0 || distance > MAX_PATTERN_DIST) {

                pattern.clear();
                pattern.add(pos.immutable());

                playerFirstBlockTime.put(uuid, currentTime);

                return;
            }

            pattern.add(pos.immutable());

            return;
        }

        // Third block: it must continue in exactly the same direction.
        if (pattern.size() == 2) {

            BlockPos first = pattern.get(0);
            BlockPos second = pattern.get(1);

            int dx1 = second.getX() - first.getX();
            int dy1 = second.getY() - first.getY();
            int dz1 = second.getZ() - first.getZ();

            int dx2 = pos.getX() - second.getX();
            int dy2 = pos.getY() - second.getY();
            int dz2 = pos.getZ() - second.getZ();

            boolean validPattern =
                    dx1 == dx2
                            && dy1 == dy2
                            && dz1 == dz2;

            if (validPattern) {

                // All three blocks form an equally spaced straight line.
                startMimicrySequence(
                        level,
                        pos,
                        dx1,
                        dy1,
                        dz1
                );

                pattern.clear();
                playerFirstBlockTime.remove(uuid);

            } else {

                // Invalid continuation: restart using the latest block.
                pattern.clear();
                pattern.add(pos.immutable());

                playerFirstBlockTime.put(uuid, currentTime);
            }
        }
    }

    private static void summonEndermen(
            ServerLevel level,
            BlockPos pos) {

        int count = 1 + level.getRandom().nextInt(3);

        for (int i = 0; i < count; i++) {

            double spawnX =
                    pos.getX()
                            + (level.getRandom().nextDouble() - 0.5)
                            * 16;

            double spawnZ =
                    pos.getZ()
                            + (level.getRandom().nextDouble() - 0.5)
                            * 16;

            int spawnY =
                    level.getHeight(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            (int) spawnX,
                            (int) spawnZ
                    );

            Enderman enderman =
                    EntityTypes.ENDERMAN.create(
                            level,
                            EntitySpawnReason.EVENT
                    );

            if (enderman != null) {

                enderman.snapTo(
                        spawnX,
                        spawnY,
                        spawnZ,
                        level.getRandom().nextFloat() * 360,
                        0
                );

                level.addFreshEntity(enderman);

                chorusAnchors.put(
                        enderman.getUUID(),
                        pos.immutable()
                );
            }
        }
    }

    private static void startMimicrySequence(
            ServerLevel level,
            BlockPos lastPos,
            int dx,
            int dy,
            int dz) {

        long sequenceId = nextSequenceId++;

        MimicrySequence sequence =
                new MimicrySequence(sequenceId, level);

        mimicrySequences.put(sequenceId, sequence);

        assignNextEnderman(
                level,
                sequence,
                lastPos,
                dx,
                dy,
                dz
        );
    }

    private static void assignNextEnderman(
            ServerLevel level,
            MimicrySequence sequence,
            BlockPos lastPos,
            int dx,
            int dy,
            int dz) {

        if (sequence.remainingSteps <= 0) {
            finishSequence(sequence.id);
            return;
        }

        BlockPos target = lastPos.offset(dx, dy, dz);

        // If the target is occupied, try one further step.
        if (!level.getBlockState(target).canBeReplaced()) {

            BlockPos alternate = target.offset(dx, dy, dz);

            if (level.getBlockState(alternate).canBeReplaced()) {
                target = alternate;
            } else {
                finishSequence(sequence.id);
                return;
            }
        }

        List<Enderman> nearby =
                level.getEntitiesOfClass(
                        Enderman.class,
                        new AABB(
                                lastPos.getX() - SEARCH_RADIUS,
                                lastPos.getY() - SEARCH_RADIUS,
                                lastPos.getZ() - SEARCH_RADIUS,
                                lastPos.getX() + SEARCH_RADIUS,
                                lastPos.getY() + SEARCH_RADIUS,
                                lastPos.getZ() + SEARCH_RADIUS
                        )
                );

        Enderman chosen = null;
        double closestDistance = Double.MAX_VALUE;

        for (Enderman enderman : nearby) {

            UUID uuid = enderman.getUUID();

            if (sequence.participatingEndermen.contains(uuid)) {
                continue;
            }

            if (mimicryTasks.containsKey(uuid)) {
                continue;
            }

            double distance =
                    enderman.distanceToSqr(
                            target.getX() + 0.5D,
                            target.getY() + 0.5D,
                            target.getZ() + 0.5D
                    );

            if (distance < closestDistance) {
                closestDistance = distance;
                chosen = enderman;
            }
        }

        if (chosen == null) {
            finishSequence(sequence.id);
            return;
        }

        UUID endermanUUID = chosen.getUUID();

        sequence.participatingEndermen.add(endermanUUID);

        BlockState carried =
                pickBlockForEnderman(
                        level,
                        chosen.blockPosition(),
                        chosen
                );

        if (carried == null || carried.isAir()) {

            sequence.participatingEndermen.remove(endermanUUID);

            finishSequence(sequence.id);

            return;
        }

        mimicryTasks.put(
                endermanUUID,
                new MimicryTask(
                        sequence.id,
                        target.immutable(),
                        dx,
                        dy,
                        dz
                )
        );

        chosen.getNavigation().moveTo(
                target.getX() + 0.5D,
                target.getY() + 0.5D,
                target.getZ() + 0.5D,
                ENDERMAN_WALK_SPEED
        );
    }

    private static BlockState pickBlockForEnderman(
            ServerLevel level,
            BlockPos near,
            Enderman enderman) {

        BlockState carried = enderman.getCarriedBlock();

        if (carried != null && !carried.isAir()) {
            return carried;
        }

        // Only take blocks from the world when mob griefing is allowed.
        Iterable<BlockPos> candidates =
                level.getGameRules().get(GameRules.MOB_GRIEFING)
                        ? BlockPos.betweenClosed(
                        near.offset(-4, -2, -4),
                        near.offset(4, 2, 4)
                )
                        : List.of();

        for (BlockPos candidate : candidates) {

            BlockState state = level.getBlockState(candidate);

            if (state.isAir()) {
                continue;
            }

            if (isProtected(level, candidate)) {
                continue;
            }

            if (!state.is(
                    net.minecraft.tags.BlockTags.ENDERMAN_HOLDABLE)) {
                continue;
            }

            level.removeBlock(candidate, false);

            enderman.setCarriedBlock(state);

            enderman.swing(
                    InteractionHand.MAIN_HAND,
                    net.minecraft.world.item.component.SwingAnimation.DEFAULT,
                    true
            );

            return state;
        }

        // Fallback when no holdable block is nearby.
        BlockState fallback =
                ENDERMAN_BLOCKS.get(
                        level.getRandom().nextInt(
                                ENDERMAN_BLOCKS.size()
                        )
                );

        enderman.setCarriedBlock(fallback);

        enderman.swing(
                InteractionHand.MAIN_HAND,
                net.minecraft.world.item.component.SwingAnimation.DEFAULT,
                true
        );

        return fallback;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {

        if (event.getServer().getTickCount() % 20 != 0) {
            return;
        }

        // Expire placement protection older than a day.
        for (Map.Entry<ResourceKey<Level>, Map<BlockPos, Long>> dimension :
                protectedPlacements.entrySet()) {

            ServerLevel dimensionLevel =
                    event.getServer().getLevel(dimension.getKey());

            long currentTime = dimensionLevel != null
                    ? dimensionLevel.getGameTime()
                    : event.getServer().overworld().getGameTime();

            dimension.getValue().values().removeIf(placedAt ->
                    currentTime - placedAt > PLACEMENT_PROTECTION_TICKS);
        }

        protectedPlacements.values().removeIf(Map::isEmpty);

        List<PendingAssignment> pendingAssignments =
                new ArrayList<>();

        Iterator<Map.Entry<UUID, MimicryTask>> iterator =
                mimicryTasks.entrySet().iterator();

        while (iterator.hasNext()) {

            Map.Entry<UUID, MimicryTask> entry = iterator.next();

            UUID endermanUUID = entry.getKey();

            MimicryTask task = entry.getValue();

            Entity entity =
                    findEntityInServer(
                            event,
                            endermanUUID
                    );

            // Enderman unloaded or dead: count the step and hand off.
            if (!(entity instanceof Enderman enderman)) {

                iterator.remove();

                MimicrySequence sequence =
                        mimicrySequences.get(task.sequenceId);

                if (sequence != null) {

                    sequence.remainingSteps--;

                    pendingAssignments.add(
                            new PendingAssignment(
                                    sequence.level,
                                    sequence,
                                    task.target,
                                    task.dx,
                                    task.dy,
                                    task.dz
                            )
                    );
                }

                continue;
            }

            ServerLevel level = (ServerLevel) enderman.level();

            BlockPos target = task.target;

            // If the target became occupied, try one further step.
            if (!level.getBlockState(target).canBeReplaced()) {

                BlockPos alternate =
                        target.offset(
                                task.dx,
                                task.dy,
                                task.dz
                        );

                if (level.getBlockState(alternate).canBeReplaced()) {

                    task.target = alternate.immutable();
                    target = task.target;

                } else {

                    enderman.setCarriedBlock(null);
                    enderman.getNavigation().stop();

                    iterator.remove();

                    MimicrySequence sequence =
                            mimicrySequences.get(task.sequenceId);

                    if (sequence != null) {

                        sequence.remainingSteps--;

                        pendingAssignments.add(
                                new PendingAssignment(
                                        level,
                                        sequence,
                                        target.immutable(),
                                        task.dx,
                                        task.dy,
                                        task.dz
                                )
                        );
                    }

                    continue;
                }
            }

            BlockState carried = enderman.getCarriedBlock();

            if (carried == null || carried.isAir()) {

                carried =
                        pickBlockForEnderman(
                                level,
                                enderman.blockPosition(),
                                enderman
                        );

                if (carried == null || carried.isAir()) {

                    task.waitTicks++;

                    if (task.waitTicks > WAIT_TIMEOUT) {

                        enderman.getNavigation().stop();

                        iterator.remove();

                        MimicrySequence sequence =
                                mimicrySequences.get(task.sequenceId);

                        if (sequence != null) {

                            sequence.remainingSteps--;

                            pendingAssignments.add(
                                    new PendingAssignment(
                                            level,
                                            sequence,
                                            target.immutable(),
                                            task.dx,
                                            task.dy,
                                            task.dz
                                    )
                            );
                        }
                    }

                    continue;
                }
            }

            double distanceSq =
                    enderman.distanceToSqr(
                            target.getX() + 0.5D,
                            target.getY() + 0.5D,
                            target.getZ() + 0.5D
                    );

            double reachSq = PLACEMENT_DISTANCE_SQ;

            if (distanceSq > PLACEMENT_DISTANCE_SQ) {

                task.travelPasses++;

                boolean farAway =
                        distanceSq > TELEPORT_IMMEDIATELY_DIST_SQ;

                boolean stuck =
                        task.travelPasses > WALK_TIMEOUT
                                || (task.travelPasses > 1
                                && enderman.getNavigation().isDone());

                if (farAway || stuck) {

                    if (teleportNextTo(level, enderman, target)) {

                        reachSq = TELEPORT_REACH_SQ;

                        distanceSq =
                                enderman.distanceToSqr(
                                        target.getX() + 0.5D,
                                        target.getY() + 0.5D,
                                        target.getZ() + 0.5D
                                );

                    } else {

                        enderman.getNavigation().stop();

                        iterator.remove();

                        MimicrySequence sequence =
                                mimicrySequences.get(task.sequenceId);

                        if (sequence != null) {

                            sequence.remainingSteps--;

                            pendingAssignments.add(
                                    new PendingAssignment(
                                            level,
                                            sequence,
                                            target.immutable(),
                                            task.dx,
                                            task.dy,
                                            task.dz
                                    )
                            );
                        }

                        continue;
                    }

                } else {

                    enderman.getNavigation().moveTo(
                            target.getX() + 0.5D,
                            target.getY() + 0.5D,
                            target.getZ() + 0.5D,
                            ENDERMAN_WALK_SPEED
                    );
                }
            }

            if (distanceSq <= reachSq) {

                carried = enderman.getCarriedBlock();

                if (carried == null || carried.isAir()) {
                    continue;
                }

                if (!level.getBlockState(target).canBeReplaced()) {
                    continue;
                }

                if (!level.getGameRules().get(GameRules.MOB_GRIEFING)) {
                    continue;
                }

                enderman.getNavigation().stop();

                enderman.swing(
                        InteractionHand.MAIN_HAND,
                        net.minecraft.world.item.component.SwingAnimation.DEFAULT,
                        true
                );

                level.setBlock(target, carried, 3);

                // Protect the Enderman's placement like a player placement.
                protect(level, target);

                enderman.setCarriedBlock(null);

                com.terrainlab.advancement.ModAdvancements.awardNearby(
                        level,
                        Vec3.atCenterOf(target),
                        32.0,
                        "enderman_sequence"
                );

                iterator.remove();

                MimicrySequence sequence =
                        mimicrySequences.get(task.sequenceId);

                if (sequence == null) {
                    continue;
                }

                sequence.remainingSteps--;

                List<BlockPos> history =
                        endermanBlockHistory.computeIfAbsent(
                                enderman.getUUID(),
                                k -> new ArrayList<>()
                        );

                history.add(target.immutable());

                if (history.size() > HISTORY_LENGTH) {
                    history.remove(0);
                }

                // The next placement must be made by a different Enderman.
                if (sequence.remainingSteps > 0) {

                    pendingAssignments.add(
                            new PendingAssignment(
                                    level,
                                    sequence,
                                    target.immutable(),
                                    task.dx,
                                    task.dy,
                                    task.dz
                            )
                    );

                } else {

                    finishSequence(sequence.id);
                }
            }
        }

        // Assign new tasks after finishing iteration.
        for (PendingAssignment assignment : pendingAssignments) {

            if (!mimicrySequences.containsKey(
                    assignment.sequence.id)) {
                continue;
            }

            assignNextEnderman(
                    assignment.level,
                    assignment.sequence,
                    assignment.lastPos,
                    assignment.dx,
                    assignment.dy,
                    assignment.dz
            );
        }

        // Drop anchors whose Enderman is no longer loaded in any dimension.
        chorusAnchors.entrySet().removeIf(entry ->
                !(findEntityInServer(event, entry.getKey()) instanceof Enderman));

        for (ServerLevel level : event.getServer().getAllLevels()) {

            // Pull chorus-summoned Endermen toward their anchor unless busy.
            for (Map.Entry<UUID, BlockPos> entry : chorusAnchors.entrySet()) {

                Entity entity =
                        level.getEntities().get(entry.getKey());

                if (!(entity instanceof Enderman enderman)) {
                    continue;
                }

                if (mimicryTasks.containsKey(enderman.getUUID())) {
                    continue;
                }

                BlockPos anchor = entry.getValue();

                double distSq =
                        enderman.distanceToSqr(
                                anchor.getX() + 0.5,
                                anchor.getY() + 0.5,
                                anchor.getZ() + 0.5
                        );

                if (distSq > 32 * 32) {

                    double angle =
                            level.getRandom().nextDouble() * Math.PI * 2;

                    double radius =
                            8 + level.getRandom().nextDouble() * 8;

                    double tx =
                            anchor.getX() + Math.cos(angle) * radius;

                    double tz =
                            anchor.getZ() + Math.sin(angle) * radius;

                    int ty =
                            level.getHeight(
                                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                    (int) tx,
                                    (int) tz
                            );

                    enderman.snapTo(
                            tx,
                            ty,
                            tz,
                            enderman.getYRot(),
                            0
                    );
                }
            }
        }
    }

    /**
     * Teleports the Enderman onto the ground beside the target.
     * Tries the four sides in turn.
     */
    private static boolean teleportNextTo(
            ServerLevel level,
            Enderman enderman,
            BlockPos target) {

        int[][] sides = {
                {1, 0},
                {-1, 0},
                {0, 1},
                {0, -1}
        };

        for (int[] side : sides) {

            Vec3 from = enderman.position();

            double x = target.getX() + side[0] + 0.5D;
            double z = target.getZ() + side[1] + 0.5D;

            if (!enderman.randomTeleport(
                    x,
                    target.getY() + 1.0D,
                    z,
                    true,
                    floorState -> false)) {
                continue;
            }

            level.gameEvent(
                    GameEvent.TELEPORT,
                    from,
                    GameEvent.Context.of(enderman)
            );

            if (!enderman.isSilent()) {

                level.playSound(
                        null,
                        from.x,
                        from.y,
                        from.z,
                        SoundEvents.ENDERMAN_TELEPORT,
                        enderman.getSoundSource(),
                        1.0F,
                        1.0F
                );

                enderman.playSound(
                        SoundEvents.ENDERMAN_TELEPORT,
                        1.0F,
                        1.0F
                );
            }

            double distanceSq =
                    enderman.distanceToSqr(
                            target.getX() + 0.5D,
                            target.getY() + 0.5D,
                            target.getZ() + 0.5D
                    );

            if (distanceSq <= TELEPORT_REACH_SQ) {
                return true;
            }
        }

        return false;
    }

    private static Entity findEntityInServer(
            ServerTickEvent.Post event,
            UUID uuid) {

        for (ServerLevel level : event.getServer().getAllLevels()) {

            Entity entity = level.getEntities().get(uuid);

            if (entity != null) {
                return entity;
            }
        }

        return null;
    }

    private static void finishSequence(long sequenceId) {

        mimicrySequences.remove(sequenceId);
    }
}
