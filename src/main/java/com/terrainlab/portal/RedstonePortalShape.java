package com.terrainlab.portal;

import com.terrainlab.block.FieldStoneBlock;
import com.terrainlab.block.RedstonePortalBlock;
import com.terrainlab.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Redstone Portal frame scanner and filler. Vanilla's PortalShape hardcodes obsidian, so
 * this is a separate implementation.
 * <p>
 * A valid portal is a complete rectangle of air (or existing portal cells) in one vertical
 * plane, bordered on every cell including corners by FieldStoneBlock, with interior size
 * within [MIN_SIZE, MAX_SIZE] on both axes.
 * <p>
 * Known limitation of {@link #recomputeFromFrame}: a corner frame block has no in-plane
 * interior neighbour, so powering only a corner does not activate the portal.
 */
public final class RedstonePortalShape {

    public static final int MIN_SIZE = 1;   // interior width/height, inclusive
    public static final int MAX_SIZE = 21;  // interior width/height, inclusive - roughly Nether-portal scale

    /**
     * Re-entrancy guard: fill/clear and lit-syncing call Level#setBlock, whose neighbour
     * notifications would re-enter RedstonePortalHandler.onNeighborNotify unboundedly. Set and
     * cleared only around that handler's whole body. Server thread only.
     */
    private static boolean mutating = false;

    public static boolean isMutating() {
        return mutating;
    }

    public static void setMutating(boolean value) {
        mutating = value;
    }

    private RedstonePortalShape() {
    }

    /** Call whenever a FieldStoneBlock's neighborhood changes (redstone power, most often). */
    public static void recomputeFromFrame(ServerLevel level, BlockPos framePos) {
        Rectangle rect = scanFromFrame(level, framePos, true);
        if (rect == null) rect = scanFromFrame(level, framePos, false);
        if (rect == null) return; // this frame block isn't (yet) part of a complete rectangle

        // The whole frame's strongest signal, so another powered edge keeps the portal open.
        int power = maxFrameSignal(level, rect);

        if (power > 0) {
            fill(level, rect, power);
        } else {
            clear(level, rect);
        }
    }

    /** Call whenever a RedstonePortalBlock's neighborhood changes - re-validates it's still framed and powered. */
    public static void validatePortalCell(ServerLevel level, BlockPos portalPos) {
        BlockState state = level.getBlockState(portalPos);
        if (!(state.getBlock() instanceof RedstonePortalBlock)) return;
        boolean fixedX = state.getValue(RedstonePortalBlock.AXIS) == Direction.Axis.Z;

        Rectangle rect = scanFromInterior(level, portalPos, fixedX);
        if (rect == null) {
            // Frame no longer forms a complete enclosure (most likely a frame block was just
            // broken) - sweep up whatever portal cells are still connected and clear them.
            Rectangle broken = scanConnectedPortalCells(level, portalPos, fixedX);
            if (broken != null) clear(level, broken);
            return;
        }

        int power = maxFrameSignal(level, rect);
        if (power <= 0) {
            clear(level, rect);
        } else {
            fill(level, rect, power); // re-stamp in case the channel (signal strength) changed
        }
    }

    /**
     * Resolves the registered origin of the portal containing {@code pos} (the same cell fill()
     * registers). Callers pass this, not the entity's position, to PortalNetworkSavedData#findNearest
     * so the portal's own entry is excluded.
     */
    public static Optional<BlockPos> originOf(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RedstonePortalBlock)) return Optional.empty();

        boolean fixedX = state.getValue(RedstonePortalBlock.AXIS) == Direction.Axis.Z;
        Rectangle rect = scanFromInterior(level, pos, fixedX);
        if (rect == null) return Optional.empty(); // frame no longer complete

        return Optional.of(interiorOrigin(rect));
    }

    // ---- internals ----

    private record Rectangle(boolean fixedX, int fixed, int minA, int maxA, int minY, int maxY) {
    }

    private static Rectangle scanFromFrame(ServerLevel level, BlockPos framePos, boolean fixedX) {
        Direction[] dirs = fixedX
                ? new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN}
                : new Direction[]{Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN};
        for (Direction dir : dirs) {
            BlockPos neighbor = framePos.relative(dir);
            BlockState neighborState = level.getBlockState(neighbor);
            if (neighborState.isAir() || neighborState.getBlock() instanceof RedstonePortalBlock) {
                Rectangle rect = scanFromInterior(level, neighbor, fixedX);
                if (rect != null) return rect;
            }
        }
        return null;
    }

    /** Flood-fills from a known-interior cell and validates the resulting rectangle plus its frame border. */
    private static Rectangle scanFromInterior(ServerLevel level, BlockPos seed, boolean fixedX) {
        int fixed = fixedX ? seed.getX() : seed.getZ();
        // `visited` also holds probed border cells; `interiorCells` is the true interior count.
        Set<Long> visited = new HashSet<>();
        Set<Long> interiorCells = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed);
        visited.add(seed.asLong());
        interiorCells.add(seed.asLong());

        int minA = fixedX ? seed.getZ() : seed.getX();
        int maxA = minA;
        int minY = seed.getY();
        int maxY = minY;

        Direction[] inPlane = fixedX
                ? new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN}
                : new Direction[]{Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN};

        while (!queue.isEmpty()) {
            if (interiorCells.size() > MAX_SIZE * MAX_SIZE) return null; // leaking out - not actually enclosed
            BlockPos cur = queue.poll();

            int a = fixedX ? cur.getZ() : cur.getX();
            int y = cur.getY();
            minA = Math.min(minA, a);
            maxA = Math.max(maxA, a);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);

            for (Direction dir : inPlane) {
                BlockPos next = cur.relative(dir);
                if ((fixedX && next.getX() != fixed) || (!fixedX && next.getZ() != fixed)) continue;
                if (!visited.add(next.asLong())) continue;
                BlockState nextState = level.getBlockState(next);
                if (nextState.isAir() || nextState.getBlock() instanceof RedstonePortalBlock) {
                    interiorCells.add(next.asLong());
                    queue.add(next);
                } // else: border cell, don't traverse into it (but stays marked visited above)
            }
        }

        int width = maxA - minA + 1;
        int height = maxY - minY + 1;
        if (width < MIN_SIZE || width > MAX_SIZE || height < MIN_SIZE || height > MAX_SIZE) return null;

        // Interior must fill its whole bounding box, otherwise the frame is jagged or has a gap.
        if (interiorCells.size() != width * height) return null;

        Rectangle rect = new Rectangle(fixedX, fixed, minA, maxA, minY, maxY);
        return bordersAreFrame(level, rect) ? rect : null;
    }

    /** Loosened version used only for teardown: floods connected portal cells with no frame requirement. */
    private static Rectangle scanConnectedPortalCells(ServerLevel level, BlockPos seed, boolean fixedX) {
        int fixed = fixedX ? seed.getX() : seed.getZ();
        Set<Long> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed);
        visited.add(seed.asLong());
        int minA = fixedX ? seed.getZ() : seed.getX();
        int maxA = minA;
        int minY = seed.getY();
        int maxY = minY;
        Direction[] inPlane = fixedX
                ? new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN}
                : new Direction[]{Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN};

        while (!queue.isEmpty()) {
            if (visited.size() > MAX_SIZE * MAX_SIZE) break;
            BlockPos cur = queue.poll();
            int a = fixedX ? cur.getZ() : cur.getX();
            int y = cur.getY();
            minA = Math.min(minA, a);
            maxA = Math.max(maxA, a);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            for (Direction dir : inPlane) {
                BlockPos next = cur.relative(dir);
                if ((fixedX && next.getX() != fixed) || (!fixedX && next.getZ() != fixed)) continue;
                if (!visited.add(next.asLong())) continue;
                if (level.getBlockState(next).getBlock() instanceof RedstonePortalBlock) queue.add(next);
            }
        }
        return new Rectangle(fixedX, fixed, minA, maxA, minY, maxY);
    }

    private static boolean bordersAreFrame(ServerLevel level, Rectangle rect) {
        for (int a = rect.minA() - 1; a <= rect.maxA() + 1; a++) {
            for (int y = rect.minY() - 1; y <= rect.maxY() + 1; y++) {
                boolean onBorder = a == rect.minA() - 1 || a == rect.maxA() + 1 || y == rect.minY() - 1 || y == rect.maxY() + 1;
                if (!onBorder) continue;
                if (!(level.getBlockState(borderPos(rect, a, y)).getBlock() instanceof FieldStoneBlock)) return false;
            }
        }
        return true;
    }

    private static int maxFrameSignal(ServerLevel level, Rectangle rect) {
        int best = 0;
        for (int a = rect.minA() - 1; a <= rect.maxA() + 1; a++) {
            for (int y = rect.minY() - 1; y <= rect.maxY() + 1; y++) {
                boolean onBorder = a == rect.minA() - 1 || a == rect.maxA() + 1 || y == rect.minY() - 1 || y == rect.maxY() + 1;
                if (!onBorder) continue;
                best = Math.max(best, level.getBestNeighborSignal(borderPos(rect, a, y)));
            }
        }
        return best;
    }

    private static BlockPos borderPos(Rectangle rect, int a, int y) {
        return rect.fixedX() ? new BlockPos(rect.fixed(), y, a) : new BlockPos(a, y, rect.fixed());
    }

    private static BlockPos interiorPos(Rectangle rect, int a, int y) {
        return rect.fixedX() ? new BlockPos(rect.fixed(), y, a) : new BlockPos(a, y, rect.fixed());
    }

    private static void fill(ServerLevel level, Rectangle rect, int channel) {
        Direction.Axis axis = rect.fixedX() ? Direction.Axis.Z : Direction.Axis.X;
        BlockState portalState = ModBlocks.REDSTONE_PORTAL.get().defaultBlockState()
                .setValue(RedstonePortalBlock.AXIS, axis)
                .setValue(RedstonePortalBlock.CHANNEL, channel);

        for (int a = rect.minA(); a <= rect.maxA(); a++) {
            for (int y = rect.minY(); y <= rect.maxY(); y++) {
                BlockPos pos = interiorPos(rect, a, y);
                if (!level.getBlockState(pos).equals(portalState)) {
                    level.setBlock(pos, portalState, 3);
                }
            }
        }
        setFrameLit(level, rect, true); // whole frame glows together
        PortalNetworkSavedData.get(level).activate(level.dimension(), interiorOrigin(rect), channel);
    }

    private static void clear(ServerLevel level, Rectangle rect) {
        for (int a = rect.minA(); a <= rect.maxA(); a++) {
            for (int y = rect.minY(); y <= rect.maxY(); y++) {
                BlockPos pos = interiorPos(rect, a, y);
                if (level.getBlockState(pos).getBlock() instanceof RedstonePortalBlock) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        resyncFrameLit(level, rect); // directly powered cells stay lit
        PortalNetworkSavedData.get(level).deactivate(level.dimension(), interiorOrigin(rect));
    }

    /** Forces every FieldStoneBlock on the rectangle's border to the given LIT value. */
    private static void setFrameLit(ServerLevel level, Rectangle rect, boolean lit) {
        for (int a = rect.minA() - 1; a <= rect.maxA() + 1; a++) {
            for (int y = rect.minY() - 1; y <= rect.maxY() + 1; y++) {
                boolean onBorder = a == rect.minA() - 1 || a == rect.maxA() + 1 || y == rect.minY() - 1 || y == rect.maxY() + 1;
                if (!onBorder) continue;
                BlockPos pos = borderPos(rect, a, y);
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof FieldStoneBlock && state.getValue(FieldStoneBlock.LIT) != lit) {
                    level.setBlock(pos, state.setValue(FieldStoneBlock.LIT, lit), 3);
                }
            }
        }
    }

    /**
     * On deactivation, recomputes each border cell's LIT from its own redstone signal, so a
     * directly powered Field Stone keeps glowing as it would outside a portal.
     */
    private static void resyncFrameLit(ServerLevel level, Rectangle rect) {
        for (int a = rect.minA() - 1; a <= rect.maxA() + 1; a++) {
            for (int y = rect.minY() - 1; y <= rect.maxY() + 1; y++) {
                boolean onBorder = a == rect.minA() - 1 || a == rect.maxA() + 1 || y == rect.minY() - 1 || y == rect.maxY() + 1;
                if (!onBorder) continue;
                BlockPos pos = borderPos(rect, a, y);
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof FieldStoneBlock) {
                    boolean powered = level.getBestNeighborSignal(pos) > 0;
                    if (state.getValue(FieldStoneBlock.LIT) != powered) {
                        level.setBlock(pos, state.setValue(FieldStoneBlock.LIT, powered), 3);
                    }
                }
            }
        }
    }

    private static BlockPos interiorOrigin(Rectangle rect) {
        return interiorPos(rect, rect.minA(), rect.minY());
    }
}
