package com.terrainlab.event;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Operator debug command {@code /terrainlab biomestats <y> [radius]}: the share of each biome at block
 * height y within {@code radius} blocks of the world origin in the current dimension, read from the
 * world generator's biome source every STEP blocks. No chunks are loaded or generated, so it covers
 * unexplored terrain.
 * <p>
 * Runs on a background thread and reports progress every PROGRESS_INTERVAL_MS. Writes every biome
 * (samples and share) to a CSV under {@code terrainlab_biome_stats/} in the server directory and
 * prints the summary in chat. {@code /terrainlab biomestats stop} ends a survey early, keeping the
 * partial results.
 */
public final class BiomeStatsCommand {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEFAULT_RADIUS = 10_000;
    private static final int STEP = 16;
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final AtomicBoolean STOP_REQUESTED = new AtomicBoolean();
    private static final long PROGRESS_INTERVAL_MS = 10_000L;
    /** Biomes under this share (in percent) are grouped into one "Other" line in chat. */
    private static final double OTHER_THRESHOLD = 0.1;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private BiomeStatsCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("terrainlab")
                .then(Commands.literal("biomestats")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("stop")
                                .executes(BiomeStatsCommand::stop))
                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                .executes(ctx -> start(ctx, DEFAULT_RADIUS))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(64, 30_000))
                                        .executes(ctx -> start(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))));
    }

    /** One biome and how many samples landed in it. */
    private record Count(String biome, long samples) {
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        if (!RUNNING.get()) {
            ctx.getSource().sendFailure(Component.literal("No biome survey is running."));
            return 0;
        }
        STOP_REQUESTED.set(true);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Stopping the biome survey; partial results will be written."), true);
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> ctx, int radius) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        int y = IntegerArgumentType.getInteger(ctx, "y");
        if (y < level.getMinY() || y > level.getMaxY()) {
            source.sendFailure(Component.literal(String.format(Locale.ROOT,
                    "y=%d is outside this dimension's build height (%d to %d).",
                    y, level.getMinY(), level.getMaxY())));
            return 0;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            source.sendFailure(Component.literal(
                    "A biome survey is already running (/terrainlab biomestats stop ends it)."));
            return 0;
        }

        MinecraftServer server = source.getServer();
        int perAxis = 2 * radius / STEP + 1;
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Surveying biomes at y=%d within %d blocks of 0,0 in %s (%,d samples). This runs in the "
                        + "background; the server stays playable.",
                y, radius, level.dimension().identifier(), (long) perAxis * perAxis)), true);

        STOP_REQUESTED.set(false);
        Thread thread = new Thread(() -> {
            try {
                survey(source, level, server, y, radius, perAxis);
            } catch (Throwable t) {
                LOGGER.error("Biome survey failed", t);
                server.execute(() -> source.sendFailure(Component.literal("Biome survey failed: " + t)));
            } finally {
                RUNNING.set(false);
            }
        }, "TerrainLab biome survey");
        thread.setDaemon(true);
        thread.start();
        return 1;
    }

    private static void survey(CommandSourceStack source, ServerLevel level, MinecraftServer server, int y,
                               int radius, int perAxis) throws IOException {
        long startTime = System.currentTimeMillis();
        RandomState randomState = level.getChunkSource().randomState();
        BiomeResolver resolver = level.getChunkSource().getGenerator().getBiomeSource()
                .createUncachedResolver(randomState);
        int quartY = QuartPos.fromBlock(y);

        Map<String, Long> counts = new HashMap<>();
        long total = 0L;
        int rows = 0;
        boolean stopped = false;
        long lastReport = System.currentTimeMillis();

        for (int ix = 0; ix < perAxis; ix++) {
            if (STOP_REQUESTED.get()) {
                stopped = true;
                break;
            }
            int quartX = QuartPos.fromBlock(-radius + ix * STEP);
            for (int iz = 0; iz < perAxis; iz++) {
                Holder<Biome> biome = resolver.getNoiseBiome(quartX, quartY, QuartPos.fromBlock(-radius + iz * STEP));
                counts.merge(biome.getRegisteredName(), 1L, Long::sum);
            }
            total += perAxis;
            rows++;

            long now = System.currentTimeMillis();
            if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                lastReport = now;
                String progress = String.format(Locale.ROOT, "Biome survey: %d/%d rows (%.0f%%)",
                        rows, perAxis, 100.0 * rows / perAxis);
                LOGGER.info("[TerrainLab/biomestats] {}", progress);
                say(server, source, progress);
            }
        }

        List<Count> sorted = new ArrayList<>();
        counts.forEach((biome, n) -> sorted.add(new Count(biome, n)));
        sorted.sort((a, b) -> a.samples() != b.samples()
                ? Long.compare(b.samples(), a.samples())
                : a.biome().compareTo(b.biome()));

        Path dir = server.getServerDirectory().resolve("terrainlab_biome_stats");
        Files.createDirectories(dir);
        String base = String.format(Locale.ROOT, "%s_y%d_r%d_%s%s",
                level.dimension().identifier().getPath(), y, radius, LocalDateTime.now().format(FILE_TIME),
                stopped ? "_partial" : "");
        Path file = dir.resolve(base + ".csv");

        double divisor = Math.max(total, 1L);
        List<String> lines = new ArrayList<>();
        int otherBiomes = 0;
        long otherSamples = 0L;
        try (BufferedWriter w = Files.newBufferedWriter(file)) {
            w.write("biome,samples,percent\n");
            for (Count c : sorted) {
                double percent = 100.0 * c.samples() / divisor;
                w.write(String.format(Locale.ROOT, "%s,%d,%.3f%n", c.biome(), c.samples(), percent));
                if (percent < OTHER_THRESHOLD) {
                    otherBiomes++;
                    otherSamples += c.samples();
                } else {
                    lines.add(String.format(Locale.ROOT, "%s, %.1f%%", c.biome(), percent));
                }
            }
        }
        if (otherBiomes > 0) {
            lines.add(String.format(Locale.ROOT, "Other (%d biomes), %.1f%%",
                    otherBiomes, 100.0 * otherSamples / divisor));
        }

        long seconds = (System.currentTimeMillis() - startTime) / 1000L;
        long sampleTotal = total;
        int rowsDone = rows;
        String outcome = stopped ? "Survey stopped" : "Survey done";
        server.execute(() -> {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "%s in %ds: %,d samples (%d/%d rows) at y=%d within %d blocks of 0,0 in %s. biome, share:",
                    outcome, seconds, sampleTotal, rowsDone, perAxis, y, radius,
                    level.dimension().identifier())), false);
            for (String line : lines) {
                source.sendSuccess(() -> Component.literal(line), false);
            }
            source.sendSuccess(() -> Component.literal("Full list: " + file.toAbsolutePath()), false);
        });
        LOGGER.info("[TerrainLab/biomestats] Biome survey written to {}", file);
    }

    private static void say(MinecraftServer server, CommandSourceStack source, String message) {
        server.execute(() -> source.sendSuccess(() -> Component.literal(message), false));
    }
}
