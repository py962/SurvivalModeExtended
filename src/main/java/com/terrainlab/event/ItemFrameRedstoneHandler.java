package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.api.ClockFrameAccess;
import com.terrainlab.data.SeasonSavedData;
import com.terrainlab.redstone.ClockFramePulse;
import com.terrainlab.registry.ModItems;
import com.terrainlab.season.Season;
import com.terrainlab.season.SeasonManager;
import com.terrainlab.season.TemperatureSeason;
import com.terrainlab.temperature.LocalTemperatureService;
import com.terrainlab.temperature.ThermometerBand;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Item-frame redstone outputs, driven from a per-tick pass over each level's item frames. Frames are
 * tracked from entity join/leave events rather than scanning every loaded entity.
 * <ul>
 *   <li>Clock: the frame's rotation selects a period (see {@link ClockFramePulse}); the frame emits a
 *       short 0/15 comparator pulse each period via the flag exposed by {@link ClockFrameAccess}.</li>
 *   <li>Calendar: rotation is set to the current season ordinal, so comparators read the season.</li>
 *   <li>Thermometer: rotation is set to the local {@link ThermometerBand}, bucketed onto 8 rotations.</li>
 * </ul>
 * Calendar and thermometer use vanilla's rotation-based comparator output (1-8); the rotation is
 * compared against the frame's live rotation each tick, so a manual turn is corrected.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class ItemFrameRedstoneHandler {

    /** Per-frame clock pulse timing; the pulse level itself lives on the frame via {@link ClockFrameAccess}. */
    private static final class FrameClockState {
        long cycleStartTick = Long.MIN_VALUE;
        long currentPeriod = ClockFramePulse.BASE_PERIOD_TICKS;
    }

    private static final Map<UUID, FrameClockState> CLOCK_STATES = new HashMap<>();

    /** Ambient temperature is sampled this often per thermometer frame (staggered by frame). */
    private static final int THERMOMETER_SAMPLE_INTERVAL_TICKS = 20;

    // Last sampled thermometer rotation per frame, reapplied every tick between samples.
    private static final Map<UUID, Integer> THERMOMETER_ROTATION = new HashMap<>();

    /** Item frames known to each server level; removed frames are pruned during the tick pass. */
    private static final Map<ResourceKey<Level>, Set<ItemFrame>> FRAMES = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        FRAMES.computeIfAbsent(level.dimension(), key -> Collections.newSetFromMap(new IdentityHashMap<>()))
                .add(frame);
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // Leave also fires when a chunk stops being tracked while the frame stays loaded; keep it then.
        if (!frame.isRemoved()) return;

        forgetFrame(level, frame);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        FRAMES.clear();
        CLOCK_STATES.clear();
        THERMOMETER_ROTATION.clear();
    }

    // Runs in every dimension.
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        Set<ItemFrame> frames = FRAMES.get(level.dimension());
        if (frames == null || frames.isEmpty()) return;

        // Iterate a snapshot: neighbour updates below may add or remove frames.
        for (ItemFrame frame : frames.toArray(new ItemFrame[0])) {
            if (frame.isRemoved()) {
                forgetFrame(level, frame);
                continue;
            }

            ItemStack held = frame.getItem();
            UUID id = frame.getUUID();
            if (held.isEmpty()) {
                CLOCK_STATES.remove(id);
                THERMOMETER_ROTATION.remove(id);
                continue;
            }

            if (held.is(Items.CLOCK)) {
                tickClockFrame(level, frame);
            } else if (held.is(ModItems.CALENDAR.get())) {
                tickCalendarFrame(level, frame);
            } else if (held.is(ModItems.THERMOMETER.get())) {
                tickThermometerFrame(level, frame);
            }

            if (!held.is(ModItems.THERMOMETER.get())) {
                THERMOMETER_ROTATION.remove(id);
            }
        }
    }

    private static void forgetFrame(ServerLevel level, ItemFrame frame) {
        Set<ItemFrame> frames = FRAMES.get(level.dimension());
        if (frames != null) {
            frames.remove(frame);
        }

        // An unloaded frame keeps its UUID when reloaded, so its state is kept; destroyed frames are dropped.
        Entity.RemovalReason reason = frame.getRemovalReason();
        if (reason != null && reason.shouldDestroy()) {
            UUID id = frame.getUUID();
            CLOCK_STATES.remove(id);
            THERMOMETER_ROTATION.remove(id);
        }
    }

    private static void tickClockFrame(ServerLevel level, ItemFrame frame) {
        UUID id = frame.getUUID();
        FrameClockState state = CLOCK_STATES.computeIfAbsent(id, k -> new FrameClockState());
        long now = level.getGameTime();

        boolean newCycle = state.cycleStartTick == Long.MIN_VALUE
                || now >= state.cycleStartTick + state.currentPeriod;
        if (newCycle) {
            // Rotation only selects the period; it is never written back.
            state.currentPeriod = ClockFramePulse.periodForRotation(frame.getRotation() % 8);
            state.cycleStartTick = now;
        }

        long ticksIntoCycle = now - state.cycleStartTick;
        boolean shouldBeHigh = ClockFramePulse.isPulseHigh(ticksIntoCycle, state.currentPeriod);

        ClockFrameAccess access = (ClockFrameAccess) frame;
        if (shouldBeHigh != access.terrainlab$isClockPulseHigh()) {
            access.terrainlab$setClockPulseHigh(shouldBeHigh);
            level.updateNeighbourForOutputSignal(frame.blockPosition(), Blocks.AIR);
        }
    }

    private static void tickCalendarFrame(ServerLevel level, ItemFrame frame) {
        long offset = SeasonSavedData.get(level).getSeasonalOffset();
        Season season = SeasonManager.getCurrentSeason(level.getGameTime(), offset);
        int desiredRotation = season.ordinal(); // 0-5, fits the 0-7 rotation range directly

        // setRotation also updates neighbours, so it only runs when the live rotation differs.
        if (frame.getRotation() != desiredRotation) {
            frame.setRotation(desiredRotation);
        }
    }

    private static void tickThermometerFrame(ServerLevel level, ItemFrame frame) {
        UUID id = frame.getUUID();
        Integer desiredRotation = THERMOMETER_ROTATION.get(id);

        if (desiredRotation == null
                || Math.floorMod(level.getGameTime() + id.hashCode(), THERMOMETER_SAMPLE_INTERVAL_TICKS) == 0) {
            long offset = SeasonSavedData.get(level).getSeasonalOffset();
            TemperatureSeason season = SeasonManager.getCurrentTemperatureSeason(level.getGameTime(), offset);
            // Same day/night blend as the player's temperature.
            double nightFraction = com.terrainlab.season.SolarTime.nightFraction(level);
            double celsius = LocalTemperatureService.getAmbientTemperature(
                    level, frame.blockPosition(), season, nightFraction);
            ThermometerBand band = ThermometerBand.fromCelsius(celsius);
            desiredRotation = thermometerRotationFor(band);
            THERMOMETER_ROTATION.put(id, desiredRotation);
        }

        // setRotation also updates neighbours, so it only runs when the live rotation differs.
        if (frame.getRotation() != desiredRotation) {
            frame.setRotation(desiredRotation);
        }
    }

    /**
     * Maps the 10 thermometer bands linearly onto the 8 frame rotations (first band to 0, last to 7);
     * some adjacent bands share a rotation.
     */
    private static int thermometerRotationFor(ThermometerBand band) {
        int bandCount = ThermometerBand.values().length;
        return (int) Math.round(band.ordinal() * 7.0 / (bandCount - 1));
    }
}
