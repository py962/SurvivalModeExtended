package com.terrainlab.debug;

import com.terrainlab.TerrainLab;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Map;

/**
 * Singleplayer has no server watchdog, so a stuck server thread leaves nothing in the log. This
 * (always on, whatever the debug setting) checks once a second and, whenever the server thread has
 * not finished a tick for {@link #STALL_SECONDS} seconds, logs (again every STALL_SECONDS while it
 * stays stuck):
 * <ul>
 *   <li>the server thread's state (RUNNABLE means it is busy, BLOCKED or WAITING means it is waiting
 *   on something) and its full stack, which shows exactly which method it is in;</li>
 *   <li>any other thread currently inside TerrainLab code, with its stack;</li>
 *   <li>any Java-level deadlock the JVM can detect.</li>
 * </ul>
 * Lines start with [TerrainLab/stall].
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class ServerStallWatchdog {

    private static final long STALL_SECONDS = 10L;
    private static final int MAX_FRAMES = 80;

    private static volatile long lastTickNanos;
    private static volatile Thread watcher;

    private ServerStallWatchdog() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        lastTickNanos = System.nanoTime();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // Always on: it costs one sleeping thread and only ever logs when the server is stuck.
        MinecraftServer server = event.getServer();
        Thread serverThread = server.getRunningThread();
        lastTickNanos = System.nanoTime();

        Thread thread = new Thread(() -> watch(server, serverThread), "TerrainLab stall watchdog");
        thread.setDaemon(true);
        watcher = thread;
        thread.start();
        TerrainLab.LOGGER.info("[TerrainLab/stall] watchdog running: a server stall over {} s will be logged",
                STALL_SECONDS);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        Thread thread = watcher;
        watcher = null;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private static void watch(MinecraftServer server, Thread serverThread) {
        long lastDump = 0L;
        while (watcher == Thread.currentThread()) {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                return;
            }
            long now = System.nanoTime();
            // A paused singleplayer game skips ticks on purpose; isPaused is only set between ticks,
            // so a tick that is stuck still counts.
            if (server.isPaused()) {
                lastTickNanos = now;
                continue;
            }
            long stalledSeconds = (now - lastTickNanos) / 1_000_000_000L;
            if (stalledSeconds >= STALL_SECONDS && (now - lastDump) / 1_000_000_000L >= STALL_SECONDS) {
                lastDump = now;
                dump(serverThread, stalledSeconds);
            }
        }
    }

    private static void dump(Thread serverThread, long stalledSeconds) {
        StringBuilder out = new StringBuilder();
        out.append("[TerrainLab/stall] the server thread has not finished a tick for ").append(stalledSeconds)
                .append(" s. Server thread state: ").append(serverThread.getState()).append('\n');
        appendStack(out, serverThread.getStackTrace());

        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            Thread thread = entry.getKey();
            if (thread == serverThread || thread == Thread.currentThread() || !inTerrainLab(entry.getValue())) {
                continue;
            }
            out.append("--- other thread in TerrainLab code: \"").append(thread.getName()).append("\" ")
                    .append(thread.getState()).append('\n');
            appendStack(out, entry.getValue());
        }

        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        long[] deadlocked = mx.findDeadlockedThreads();
        if (deadlocked != null) {
            out.append("--- JVM reports a DEADLOCK between these threads:\n");
            for (ThreadInfo info : mx.getThreadInfo(deadlocked, true, true)) {
                out.append("  \"").append(info.getThreadName()).append("\" waiting for ")
                        .append(info.getLockName()).append(" held by \"").append(info.getLockOwnerName())
                        .append("\"\n");
            }
        }
        TerrainLab.LOGGER.warn(out.toString());
    }

    private static boolean inTerrainLab(StackTraceElement[] stack) {
        for (StackTraceElement frame : stack) {
            if (frame.getClassName().startsWith("com.terrainlab.")
                    && !frame.getClassName().startsWith("com.terrainlab.debug.")) {
                return true;
            }
        }
        return false;
    }

    private static void appendStack(StringBuilder out, StackTraceElement[] stack) {
        int n = Math.min(stack.length, MAX_FRAMES);
        for (int i = 0; i < n; i++) {
            out.append("    at ").append(stack[i]).append('\n');
        }
        if (stack.length > n) {
            out.append("    ... ").append(stack.length - n).append(" more\n");
        }
    }
}
