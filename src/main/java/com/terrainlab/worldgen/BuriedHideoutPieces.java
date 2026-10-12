package com.terrainlab.worldgen;

import com.terrainlab.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.List;

/** Pieces of {@link BuriedHideoutStructure}: the hideout template and its two tunnels. */
public final class BuriedHideoutPieces {

    static final int FLAG_ASCENDING = 1;  // rear: this rail climbs back towards the chamber
    static final int FLAG_BRIDGE = 2;     // rear: track carried over a drop
    static final int FLAG_STAIR = 4;      // front: staircase step down from a cliff mouth
    static final int FLAG_CAVE = 8;       // rear: track crossing a small cave
    static final int FLAG_INSIDE = 16;    // rear: re-laid track inside the template's corridor

    private BuriedHideoutPieces() {
    }

    // ---- chamber -------------------------------------------------------------------------------

    public static class ChamberPiece extends TemplateStructurePiece {

        public ChamberPiece(StructureTemplateManager templates, BlockPos origin, Rotation rotation) {
            super(ModStructures.HIDEOUT_CHAMBER.get(), 0, templates, BuriedHideoutStructure.TEMPLATE,
                    BuriedHideoutStructure.TEMPLATE.toString(), makeSettings(rotation), origin);
        }

        public ChamberPiece(StructureTemplateManager templates, CompoundTag tag) {
            super(ModStructures.HIDEOUT_CHAMBER.get(), tag, templates,
                    id -> makeSettings(tag.read("Rot", Rotation.LEGACY_CODEC).orElseThrow()));
        }

        private static StructurePlaceSettings makeSettings(Rotation rotation) {
            return new StructurePlaceSettings()
                    .setRotation(rotation)
                    .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK)
                    .setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.store("Rot", Rotation.LEGACY_CODEC, this.placeSettings.getRotation());
        }

