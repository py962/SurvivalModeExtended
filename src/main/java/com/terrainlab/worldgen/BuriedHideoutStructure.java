package com.terrainlab.worldgen;

import com.mojang.serialization.MapCodec;
import com.terrainlab.TerrainLab;
import com.terrainlab.debug.TerrainLabDebug;
import com.terrainlab.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.core.Vec3i;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The Outlaws Hideout, buried in a badlands hill: the hideout template, a 3x3 front tunnel rising
 * 1 block per 2 from the cavern outside the west doors until it breaks out to open sky, and a rail
 * escape tunnel that drops a block straight after the rail line's bend, then 1 block per
 * {@link #REAR_GRADE} east, crossing caves narrower than 10 blocks and ending at a larger cave or
 * where it comes out of the hillside (no bridge at the surface).
 * <p>
 * Everything is planned here from the generator's terrain noise, before any piece is built: the
 * chamber is placed as high as the hill allows with at least {@link #COVER} blocks over a low
 * percentile of the ground across its footprint (plus a {@link #MARGIN}-block ring); CoverPiece fills
 * any dip that would leave the roof or a wall exposed. Never
 * with the loot room below {@link #MIN_CHEST_Y}. All four rotations are tried; if none fits, this
 * location is rejected and the structure set tries elsewhere.
 * <p>
 * Vanilla only checks the biome after this method returns, and /locate runs it for every candidate
 * chunk, so the cheap rejections come first: the biome at the chunk centre, then the height at five
 * points every rotation must cover. The full height grid is sampled once for all rotations, and all
 * terrain reads go through a per-call cache, capped at {@link #READ_BUDGET} per location so no single
 * location can stall the server.
 * <p>
 * With {@code -Dterrainlab.debug=true}, a summary of attempts and rejection reasons is logged every
 * few seconds while the structure is being searched for or generated, and every placement is logged.
 */
public class BuriedHideoutStructure extends Structure {

    public static final MapCodec<BuriedHideoutStructure> CODEC = simpleCodec(BuriedHideoutStructure::new);

    static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath("terrainlab", "outlaws_hideout");

    // Template-local layout (unrotated). See the template: west doors at x 6, z 10-11; the west
    // cavern opens on the template's west face at x 0, z 8-13, y 3-5; the rail line runs east at
    // y 1, z 22 to the east face.
    static final int CHEST_LOCAL_Y = 2;
    static final int TOP_LOCAL_Y = 13;
    /** Front tunnel: first cell outside the west face; y is the tunnel's floor block. */
    static final BlockPos FRONT_START = new BlockPos(-1, 2, 10);
    /** Rear tunnel: first rail after the bend (inside the template); y is the template's rail level. */
    static final BlockPos REAR_START = new BlockPos(13, 1, 22);
    /** Rear steps still inside the template's own corridor (x 13-30), re-laid a block lower. */
    static final int REAR_INSIDE_STEPS = 18;

    static final int COVER = 2;
    static final int MARGIN = 1;
    /**
     * The room is placed by the ground height that only this share of samples falls below, not by
     * the single lowest sample; CoverPiece then fills any dip that leaves the roof or walls exposed.
     */
    static final double COVER_PERCENTILE = 0.25;
    /** The room is centred on the highest badlands ground within this many blocks of the candidate. */
    static final int SEARCH_RADIUS = 64;
    static final int SEARCH_STEP = 8;
    static final int REFINE_RADIUS = 6;
    static final int REFINE_STEP = 2;
    /**
     * Pieces are only built in chunks within 8 of the start chunk (vanilla's structure reference
     * range); everything planned is kept inside 7, leaving room for tunnel walls and berms.
     */
    static final int MAX_CHUNK_REACH = 7;
    static final int SAMPLE_STEP = 3;
    static final int MIN_CHEST_Y = 75;
    /**
     * Height at which the biome is read. A biome lookup also depends on depth below the surface,
     * so under high inland terrain a point 20+ blocks down reads as a cave biome (dripstone caves
     * under badlands hills). Reading well above any badlands ground always gives the surface biome.
     */
    static final int BIOME_CHECK_Y = 256;
    /** Lowest surface (first air) that still fits the room with its cover above MIN_CHEST_Y. */
    static final int MIN_SURFACE = MIN_CHEST_Y - CHEST_LOCAL_Y + TOP_LOCAL_Y + COVER + 1;

    static final int FRONT_MAX_STEPS = 220;   // safety limit only; normally it breaks out long before
    static final int FRONT_MAX_TRENCH = 10;   // open cut after breakout, until ground meets the floor
    static final int FRONT_MAX_EXIT_DROP = 3; // a bigger drop at the mouth gets a staircase down
    static final int FRONT_MAX_STAIRS = 64;   // longest staircase; a taller cliff just ends the stairs there
    static final int REAR_MAX_STEPS = 320;    // safety limit only
    /** The escape track drops 1 block every this many blocks. */
    static final int REAR_GRADE = 10;
    static final int REAR_MAX_BRIDGE = 40;
    /** The escape tunnel crosses a cave and tunnels on if rock resumes within this many blocks. */
    static final int REAR_MAX_CAVE_SPAN = 9;
    /** Most terrain columns one location may read before it is given up on. */
    static final int READ_BUDGET = 2000;

    // Debug counters (see class comment).
    private static final AtomicLong CALLS = new AtomicLong();
    private static final AtomicLong NOT_BIOME = new AtomicLong();
    private static final AtomicLong TOO_LOW_QUICK = new AtomicLong();
    private static final AtomicLong TOO_LOW = new AtomicLong();
    private static final AtomicLong FRONT_FAILED = new AtomicLong();
    private static final AtomicLong REAR_FAILED = new AtomicLong();
    private static final AtomicLong OVER_BUDGET = new AtomicLong();
    private static final AtomicLong PLACED = new AtomicLong();
    private static final AtomicLong READS = new AtomicLong();
    private static final AtomicLong NANOS = new AtomicLong();
    private static final AtomicLong LAST_LOG = new AtomicLong(System.nanoTime());
    private static final long LOG_INTERVAL_NANOS = 5_000_000_000L;

    public BuriedHideoutStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.BURIED_HIDEOUT.get();
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!TerrainLabDebug.ENABLED) {
            return find(context, null);
        }
        long start = System.nanoTime();
        Terrain[] used = new Terrain[1];
        Optional<GenerationStub> result = find(context, used);
        CALLS.incrementAndGet();
        NANOS.addAndGet(System.nanoTime() - start);
        if (used[0] != null) {
            // Only locations that passed the biome check get a line; the rest are in the summary.
            READS.addAndGet(used[0].reads);
            TerrainLab.LOGGER.info("[TerrainLab/hideout] chunk {}: {} | {} | {} terrain reads, {} ms",
                    context.chunkPos(), result.isPresent() ? "PLACED at " + result.get().position().toShortString()
                            : "rejected", used[0].trace, used[0].reads, (System.nanoTime() - start) / 1_000_000L);
        }
        if (result.isPresent()) {
            PLACED.incrementAndGet();
        }
        logSummaryIfDue();
        return result;
    }

    private Optional<GenerationStub> find(GenerationContext context, Terrain[] used) {
        try {
            return findUnchecked(context, used);
        } catch (OverBudget e) {
            if (TerrainLabDebug.ENABLED) {
                OVER_BUDGET.incrementAndGet();
                if (used[0] != null) {
                    used[0].trace.append("gave up: over the read budget of ").append(READ_BUDGET);
                }
            }
            return Optional.empty();
        }
    }

    private Optional<GenerationStub> findUnchecked(GenerationContext context, Terrain[] used) {
        StructureTemplate template = context.structureTemplateManager().getOrCreate(TEMPLATE);
        Vec3i size = template.getSize();
        if (size.getX() == 0) {
            return Optional.empty();
        }
        int cx = context.chunkPos().getMiddleBlockX();
        int cz = context.chunkPos().getMiddleBlockZ();

        // 1. Biome, before any terrain noise is sampled.
        if (!context.validBiome().test(context.chunkGenerator().getBiomeSource().getNoiseBiome(
                QuartPos.fromBlock(cx), QuartPos.fromBlock(BIOME_CHECK_Y), QuartPos.fromBlock(cz),
                context.randomState().sampler()))) {
            count(NOT_BIOME);
            return Optional.empty();
        }

        Terrain terrain = new Terrain(context.chunkGenerator(), context.randomState(), context.heightAccessor(),
                context.chunkPos());
        if (used != null) {
            used[0] = terrain;
            TerrainLab.LOGGER.info("[TerrainLab/hideout] chunk {}: badlands, checking terrain (thread {})",
                    context.chunkPos(), Thread.currentThread().getName());
        }

        // 2. Top down: find the highest badlands ground within SEARCH_RADIUS, on a coarse grid and then a
        // finer look round the best reading. Only points whose own biome is badlands count.
        java.util.function.BiPredicate<Integer, Integer> badlands = (x, z) -> context.validBiome().test(
                context.chunkGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x),
                        QuartPos.fromBlock(BIOME_CHECK_Y), QuartPos.fromBlock(z), context.randomState().sampler()));
        int[] best = {cx, cz, Integer.MIN_VALUE};
        scanForHighPoint(terrain, badlands, cx, cz, SEARCH_RADIUS, SEARCH_STEP, best);
        scanForHighPoint(terrain, badlands, best[0], best[1], REFINE_RADIUS, REFINE_STEP, best);
        int px = best[0];
        int pz = best[1];
        terrain.trace.append("high point ").append(px).append(",").append(pz).append(" at Y ").append(best[2]).append("; ");
        if (best[2] < MIN_SURFACE) {
            terrain.trace.append("below Y ").append(MIN_SURFACE);
            count(TOO_LOW_QUICK);
            return Optional.empty();
        }

        // 3. One height grid round the high point, covering every rotation's footprint plus margin.
        int reach = Math.max(size.getX(), size.getZ()) / 2 + 1 + MARGIN;
        List<int[]> grid = new ArrayList<>();
        for (int x = px - reach; ; x = Math.min(x + SAMPLE_STEP, px + reach)) {
            for (int z = pz - reach; ; z = Math.min(z + SAMPLE_STEP, pz + reach)) {
                grid.add(new int[]{x, z, terrain.height(x, z)});
                if (z >= pz + reach) break;
            }
            if (x >= px + reach) break;
        }

        Rotation first = Rotation.getRandom(context.random());
        Rotation[] all = Rotation.values();
        int furthest = 0; // 0 too low, 1 front tunnel failed, 2 rear tunnel failed
        for (int r = 0; r < all.length; r++) {
            Rotation rotation = all[(first.ordinal() + r) % all.length];
            int[] stage = {0};
            terrain.trace.append(rotation.getSerializedName()).append(": ");
            Optional<GenerationStub> stub = tryRotation(context, terrain, grid, template, size, rotation, px, pz, stage);
            if (stub.isPresent()) {
                terrain.trace.append("ok");
                return stub;
            }
            terrain.trace.append("; ");
            furthest = Math.max(furthest, stage[0]);
        }
        count(furthest == 0 ? TOO_LOW : furthest == 1 ? FRONT_FAILED : REAR_FAILED);
        return Optional.empty();
    }

    /** Highest badlands ground on a square grid round (x, z), kept in best = {x, z, height} if higher. */
    private static void scanForHighPoint(Terrain terrain, java.util.function.BiPredicate<Integer, Integer> badlands,
                                         int x0, int z0, int radius, int step, int[] best) {
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                int x = x0 + dx;
                int z = z0 + dz;
                if (!terrain.withinReach(x, z, SEARCH_RADIUS / 16 + 1) || !badlands.test(x, z)) {
                    continue;
                }
                int h = terrain.height(x, z);
                if (h > best[2]) {
                    best[0] = x;
                    best[1] = z;
                    best[2] = h;
                }
            }
        }
    }

    private static void count(AtomicLong counter) {
        if (TerrainLabDebug.ENABLED) {
            counter.incrementAndGet();
        }
    }

    private static void logSummaryIfDue() {
        long now = System.nanoTime();
        long last = LAST_LOG.get();
        if (now - last < LOG_INTERVAL_NANOS || !LAST_LOG.compareAndSet(last, now)) {
            return;
        }
        long calls = CALLS.getAndSet(0);
        if (calls == 0) {
            return;
        }
        TerrainLab.LOGGER.info("[TerrainLab/hideout] last ~5 s: {} locations checked -> {} placed | rejected: "
                        + "{} not badlands, {} quick height, {} too low/thin cover, {} front tunnel, {} rear tunnel, "
                        + "{} over read budget | {} terrain reads, {} ms",
                calls, PLACED.getAndSet(0), NOT_BIOME.getAndSet(0), TOO_LOW_QUICK.getAndSet(0), TOO_LOW.getAndSet(0),
                FRONT_FAILED.getAndSet(0), REAR_FAILED.getAndSet(0), OVER_BUDGET.getAndSet(0), READS.getAndSet(0),
                NANOS.getAndSet(0) / 1_000_000L);
    }

    private Optional<GenerationStub> tryRotation(GenerationContext context, Terrain terrain, List<int[]> grid,
                                                 StructureTemplate template, Vec3i size,
                                                 Rotation rotation, int cx, int cz, int[] stage) {
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);

        BlockPos centreRel = StructureTemplate.calculateRelativePosition(settings,
                new BlockPos(size.getX() / 2, 0, size.getZ() / 2));
        int ox = cx - centreRel.getX();
        int oz = cz - centreRel.getZ();
        BoundingBox footprint = template.getBoundingBox(settings, new BlockPos(ox, 0, oz));

        // 1. Depth, top down: the roof starts COVER blocks under the summit and steps down until at least
        // 75% of the ground over the footprint and its ring is COVER or more above it, which is the
        // ground's 25th percentile. Dips left exposed are banked in afterwards by CoverPiece.
        List<Integer> heights = new ArrayList<>();
        for (int[] s : grid) {
            if (s[0] >= footprint.minX() - MARGIN && s[0] <= footprint.maxX() + MARGIN
                    && s[1] >= footprint.minZ() - MARGIN && s[1] <= footprint.maxZ() + MARGIN) {
                heights.add(s[2]);
            }
        }
        heights.sort(null);
        int surface = heights.get((int) (heights.size() * COVER_PERCENTILE));
        // Highest placement that still leaves COVER solid blocks over the roof (surface is the first air).
        int oy = surface - 1 - COVER - TOP_LOCAL_Y;
        terrain.trace.append("ground ").append(surface).append(" (lowest ").append(heights.get(0))
                .append("), chest Y ").append(oy + CHEST_LOCAL_Y);
        if (oy + CHEST_LOCAL_Y < MIN_CHEST_Y) {
            terrain.trace.append(" < ").append(MIN_CHEST_Y);
            return Optional.empty();
        }
        BlockPos origin = new BlockPos(ox, oy, oz);
        stage[0] = 1;

        // 2. Front tunnel, west out of the cavern, rising until it reaches open sky.
        Direction front = rotation.rotate(Direction.WEST);
        BlockPos frontStart = origin.offset(StructureTemplate.calculateRelativePosition(settings, FRONT_START));
        BuriedHideoutPieces.TunnelPlan frontPlan = planFront(terrain, frontStart, front);
        if (frontPlan == null) {
            terrain.trace.append(", front tunnel failed (").append(terrain.lastTunnelReason).append(")");
            return Optional.empty();
        }
        terrain.trace.append(", front ").append(frontPlan.steps().size()).append(" steps");
        terrain.lastTunnelReason = "";
        stage[0] = 2;

        // 3. Rear escape, east along the rail line, falling until it reaches a cave or the surface.
        Direction rear = rotation.rotate(Direction.EAST);
        BlockPos rearStart = origin.offset(StructureTemplate.calculateRelativePosition(settings, REAR_START));
        // The escape tunnel never rejects a location: it runs as far as it can (see planRear).
        BuriedHideoutPieces.TunnelPlan rearPlan = planRear(terrain, rearStart, rear);
        terrain.trace.append(", rear ").append(rearPlan == null ? 0 : rearPlan.steps().size()).append(" steps")
                .append(terrain.lastTunnelReason.isEmpty() ? "" : " (" + terrain.lastTunnelReason + ")").append(", ");

        // Vanilla's final biome check reads the biome at this point, so it is set at ground level above
        // the room centre (not down at the chest, where the biome can read as a cave biome).
        BlockPos centreAtChest = origin.offset(StructureTemplate.calculateRelativePosition(settings,
                new BlockPos(size.getX() / 2, CHEST_LOCAL_Y, size.getZ() / 2)));
        BlockPos stubPos = new BlockPos(centreAtChest.getX(), surface, centreAtChest.getZ());
        BoundingBox chamberBox = template.getBoundingBox(settings, origin);
        return Optional.of(new GenerationStub(stubPos, builder -> {
            builder.addPiece(new BuriedHideoutPieces.ChamberPiece(context.structureTemplateManager(), origin, rotation));
            // After the chamber, before the tunnels, so the tunnels cut through any fill.
            builder.addPiece(new BuriedHideoutPieces.CoverPiece(chamberBox));
            builder.addPiece(new BuriedHideoutPieces.TunnelPiece(frontPlan));
            if (rearPlan != null) {
                builder.addPiece(new BuriedHideoutPieces.TunnelPiece(rearPlan));
            }
        }));
    }

    /**
     * Rises 1 block per 2 from the cavern. Breaks out at the first column with no solid rock above
     * the tunnel's ceiling, then continues as an open cut until the ground meets the floor. Rejected
     * if it would come out under water, onto a drop of more than FRONT_MAX_EXIT_DROP, or never
     * reaches the surface within the safety limit.
     */
    private static BuriedHideoutPieces.TunnelPlan planFront(Terrain terrain, BlockPos start, Direction dir) {
        List<int[]> steps = new ArrayList<>();
        int breakout = -1;
        for (int i = 0; i < FRONT_MAX_STEPS; i++) {
            int x = start.getX() + dir.getStepX() * i;
            int z = start.getZ() + dir.getStepZ() * i;
            if (!terrain.withinReach(x, z, MAX_CHUNK_REACH)) {
                return fail(terrain, "no surface within the structure's reach (" + i + " steps)");
            }
            int floor = start.getY() + i / 2;
            int ground = terrain.height(x, z);
            steps.add(new int[]{x, z, floor, ground, 0, Integer.MIN_VALUE});

            if (breakout < 0) {
                if (ground <= floor + 4) { // no rock above the ceiling row (floor + 3)
                    int water = terrain.waterSurface(x, z);
                    if (water > ground) return fail(terrain, "comes out under water at step " + i);
                    if (floor - (ground - 1) > FRONT_MAX_EXIT_DROP) {
                        // Out of a cliff face: carry on as a staircase down to the ground.
                        return stairsDown(terrain, steps, dir, i, floor);
                    }
                    breakout = i;
                }
            } else if (ground - 1 <= floor || i >= breakout + FRONT_MAX_TRENCH) {
                if (i >= breakout + 2) {
                    return new BuriedHideoutPieces.TunnelPlan(true, dir, breakout, steps);
                }
            }
        }
        return fail(terrain, "no surface within " + FRONT_MAX_STEPS + " steps");
    }

    /**
     * Drops a block straight after the bend, then 1 block per REAR_GRADE along the rail line. The
     * first REAR_INSIDE_STEPS steps re-lay the template's own track one block lower. A cave is
     * crossed (on dark oak where its floor is lower) if rock resumes within REAR_MAX_CAVE_SPAN
     * blocks; a larger cave ends the line, bridged over a drop. Coming out of the hillside ends the
     * line at the mouth: no bridge is built at the surface.
     */
    private static BuriedHideoutPieces.TunnelPlan planRear(Terrain terrain, BlockPos start, Direction dir) {
        List<int[]> steps = new ArrayList<>();
        try {
            return planRearSteps(terrain, start, dir, steps);
        } catch (OverBudget e) {
            return endHere(terrain, steps, dir, "stopped at the read budget");
        }
    }

    /**
     * The escape tunnel is never a reason to reject a location: when it cannot go on (water or lava
     * ahead, the step limit, a bridge too long or running into water, the hillside over a drop),
     * the track simply ends where it got to, against a hay bale bumper. Fluids beside the carved
     * tunnel are sealed off as it is built. Null only if there is no room for even one step.
     */
    private static BuriedHideoutPieces.TunnelPlan endHere(Terrain terrain, List<int[]> steps, Direction dir,
                                                         String reason) {
        terrain.lastTunnelReason = reason;
        return steps.isEmpty() ? null : new BuriedHideoutPieces.TunnelPlan(false, dir, steps.size(), steps);
    }

    /** Rail height at rear step i: one block down straight after the bend, then 1 per REAR_GRADE. */
    private static int rearRail(BlockPos start, int i) {
        return start.getY() - 1 - i / REAR_GRADE;
    }

    private static BuriedHideoutPieces.TunnelPlan planRearSteps(Terrain terrain, BlockPos start, Direction dir,
                                                               List<int[]> steps) {
        for (int i = 0; i < REAR_MAX_STEPS; i++) {
            int x = start.getX() + dir.getStepX() * i;
            int z = start.getZ() + dir.getStepZ() * i;
            int rail = rearRail(start, i);
            int flags = (i % REAR_GRADE == 0) ? BuriedHideoutPieces.FLAG_ASCENDING : 0;

            if (i < REAR_INSIDE_STEPS) {
                // Still inside the template's corridor: no terrain checks, the template carved it.
                steps.add(new int[]{x, z, rail, rail, flags | BuriedHideoutPieces.FLAG_INSIDE, Integer.MIN_VALUE});
                continue;
            }
            if (!terrain.withinReach(x, z, MAX_CHUNK_REACH)) {
                return endHere(terrain, steps, dir, "ends at the structure's reach after " + i + " steps");
            }
            NoiseColumn column = terrain.column(x, z);
            BlockState atRail = column.getBlock(rail);
            BlockState atHead = column.getBlock(rail + 1);
            if (isFluid(atRail) || isFluid(atHead) || isFluid(column.getBlock(rail - 1))) {
                return endHere(terrain, steps, dir, "ends before water or lava at step " + i);
            }
            int ground = terrain.height(x, z);
            boolean outside = ground <= rail + 1;
            boolean cave = !outside && atRail.isAir() && atHead.isAir();
            int groundTop = (outside || cave) ? topSolidBelow(column, rail - 1, terrain.ha.getMinY()) : rail - 1;
            boolean overDrop = groundTop < rail - 1;

            if (outside) {
                if (!overDrop) {
                    steps.add(new int[]{x, z, rail, ground, flags, Integer.MIN_VALUE});
                    return new BuriedHideoutPieces.TunnelPlan(false, dir, i, steps);
                }
                // Out of the hillside above a drop: end at the mouth, no bridge.
                return endHere(terrain, steps, dir, "ends at the hillside mouth after " + i + " steps");
            }
            if (cave) {
                if (inSmallCave(terrain, start, dir, i)) {
                    // Cross it and tunnel on into the far wall.
                    flags |= BuriedHideoutPieces.FLAG_CAVE;
                    if (overDrop) {
                        flags |= BuriedHideoutPieces.FLAG_BRIDGE;
                    }
                    steps.add(new int[]{x, z, rail, ground, flags, overDrop ? groundTop : Integer.MIN_VALUE});
                    continue;
                }
                steps.add(new int[]{x, z, rail, ground, flags, Integer.MIN_VALUE});
                if (!overDrop) {
                    return new BuriedHideoutPieces.TunnelPlan(false, dir, i, steps);
                }
                return bridge(terrain, steps, dir, i, rail);
            }
            steps.add(new int[]{x, z, rail, ground, flags, Integer.MIN_VALUE});
        }
        return endHere(terrain, steps, dir, "ends after " + REAR_MAX_STEPS + " steps underground");
    }

    /**
     * True if, from the open cell at step i, solid rock resumes at rail height within
     * REAR_MAX_CAVE_SPAN steps, with no fluid and no way out to the hillside in between.
     */
    private static boolean inSmallCave(Terrain terrain, BlockPos start, Direction dir, int i) {
        for (int k = i + 1; k <= i + REAR_MAX_CAVE_SPAN; k++) {
            int x = start.getX() + dir.getStepX() * k;
            int z = start.getZ() + dir.getStepZ() * k;
            if (!terrain.withinReach(x, z, MAX_CHUNK_REACH)) {
                return false;
            }
            int rail = rearRail(start, k);
            NoiseColumn column = terrain.column(x, z);
            BlockState atRail = column.getBlock(rail);
            BlockState atHead = column.getBlock(rail + 1);
            if (isFluid(atRail) || isFluid(atHead) || isFluid(column.getBlock(rail - 1))) {
                return false;
            }
            if (terrain.height(x, z) <= rail + 1) {
                return false; // opens onto the hillside instead
            }
            if (!(atRail.isAir() && atHead.isAir())) {
                return true; // the far wall
            }
        }
        return false;
    }

    /**
     * Front tunnel mouth in a cliff face: a 3-wide plank staircase descending 1 block per step
     * from the mouth until it meets the ground (or water, where it ends as a landing stage), on log
     * pillars. A cliff never rejects the location: past FRONT_MAX_STAIRS steps the stairs simply
     * end where they are.
     */
    private static BuriedHideoutPieces.TunnelPlan stairsDown(Terrain terrain, List<int[]> steps, Direction dir,
                                                            int mouth, int floor) {
        int[] start = steps.get(mouth);
        for (int j = 1; j <= FRONT_MAX_STAIRS; j++) {
            int x = start[0] + dir.getStepX() * j;
            int z = start[1] + dir.getStepZ() * j;
            if (!terrain.withinReach(x, z, MAX_CHUNK_REACH)) {
                terrain.lastTunnelReason = "stairs end at the structure's reach";
                return new BuriedHideoutPieces.TunnelPlan(true, dir, mouth, steps);
            }
            int stepY = floor - j;
            int ground = terrain.height(x, z);
            steps.add(new int[]{x, z, stepY, ground, BuriedHideoutPieces.FLAG_STAIR, ground - 1});
            if (ground - 1 >= stepY || terrain.waterSurface(x, z) - 1 >= stepY) {
                return new BuriedHideoutPieces.TunnelPlan(true, dir, mouth, steps);
            }
        }
        terrain.lastTunnelReason = "stairs end after " + FRONT_MAX_STAIRS + " steps";
        return new BuriedHideoutPieces.TunnelPlan(true, dir, mouth, steps);
    }

    /** Level track over a drop until there is ground (or a far wall) at the rail's floor. */
    private static BuriedHideoutPieces.TunnelPlan bridge(Terrain terrain, List<int[]> steps, Direction dir,
                                                        int endIndex, int rail) {
        int minY = terrain.ha.getMinY();
        int[] last = steps.get(endIndex);
        NoiseColumn firstColumn = terrain.column(last[0], last[1]);
        last[4] |= BuriedHideoutPieces.FLAG_BRIDGE;
        last[5] = topSolidBelow(firstColumn, rail - 1, minY);

        for (int j = 1; j <= REAR_MAX_BRIDGE; j++) {
            int x = last[0] + dir.getStepX() * j;
            int z = last[1] + dir.getStepZ() * j;
            if (!terrain.withinReach(x, z, MAX_CHUNK_REACH)) {
                terrain.lastTunnelReason = "bridge ends at the structure's reach";
                return new BuriedHideoutPieces.TunnelPlan(false, dir, endIndex, steps);
            }
            NoiseColumn column = terrain.column(x, z);
            if (isFluid(column.getBlock(rail)) || isFluid(column.getBlock(rail + 1))) {
                terrain.lastTunnelReason = "bridge ends before water or lava";
                return new BuriedHideoutPieces.TunnelPlan(false, dir, endIndex, steps);
            }
            int ground = terrain.height(x, z);
            int groundTop = topSolidBelow(column, rail - 1, minY);
            boolean landed = groundTop >= rail - 1;
            steps.add(new int[]{x, z, rail, ground, landed ? 0 : BuriedHideoutPieces.FLAG_BRIDGE, groundTop});
            if (landed) {
                return new BuriedHideoutPieces.TunnelPlan(false, dir, endIndex, steps);
            }
        }
        terrain.lastTunnelReason = "bridge ends after " + REAR_MAX_BRIDGE + " blocks";
        return new BuriedHideoutPieces.TunnelPlan(false, dir, endIndex, steps);
    }

    private static BuriedHideoutPieces.TunnelPlan fail(Terrain terrain, String reason) {
        terrain.lastTunnelReason = reason;
        return null;
    }

    /** Highest solid (non-air, non-fluid) block at or below fromY, or minY - 1 if none or fluid first. */
    private static int topSolidBelow(NoiseColumn column, int fromY, int minY) {
        for (int y = fromY; y >= minY; y--) {
            BlockState state = column.getBlock(y);
            if (isFluid(state)) {
                return minY - 1;
            }
            if (!state.isAir()) {
                return y;
            }
        }
        return minY - 1;
    }

    private static boolean isFluid(BlockState state) {
        return !state.getFluidState().isEmpty();
    }

    /**
     * Per-call cache of terrain reads. Each read makes the generator evaluate a whole noise column, so
     * no column is read twice, and a column already fetched in full also gives its OCEAN_FLOOR_WG
     * height without another read.
     */
    private static final class Terrain {
        final ChunkGenerator gen;
        final RandomState rs;
        final LevelHeightAccessor ha;
        private final Map<Long, Integer> heights = new HashMap<>();
        private final Map<Long, NoiseColumn> columns = new HashMap<>();
        int reads;
        /** Debug: what was tried at this location, logged when debugging is on. */
        final StringBuilder trace = new StringBuilder();
        String lastTunnelReason = "";

        private final net.minecraft.world.level.ChunkPos startChunk;

        Terrain(ChunkGenerator gen, RandomState rs, LevelHeightAccessor ha, net.minecraft.world.level.ChunkPos startChunk) {
            this.gen = gen;
            this.rs = rs;
            this.ha = ha;
            this.startChunk = startChunk;
        }

        /** Whether (x, z) lies within the given number of chunks of the structure's start chunk. */
        boolean withinReach(int x, int z, int chunks) {
            return Math.abs((x >> 4) - startChunk.x) <= chunks && Math.abs((z >> 4) - startChunk.z) <= chunks;
        }

        /** First air above the highest motion-blocking block, as OCEAN_FLOOR_WG. */
        int height(int x, int z) {
            long key = key(x, z);
            Integer cached = heights.get(key);
            if (cached != null) {
                return cached;
            }
            spend();
            int h = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ha, rs);
            heights.put(key, h);
            return h;
        }

        NoiseColumn column(int x, int z) {
            long key = key(x, z);
            NoiseColumn column = columns.get(key);
            if (column == null) {
                spend();
                column = gen.getBaseColumn(x, z, ha, rs);
                columns.put(key, column);
                if (!heights.containsKey(key)) {
                    heights.put(key, heightFrom(column));
                }
            }
            return column;
        }

        /** WORLD_SURFACE_WG height (water counts as surface); only used at the front tunnel's mouth. */
        int waterSurface(int x, int z) {
            spend();
            return gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ha, rs);
        }

        private void spend() {
            if (++reads > READ_BUDGET) {
                throw OverBudget.INSTANCE;
            }
        }

        private int heightFrom(NoiseColumn column) {
            for (int y = ha.getMaxY(); y >= ha.getMinY(); y--) {
                if (Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(column.getBlock(y))) {
                    return y + 1;
                }
            }
            return ha.getMinY();
        }

        private static long key(int x, int z) {
            return (x & 0xFFFFFFFFL) | ((long) z << 32);
        }
    }

    /** Thrown when a location exceeds READ_BUDGET; caught in find(). No stack trace, so it is cheap. */
    private static final class OverBudget extends RuntimeException {
        static final OverBudget INSTANCE = new OverBudget();

        private OverBudget() {
            super(null, null, false, false);
        }
    }
}
