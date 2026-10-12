package com.terrainlab.event;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.terrainlab.debug.TerrainLabDebug;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Operator debug command {@code /terrainlab structurestats [radius]}: finds every structure that
 * would generate within {@code radius} blocks of the world origin in the current dimension, without
 * generating any chunks. Each structure set's candidate chunks are found from its placement, and
 * each candidate runs the same weighted structure choice and {@code Structure#generate} call as
 * worldgen, so biome checks, jigsaw assembly and this mod's start vetoes all apply.
 * <p>
 * Runs on a background thread. Writes every structure (id, position, piece count, size) to a CSV,
 * a per-structure summary to a second CSV and per-set timings to a third, all under
 * {@code terrainlab_structure_stats/} in the server directory, and prints the summary in chat.
 * <p>
 * Each set is announced as it starts and reports progress every PROGRESS_INTERVAL_MS. Each
 * candidate is test-built on a worker thread with a CANDIDATE_TIMEOUT_SECONDS limit; one that
 * overruns is logged and skipped (its worker is abandoned, as structure generation cannot be
 * interrupted). {@code /terrainlab structurestats stop} ends a survey early, keeping the partial
 * results.
 */
public final class StructureStatsCommand {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEFAULT_RADIUS = 10_000;
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final AtomicBoolean STOP_REQUESTED = new AtomicBoolean();
    private static final long PROGRESS_INTERVAL_MS = 10_000L;
    private static final long CANDIDATE_TIMEOUT_SECONDS = 20L;
    /** A candidate still running after this many seconds is reported, with the worker's stack. */
    private static final long SLOW_REPORT_SECONDS = 5L;
    /** Candidates slower than this are always logged. */
    private static final long SLOW_LOG_MS = 1000L;
    private static final int MAX_FRAMES = 60;
    private static final AtomicReference<Thread> WORKER_THREAD = new AtomicReference<>();
    /** Structure sets the survey leaves out. Villages take far longer to test-build than anything else. */
    private static final java.util.Set<String> SKIPPED_SETS = java.util.Set.of("minecraft:villages");

    private StructureStatsCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("terrainlab")
                .then(Commands.literal("structurestats")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> start(ctx, DEFAULT_RADIUS))
                        .then(Commands.literal("stop")
                                .executes(StructureStatsCommand::stop))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(64, 30_000))
                                .executes(ctx -> start(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))));
    }

    /** One structure that would generate. */
    private record Found(String structure, String set, BlockPos pos, ChunkPos chunk, int pieces, BoundingBox box) {
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        if (!RUNNING.get()) {
            ctx.getSource().sendFailure(Component.literal("No structure survey is running."));
            return 0;
        }
        STOP_REQUESTED.set(true);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Stopping the structure survey; partial results will be written."), true);
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> ctx, int radius) {
        CommandSourceStack source = ctx.getSource();
        if (!RUNNING.compareAndSet(false, true)) {
            source.sendFailure(Component.literal(
                    "A structure survey is already running (/terrainlab structurestats stop ends it)."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        MinecraftServer server = source.getServer();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Surveying structures within %d blocks of 0,0 in %s. This runs in the background; "
                        + "the server stays playable.", radius, level.dimension().identifier())), true);

        STOP_REQUESTED.set(false);
        Thread thread = new Thread(() -> {
            try {
                survey(source, level, server, radius);
            } catch (Throwable t) {
                LOGGER.error("Structure survey failed", t);
                server.execute(() -> source.sendFailure(Component.literal("Structure survey failed: " + t)));
            } finally {
                RUNNING.set(false);
            }
        }, "TerrainLab structure survey");
        thread.setDaemon(true);
        thread.start();
        return 1;
    }

    private static void survey(CommandSourceStack source, ServerLevel level, MinecraftServer server, int radius)
            throws IOException {
        long startTime = System.currentTimeMillis();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        RandomState randomState = level.getChunkSource().randomState();
        StructureTemplateManager templates = server.getStructureManager();
        long seed = state.getLevelSeed();

        int minChunk = Math.floorDiv(-radius, 16);
        int maxChunk = Math.floorDiv(radius, 16);

        List<Holder<StructureSet>> sets = state.possibleStructureSets();
        List<Found> found = new ArrayList<>();
        List<String> setTimings = new ArrayList<>(); // set,candidates,checked,found,skipped,seconds
        int candidates = 0;
        int skippedTotal = 0;
        boolean stopped = false;
        ExecutorService worker = newWorker();

        try {
            for (int s = 0; s < sets.size() && !stopped; s++) {
                Holder<StructureSet> setHolder = sets.get(s);
                StructureSet set = setHolder.value();
                String setName = setHolder.getRegisteredName();
                String prefix = String.format(Locale.ROOT, "[%d/%d] %s", s + 1, sets.size(), setName);
                if (SKIPPED_SETS.contains(setName)) {
                    String skipMsg = prefix + ": skipped (too slow to survey; see SKIPPED_SETS)";
                    LOGGER.info("[TerrainLab/survey] {}", skipMsg);
                    say(server, source, skipMsg);
                    continue;
                }
                long setStart = System.currentTimeMillis();

                List<ChunkPos> chunks = candidateChunks(set.placement(), state, seed, minChunk, maxChunk);
                candidates += chunks.size();
                int setCandidates = chunks.size();
                boolean ownSet = setName.startsWith("terrainlab:");
                String startMsg = String.format(Locale.ROOT, "%s: %d candidate chunks...", prefix, setCandidates);
                LOGGER.info("[TerrainLab/survey] {}", startMsg);
                say(server, source, startMsg);

                int checked = 0;
                int setFound = 0;
                int setSkipped = 0;
                long lastReport = System.currentTimeMillis();

                for (ChunkPos chunk : chunks) {
                    if (STOP_REQUESTED.get()) {
                        stopped = true;
                        break;
                    }

                    if (ownSet && TerrainLabDebug.ENABLED) {
                        LOGGER.info("[TerrainLab/survey] {}: testing chunk {},{} ({}/{})",
                                prefix, chunk.x, chunk.z, checked + 1, setCandidates);
                    }
                    long candidateStart = System.currentTimeMillis();
                    Future<Found> task = worker.submit(
                            () -> tryChunk(setHolder, chunk, level, generator, randomState, templates, seed));
                    try {
                        Found f = await(task, server, source, prefix, chunk);
                        long ms = System.currentTimeMillis() - candidateStart;
                        if (f != null) {
                            found.add(f);
                            setFound++;
                        }
                        if (ms >= SLOW_LOG_MS || (ownSet && TerrainLabDebug.ENABLED)) {
                            LOGGER.info("[TerrainLab/survey] {}: chunk {},{} -> {} in {} ms",
                                    prefix, chunk.x, chunk.z, f == null ? "nothing" : f.structure() + " at "
                                            + f.pos().toShortString(), ms);
                        }
                    } catch (TimeoutException e) {
                        // Generation can't be interrupted: abandon this worker and carry on with a new one.
                        task.cancel(true);
                        worker.shutdownNow();
                        worker = newWorker();
                        setSkipped++;
                        String msg = String.format(Locale.ROOT,
                                "%s: chunk %d,%d took over %ds - skipped (worker abandoned)",
                                prefix, chunk.x, chunk.z, CANDIDATE_TIMEOUT_SECONDS);
                        LOGGER.warn(msg);
                        say(server, source, msg);
                    } catch (ExecutionException e) {
                        setSkipped++;
                        LOGGER.warn("{}: chunk {},{} failed to test-build", prefix, chunk.x, chunk.z, e.getCause());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        stopped = true;
                        break;
                    }
                    checked++;

                    long now = System.currentTimeMillis();
                    if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                        lastReport = now;
                        String progress = String.format(Locale.ROOT, "%s: %d/%d checked, %d found",
                                prefix, checked, setCandidates, setFound);
                        LOGGER.info("[TerrainLab/survey] {}", progress);
                        say(server, source, progress);
                    }
                }

                double seconds = (System.currentTimeMillis() - setStart) / 1000.0;
                skippedTotal += setSkipped;
                setTimings.add(String.format(Locale.ROOT, "%s,%d,%d,%d,%d,%.1f",
                        setName, setCandidates, checked, setFound, setSkipped, seconds));
                String doneMsg = String.format(Locale.ROOT, "%s: done in %.0fs - %d found%s",
                        prefix, seconds, setFound, setSkipped > 0 ? ", " + setSkipped + " skipped" : "");
                LOGGER.info("[TerrainLab/survey] {}", doneMsg);
                say(server, source, doneMsg);
            }
        } finally {
            worker.shutdownNow();
        }

        Path dir = server.getServerDirectory().resolve("terrainlab_structure_stats");
        Files.createDirectories(dir);
        String base = String.format(Locale.ROOT, "%s_seed%d_r%d%s",
                level.dimension().identifier().getPath(), seed, radius, stopped ? "_partial" : "");
        Path listFile = dir.resolve(base + "_structures.csv");
        Path summaryFile = dir.resolve(base + "_summary.csv");
        Path setsFile = dir.resolve(base + "_sets.csv");

        try (BufferedWriter w = Files.newBufferedWriter(setsFile)) {
            w.write("structure_set,candidates,checked,found,skipped,seconds\n");
            for (String line : setTimings) {
                w.write(line + "\n");
            }
        }

        try (BufferedWriter w = Files.newBufferedWriter(listFile)) {
            w.write("structure,structure_set,x,y,z,chunk_x,chunk_z,pieces,size_x,size_y,size_z\n");
            for (Found f : found) {
                w.write(String.format(Locale.ROOT, "%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
                        f.structure(), f.set(), f.pos().getX(), f.pos().getY(), f.pos().getZ(),
                        f.chunk().x, f.chunk().z, f.pieces(),
                        f.box().getXSpan(), f.box().getYSpan(), f.box().getZSpan()));
            }
        }

        // Per structure: count, count scaled to a 10,000 x 10,000 square, piece count range.
        double side = (maxChunk - minChunk + 1) * 16.0;
        double per10k = (10_000.0 * 10_000.0) / (side * side);
        Map<String, int[]> summary = new TreeMap<>(); // count, minPieces, maxPieces, totalPieces
        for (Found f : found) {
            int[] row = summary.computeIfAbsent(f.structure(), k -> new int[]{0, Integer.MAX_VALUE, 0, 0});
            row[0]++;
            row[1] = Math.min(row[1], f.pieces());
            row[2] = Math.max(row[2], f.pieces());
            row[3] += f.pieces();
        }

        List<String> lines = new ArrayList<>();
        try (BufferedWriter w = Files.newBufferedWriter(summaryFile)) {
            w.write("structure,count,per_10k_square,min_pieces,avg_pieces,max_pieces\n");
            for (Map.Entry<String, int[]> e : summary.entrySet()) {
                int[] r = e.getValue();
                String line = String.format(Locale.ROOT, "%s,%d,%.1f,%d,%.1f,%d",
                        e.getKey(), r[0], r[0] * per10k, r[1], r[3] / (double) r[0], r[2]);
                w.write(line + "\n");
                lines.add(line);
            }
        }

        long seconds = (System.currentTimeMillis() - startTime) / 1000L;
        int candidateTotal = candidates;
        int skipped = skippedTotal;
        String outcome = stopped ? "Survey stopped" : "Survey done";
        server.execute(() -> {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "%s in %ds: %d structures from %d candidate chunks%s. "
                            + "structure, count, per 10k x 10k, min/avg/max pieces:",
                    outcome, seconds, found.size(), candidateTotal,
                    skipped > 0 ? " (" + skipped + " skipped)" : "")), false);
            for (String line : lines) {
                source.sendSuccess(() -> Component.literal(line), false);
            }
            source.sendSuccess(() -> Component.literal("Full list: " + listFile.toAbsolutePath()), false);
        });
        LOGGER.info("Structure survey written to {}, {} and {}", listFile, summaryFile, setsFile);
    }

    private static void say(MinecraftServer server, CommandSourceStack source, String message) {
        server.execute(() -> source.sendSuccess(() -> Component.literal(message), false));
    }

    private static ExecutorService newWorker() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread t = new Thread(runnable, "TerrainLab structure survey worker");
            t.setDaemon(true);
            WORKER_THREAD.set(t);
            return t;
        });
    }

    /**
     * Waits for one candidate, up to CANDIDATE_TIMEOUT_SECONDS. Every SLOW_REPORT_SECONDS it is still
     * running, it says so in chat and logs the worker thread's state and stack, so a hang shows
     * exactly where it is stuck (RUNNABLE in the same method each time means a loop; BLOCKED or
     * WAITING means it is waiting on a lock or another thread).
     */
    private static Found await(Future<Found> task, MinecraftServer server, CommandSourceStack source, String prefix,
                               ChunkPos chunk) throws InterruptedException, ExecutionException, TimeoutException {
        long waited = 0L;
        while (true) {
            try {
                return task.get(SLOW_REPORT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                waited += SLOW_REPORT_SECONDS;
                if (waited >= CANDIDATE_TIMEOUT_SECONDS) {
                    throw e;
                }
                Thread worker = WORKER_THREAD.get();
                String msg = String.format(Locale.ROOT, "%s: chunk %d,%d still running after %ds (worker %s)",
                        prefix, chunk.x, chunk.z, waited, worker == null ? "?" : worker.getState());
                say(server, source, msg + " - stack in the log");
                StringBuilder out = new StringBuilder("[TerrainLab/survey] ").append(msg).append('\n');
                if (worker != null) {
                    StackTraceElement[] stack = worker.getStackTrace();
                    for (int i = 0; i < Math.min(stack.length, MAX_FRAMES); i++) {
                        out.append("    at ").append(stack[i]).append('\n');
                    }
                }
                LOGGER.warn(out.toString());
            }
        }
    }

    /** Chunks within range where this placement would try to start a structure. */
    private static List<ChunkPos> candidateChunks(StructurePlacement placement, ChunkGeneratorStructureState state,
                                                  long seed, int minChunk, int maxChunk) {
        List<ChunkPos> result = new ArrayList<>();

        if (placement instanceof RandomSpreadStructurePlacement spread) {
            int spacing = spread.spacing();
            for (int cx = Math.floorDiv(minChunk, spacing); cx <= Math.floorDiv(maxChunk, spacing); cx++) {
                for (int cz = Math.floorDiv(minChunk, spacing); cz <= Math.floorDiv(maxChunk, spacing); cz++) {
                    ChunkPos chunk = spread.getPotentialStructureChunk(seed, cx * spacing, cz * spacing);
                    if (inRange(chunk, minChunk, maxChunk) && spread.isStructureChunk(state, chunk.x, chunk.z)) {
                        result.add(chunk);
                    }
                }
            }
        } else if (placement instanceof ConcentricRingsStructurePlacement rings) {
            List<ChunkPos> positions = state.getRingPositionsFor(rings);
            if (positions != null) {
                for (ChunkPos chunk : positions) {
                    if (inRange(chunk, minChunk, maxChunk) && rings.isStructureChunk(state, chunk.x, chunk.z)) {
                        result.add(chunk);
                    }
                }
            }
        } else {
            // Unknown placement type: test every chunk.
            for (int x = minChunk; x <= maxChunk; x++) {
                for (int z = minChunk; z <= maxChunk; z++) {
                    if (placement.isStructureChunk(state, x, z)) {
                        result.add(new ChunkPos(x, z));
                    }
                }
            }
        }
        return result;
    }

    private static boolean inRange(ChunkPos chunk, int min, int max) {
        return chunk.x >= min && chunk.x <= max && chunk.z >= min && chunk.z <= max;
    }

    /** The weighted structure choice from ChunkGenerator#createStructures, for one candidate chunk. */
    private static Found tryChunk(Holder<StructureSet> setHolder, ChunkPos chunk, ServerLevel level,
                                  ChunkGenerator generator, RandomState randomState,
                                  StructureTemplateManager templates, long seed) {
        List<StructureSet.StructureSelectionEntry> entries = new ArrayList<>(setHolder.value().structures());
        if (entries.size() == 1) {
            return tryGenerate(setHolder, entries.get(0), chunk, level, generator, randomState, templates, seed);
        }

        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, chunk.x, chunk.z);
        int totalWeight = 0;
        for (StructureSet.StructureSelectionEntry entry : entries) {
            totalWeight += entry.weight();
        }

        while (!entries.isEmpty()) {
            int pick = random.nextInt(totalWeight);
            int index = 0;
            for (StructureSet.StructureSelectionEntry entry : entries) {
                pick -= entry.weight();
                if (pick < 0) {
                    break;
                }
                index++;
            }

            StructureSet.StructureSelectionEntry chosen = entries.get(index);
            Found f = tryGenerate(setHolder, chosen, chunk, level, generator, randomState, templates, seed);
            if (f != null) {
                return f;
            }
            entries.remove(index);
            totalWeight -= chosen.weight();
        }
        return null;
    }

    private static Found tryGenerate(Holder<StructureSet> setHolder, StructureSet.StructureSelectionEntry entry,
                                     ChunkPos chunk, ServerLevel level, ChunkGenerator generator,
                                     RandomState randomState, StructureTemplateManager templates, long seed) {
        Holder<Structure> holder = entry.structure();
        Structure structure = holder.value();
        StructureStart start = structure.generate(holder, level.dimension(), level.registryAccess(), generator,
                generator.getBiomeSource(), randomState, templates, seed, chunk, 0, level,
                structure.biomes()::contains);
        if (!start.isValid()) {
            return null;
        }

        BoundingBox box = start.getBoundingBox();
        BlockPos centre = new BlockPos((box.minX() + box.maxX()) / 2, box.minY(), (box.minZ() + box.maxZ()) / 2);
        return new Found(holder.getRegisteredName(), setHolder.getRegisteredName(), centre, chunk,
                start.getPieces().size(), box);
    }
}