        @Override
        protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, RandomSource random,
                                        BoundingBox box) {
        }
    }

    // ---- cover -------------------------------------------------------------------------------

    /**
     * Fills dips in the hill over and around the chamber. The room is placed by a low percentile of
     * the ground rather than its lowest point, so where the ground dips the roof or a wall could be
     * left in the open. This tops the roof up to COVER blocks and banks a sloping berm round the
     * sides (COVER above the roof at the wall, falling 2 blocks per block outwards, out to
     * {@link #BERM} blocks), only replacing air above the existing ground and copying the hill's own
     * surface block. Runs after the chamber and before the tunnels, so the tunnels cut through it.
     */
    public static class CoverPiece extends StructurePiece {

        static final int BERM = 4;

        private final int chamberMinX;
        private final int chamberMinZ;
        private final int chamberMaxX;
        private final int chamberMaxZ;
        private final int roofY;

        public CoverPiece(BoundingBox chamber) {
            super(ModStructures.HIDEOUT_COVER.get(), 0, new BoundingBox(
                    chamber.minX() - BERM, chamber.minY(), chamber.minZ() - BERM,
                    chamber.maxX() + BERM, chamber.maxY() + BuriedHideoutStructure.COVER, chamber.maxZ() + BERM));
            this.chamberMinX = chamber.minX();
            this.chamberMinZ = chamber.minZ();
            this.chamberMaxX = chamber.maxX();
            this.chamberMaxZ = chamber.maxZ();
            this.roofY = chamber.maxY();
        }

        public CoverPiece(CompoundTag tag) {
            super(ModStructures.HIDEOUT_COVER.get(), tag);
            this.chamberMinX = tag.getIntOr("CX0", 0);
            this.chamberMinZ = tag.getIntOr("CZ0", 0);
            this.chamberMaxX = tag.getIntOr("CX1", 0);
            this.chamberMaxZ = tag.getIntOr("CZ1", 0);
            this.roofY = tag.getIntOr("Roof", 0);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putInt("CX0", chamberMinX);
            tag.putInt("CZ0", chamberMinZ);
            tag.putInt("CX1", chamberMaxX);
            tag.putInt("CZ1", chamberMaxZ);
            tag.putInt("Roof", roofY);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                                RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = Math.max(boundingBox.minX(), chunkBox.minX()); x <= Math.min(boundingBox.maxX(), chunkBox.maxX()); x++) {
                for (int z = Math.max(boundingBox.minZ(), chunkBox.minZ()); z <= Math.min(boundingBox.maxZ(), chunkBox.maxZ()); z++) {
                    int dx = Math.max(chamberMinX - x, Math.max(0, x - chamberMaxX));
                    int dz = Math.max(chamberMinZ - z, Math.max(0, z - chamberMaxZ));
                    int out = Math.max(dx, dz); // 0 over the roof, 1..BERM beside the walls
                    int cap = roofY + BuriedHideoutStructure.COVER - 2 * out;
                    int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z); // first air
                    if (ground > cap) {
                        continue;
                    }
                    pos.set(x, ground - 1, z);
                    if (!level.getBlockState(pos).getFluidState().isEmpty()
                            || !level.getFluidState(pos.above()).isEmpty()) {
                        continue; // leave lakes and streams alone
                    }
                    // The column's own surface block (or the chamber's rock roof); terracotta otherwise.
                    BlockState top = TunnelPiece.terrainLike(level, pos.immutable());
                    int from = out == 0 ? Math.max(ground, roofY + 1) : ground;
                    for (int y = from; y <= cap; y++) {
                        pos.set(x, y, z);
                        if (chunkBox.isInside(pos) && level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, top, 2);
                        }
                    }
                }
            }
        }
    }

    // ---- tunnels -------------------------------------------------------------------------------

    /** A planned tunnel: one entry per step, {x, z, y, ground, flags, pillarTop}. */
    public record TunnelPlan(boolean front, Direction dir, int keyIndex, List<int[]> steps) {
    }

    /**
     * Front (3x3, y = floor block) or rear (1 wide rails, y = rail block) tunnel, built from the
     * stored plan. Only blocks inside the chunk being generated are placed; terrain-coloured blocks
     * (floor patches, the lip over the entrance) copy a neighbouring natural block.
     */
    public static class TunnelPiece extends StructurePiece {

        private final boolean front;
        private final Direction dir;
        private final int keyIndex;   // front: breakout step; rear: first open step
        private final int[] xs, zs, ys, grounds, flags, pillars;

        public TunnelPiece(TunnelPlan plan) {
            super(ModStructures.HIDEOUT_TUNNEL.get(), 0, boundsOf(plan));
            this.front = plan.front();
            this.dir = plan.dir();
            this.keyIndex = plan.keyIndex();
            int n = plan.steps().size();
            xs = new int[n]; zs = new int[n]; ys = new int[n]; grounds = new int[n]; flags = new int[n]; pillars = new int[n];
            for (int i = 0; i < n; i++) {
                int[] s = plan.steps().get(i);
                xs[i] = s[0]; zs[i] = s[1]; ys[i] = s[2]; grounds[i] = s[3]; flags[i] = s[4]; pillars[i] = s[5];
            }
        }

        public TunnelPiece(CompoundTag tag) {
            super(ModStructures.HIDEOUT_TUNNEL.get(), tag);
            this.front = tag.getBooleanOr("Front", true);
            this.dir = Direction.from2DDataValue(tag.getIntOr("Dir", 0));
            this.keyIndex = tag.getIntOr("Key", 0);
            xs = tag.getIntArray("X").orElse(new int[0]);
            zs = tag.getIntArray("Z").orElse(new int[0]);
            ys = tag.getIntArray("Y").orElse(new int[0]);
            grounds = tag.getIntArray("G").orElse(new int[0]);
            flags = tag.getIntArray("F").orElse(new int[0]);
            pillars = tag.getIntArray("P").orElse(new int[0]);
        }

        private static BoundingBox boundsOf(TunnelPlan plan) {
            Direction lat = plan.dir().getClockWise();
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            int reach = plan.front() ? 3 : 1;
            for (int[] s : plan.steps()) {
                for (int l = -reach; l <= reach; l += 2 * reach) {
                    int x = s[0] + lat.getStepX() * l;
                    int z = s[1] + lat.getStepZ() * l;
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                }
                boolean pillar = s[5] != Integer.MIN_VALUE && s[2] - 2 - s[5] <= 24;
                int low = pillar ? Math.min(s[2] - 1, s[5]) : s[2] - 1;
                minY = Math.min(minY, low);
                maxY = Math.max(maxY, Math.max(s[2] + 6, s[3] + 3));
            }
            // One more step beyond the end, for the rear bumper.
            int[] last = plan.steps().get(plan.steps().size() - 1);
            minX = Math.min(minX, last[0] + plan.dir().getStepX()); maxX = Math.max(maxX, last[0] + plan.dir().getStepX());
            minZ = Math.min(minZ, last[1] + plan.dir().getStepZ()); maxZ = Math.max(maxZ, last[1] + plan.dir().getStepZ());
            return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
        }

        public boolean isFront() {
            return front;
        }

        /**
         * Riding route along the front tunnel for the outlaw patrol, from the cavern outside the
         * doors to just beyond the mouth: a point every few steps, at walking height.
         */
        public List<BlockPos> routePoints() {
            List<BlockPos> route = new java.util.ArrayList<>();
            int n = xs.length;
            // Three blocks back into the cavern.
            route.add(new BlockPos(xs[0] - dir.getStepX() * 3, ys[0] + 1, zs[0] - dir.getStepZ() * 3));
            for (int i = 0; i < n; i += 5) {
                route.add(new BlockPos(xs[i], ys[i] + 1, zs[i]));
            }
            route.add(new BlockPos(xs[n - 1] + dir.getStepX() * 4, Math.max(ys[n - 1], grounds[n - 1] - 1) + 1,
                    zs[n - 1] + dir.getStepZ() * 4));
            return route;
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            tag.putBoolean("Front", front);
            tag.putInt("Dir", dir.get2DDataValue());
            tag.putInt("Key", keyIndex);
            tag.putIntArray("X", xs);
            tag.putIntArray("Z", zs);
            tag.putIntArray("Y", ys);
            tag.putIntArray("G", grounds);
            tag.putIntArray("F", flags);
            tag.putIntArray("P", pillars);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                                RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
            if (front) {
                buildFront(level, chunkBox);
            } else {
                buildRear(level, chunkBox);
            }
        }

        // ---- front: 3x3, rising 1 per 2, carved into the cavern; widened mouth with a lip and posts

        private void buildFront(WorldGenLevel level, BoundingBox box) {
            Direction lat = dir.getClockWise();
            int n = xs.length;
            int last = n - 1;
            for (int i = 0; i < n; i++) {
                int floor = ys[i];
                if ((flags[i] & FLAG_STAIR) != 0) {
                    buildStair(level, box, lat, i);
                    continue;
                }
                boolean mouth = i >= keyIndex - 1;
                boolean open = i >= keyIndex;
                int half = mouth ? 2 : 1;
                // Occasional one-block narrowing of a side wall (never the ceiling: mounted outlaws ride
                // through, and a rider's head needs the full 3 blocks), never at the ends, a frame or the mouth.
                int narrow = (i >= 3 && i < keyIndex - 3 && i % 6 != 3) ? narrowing(i) : 0;
                int top = open ? Math.max(floor + 3, grounds[i] - 1) : floor + 3;

                for (int l = -half; l <= half; l++) {
                    if (narrow == 1 && l == -1 || narrow == 2 && l == 1) continue;
                    BlockPos column = at(i, lat, l, 0);
                    for (int y = floor + 1; y <= top; y++) {
                        carve(level, box, column.atY(y));
                    }
                    fillIfOpen(level, box, column.atY(floor));
                }

                // Support frame every 6 steps underground: posts in the walls, a beam over the ceiling.
                if (!mouth && i % 6 == 3) {
                    for (int y = floor + 1; y <= floor + 4; y++) {
                        place(level, box, at(i, lat, -2, y), log());
                        place(level, box, at(i, lat, 2, y), log());
                    }
                    for (int l = -1; l <= 1; l++) {
                        place(level, box, at(i, lat, l, floor + 4), Blocks.DARK_OAK_PLANKS.defaultBlockState());
                    }
                }

                // A lip of the hill's own rock over the mouth.
                if (i == keyIndex) {
                    for (int l = -2; l <= 2; l++) {
                        BlockPos lip = at(i, lat, l, floor + 4);
                        if (box.isInside(lip) && level.getBlockState(lip).isAir()) {
                            BlockPos behind = lip.relative(dir.getOpposite());
                            level.setBlock(lip, terrainLike(level, behind, behind.above()), 2);
                        }
                    }
                }
            }

            // Fence posts with torches either side of the way out.
            for (int side = -3; side <= 3; side += 6) {
                BlockPos column = at(last, lat, side, 0);
                if (!box.isInside(column.getX(), box.minY(), column.getZ())) continue;
                int base = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, column.getX(), column.getZ());
                place(level, box, column.atY(base), Blocks.DARK_OAK_FENCE.defaultBlockState());
                place(level, box, column.atY(base + 1), Blocks.DARK_OAK_FENCE.defaultBlockState());
                place(level, box, column.atY(base + 2), Blocks.TORCH.defaultBlockState());
            }
        }

        /** One 3-wide plank step of the staircase down from a cliff mouth, on log posts every other step. */
        private void buildStair(WorldGenLevel level, BoundingBox box, Direction lat, int i) {
            int y = ys[i];
            for (int l = -1; l <= 1; l++) {
                place(level, box, at(i, lat, l, y), Blocks.DARK_OAK_PLANKS.defaultBlockState());
                for (int h = 1; h <= 3; h++) {
                    carve(level, box, at(i, lat, l, y + h));
                }
            }
            int groundTop = pillars[i];
            if (i % 2 == 0 && groundTop > level.getMinY() && y - groundTop <= 32) {
                for (int l = -1; l <= 1; l += 2) {
                    for (int py = y - 1; py > groundTop; py--) {
                        place(level, box, at(i, lat, l, py), log());
                    }
                }
            }
        }

        /** 0 none, 1 left wall in, 2 right wall in; about one step in nine. */
        private int narrowing(int i) {
            long h = (long) xs[0] * 3129871L ^ (long) zs[0] * 116129781L ^ (long) i * 0x9E3779B97F4A7C15L;
            h = h * h * 42317861L + h * 11L;
            int roll = (int) ((h >>> 33) % 18);
            return roll < 2 ? roll + 1 : 0;
        }

        // ---- rear: a drop after the bend, then rails falling 1 per REAR_GRADE (10), posts every 6 in
        // the rock, dark oak across caves, bumper at the end

        private void buildRear(WorldGenLevel level, BoundingBox box) {
            Direction lat = dir.getClockWise();
            RailShape flat = dir.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
            RailShape climb = switch (dir.getOpposite()) {
                case EAST -> RailShape.ASCENDING_EAST;
                case WEST -> RailShape.ASCENDING_WEST;
                case NORTH -> RailShape.ASCENDING_NORTH;
                default -> RailShape.ASCENDING_SOUTH;
            };
            int n = xs.length;
            for (int i = 0; i < n; i++) {
                int rail = ys[i];
                boolean ascending = (flags[i] & FLAG_ASCENDING) != 0;
                boolean bridged = (flags[i] & FLAG_BRIDGE) != 0;
                BlockPos railPos = at(i, lat, 0, rail);

                carve(level, box, railPos.above());
                if (ascending) {
                    carve(level, box, railPos.above(2)); // headroom where the track steps down
                }
                if (bridged) {
                    place(level, box, railPos.below(), Blocks.DARK_OAK_PLANKS.defaultBlockState());
                    int pillarTop = pillars[i];
                    if (pillarTop > level.getMinY() && rail - 2 - pillarTop <= 24 && i % 3 == 0) {
                        for (int y = rail - 2; y > pillarTop; y--) {
                            place(level, box, railPos.atY(y), log());
                        }
                    }
                } else {
                    fillIfOpen(level, box, railPos.below());
                }
                place(level, box, railPos, Blocks.RAIL.defaultBlockState()
                        .setValue(RailBlock.SHAPE, ascending ? climb : flat));

                // Supports inside the rock: posts each side, a plank beam above.
                if (i < keyIndex && !ascending && !bridged && i % 6 == 2
                        && (flags[i] & (FLAG_CAVE | FLAG_INSIDE)) == 0) {
                    place(level, box, at(i, lat, -1, rail), log());
                    place(level, box, at(i, lat, -1, rail + 1), log());
                    place(level, box, at(i, lat, 1, rail), log());
                    place(level, box, at(i, lat, 1, rail + 1), log());
                    for (int l = -1; l <= 1; l++) {
                        place(level, box, at(i, lat, l, rail + 2), Blocks.DARK_OAK_PLANKS.defaultBlockState());
                    }
                }
            }
            // Hay bale bumper just past the end of the line.
            BlockPos end = at(n - 1, lat, 0, ys[n - 1]).relative(dir);
            if (box.isInside(end) && level.getBlockState(end).isAir()) {
                level.setBlock(end, Blocks.HAY_BLOCK.defaultBlockState(), 2);
            }
        }

        // ---- helpers

        private BlockPos at(int i, Direction lat, int l, int y) {
            return new BlockPos(xs[i] + lat.getStepX() * l, y, zs[i] + lat.getStepZ() * l);
        }

        private static BlockState log() {
            return Blocks.DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        }

        private static void carve(WorldGenLevel level, BoundingBox box, BlockPos pos) {
            if (box.isInside(pos) && !level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.CAVE_AIR.defaultBlockState(), 2);
                sealFluids(level, box, pos);
            }
        }

        /**
         * Water or lava beside or above a freshly carved cell would pour into the tunnel: wall it off
         * (lava with stone, water with a block of the surrounding rock).
         */
        private static void sealFluids(WorldGenLevel level, BoundingBox box, BlockPos pos) {
            for (Direction side : SEAL_SIDES) {
                BlockPos next = pos.relative(side);
                if (!box.isInside(next)) {
                    continue;
                }
                var fluid = level.getFluidState(next);
                if (fluid.isEmpty()) {
                    continue;
                }
                BlockState wall = fluid.is(net.minecraft.tags.FluidTags.LAVA)
                        ? Blocks.STONE.defaultBlockState()
                        : terrainLike(level, next.relative(side), next.below());
                level.setBlock(next, wall, 2);
            }
        }

        private static final Direction[] SEAL_SIDES = {
                Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        private static void place(WorldGenLevel level, BoundingBox box, BlockPos pos, BlockState state) {
            if (box.isInside(pos)) {
                level.setBlock(pos, state, 2);
            }
        }

        /** Floor under a tunnel cell that crosses a cave or gap: patch it with the surrounding rock. */
        private void fillIfOpen(WorldGenLevel level, BoundingBox box, BlockPos pos) {
            if (box.isInside(pos)) {
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || !state.getFluidState().isEmpty()) {
                    level.setBlock(pos, terrainLike(level, pos.below(), pos.relative(dir.getOpposite())), 2);
                }
            }
        }

        /** A natural block from one of the given spots, else plain terracotta. */
        static BlockState terrainLike(WorldGenLevel level, BlockPos... candidates) {
            for (BlockPos pos : candidates) {
                BlockState state = level.getBlockState(pos);
                Block block = state.getBlock();
                if (!state.isAir() && state.getFluidState().isEmpty() && state.isCollisionShapeFullBlock(level, pos)
                        && !(block instanceof FenceBlock) && block != Blocks.DARK_OAK_PLANKS && block != Blocks.DARK_OAK_LOG) {
                    return state;
                }
            }
            return Blocks.TERRACOTTA.defaultBlockState();
        }
    }
}
