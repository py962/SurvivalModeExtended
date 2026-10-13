package com.terrainlab.village;

import com.terrainlab.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plays the dusk song at the evening gathering of a tier-4 village. Shortly after the gathering
 * starts, up to four adults near the bell sing their village type's tune (melody, bass and up to
 * two harmony parts), once per village per day and only when a player is within earshot.
 * <p>
 * Uses the unwarped day clock, matching VillagerScheduleService.
 */
public final class VillageSongService {

    private VillageSongService() {
    }

    /** Gathering starts at 13000 (VillagerScheduleService); allows ~10s to arrive. */
    private static final long SONG_START_TICK = 13_200L;
    /** Retry window (ticks) in case nobody has reached the bell yet. */
    private static final long START_WINDOW_TICKS = 400L;
    private static final double SINGER_RADIUS = 12.0; // blocks
    private static final double LISTENER_RADIUS = 64.0; // blocks
    private static final int MAX_HARMONY_SINGERS = 2;

    private static final Map<VillageId, Long> lastSungDay = new HashMap<>();
    private static final List<Performance> performances = new ArrayList<>();

    private static final class Performance {
        final BlockPos bell;
        final VillageSongs.Song song;
        final long startTick;
        final Map<VillageSongs.Part, List<UUID>> singers;
        int nextNote = 0;

        Performance(BlockPos bell, VillageSongs.Song song, long startTick, Map<VillageSongs.Part, List<UUID>> singers) {
            this.bell = bell;
            this.song = song;
            this.startTick = startTick;
            this.singers = singers;
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        tickPerformances(level);

        if (level.getGameTime() % 20 != 0) {
            return;
        }
        long timeOfDay = Math.floorMod(level.getOverworldClockTime(), 24000L);
        if (timeOfDay < SONG_START_TICK || timeOfDay >= SONG_START_TICK + START_WINDOW_TICKS) {
            return;
        }
        long day = Math.floorDiv(level.getOverworldClockTime(), 24000L);
        VillageSavedData data = VillageSavedData.get(level);
        for (VillageId id : data.knownVillageIds()) {
            if (!id.dimension().equals(level.dimension()) || Long.valueOf(day).equals(lastSungDay.get(id))) {
                continue;
            }
            if (data.getOrCreate(id).getTier() != VillageTier.TIER_4_TOWN) {
                continue;
            }
            BlockPos bell = id.bellPos();
            if (!level.isLoaded(bell)
                    || level.getNearestPlayer(bell.getX() + 0.5, bell.getY() + 0.5, bell.getZ() + 0.5,
                    LISTENER_RADIUS, false) == null) {
                continue;
            }
            List<Villager> gathered = level.getEntitiesOfClass(Villager.class,
                    new AABB(bell).inflate(SINGER_RADIUS),
                    v -> v.isAlive() && !v.isBaby() && !v.isSleeping());
            if (gathered.isEmpty()) {
                continue; // nobody at the bell yet; retry next second
            }
            lastSungDay.put(id, day);
            start(level, bell, gathered);
        }
    }

    private static void start(ServerLevel level, BlockPos bell, List<Villager> gathered) {
        List<Villager> singers = new ArrayList<>(gathered);
        Collections.shuffle(singers, new java.util.Random(level.getRandom().nextLong()));

        Map<VillageSongs.Part, List<UUID>> parts = new EnumMap<>(VillageSongs.Part.class);
        parts.put(VillageSongs.Part.MELODY, List.of(singers.get(0).getUUID()));
        if (singers.size() > 1) {
            parts.put(VillageSongs.Part.BASS, List.of(singers.get(1).getUUID()));
        }
        List<UUID> harmony = new ArrayList<>();
        for (int i = 2; i < singers.size() && harmony.size() < MAX_HARMONY_SINGERS; i++) {
            harmony.add(singers.get(i).getUUID());
        }
        if (!harmony.isEmpty()) {
            parts.put(VillageSongs.Part.HARMONY, harmony);
        }

        VillageSongs.Song song = VillageSongs.forVillagerType(majorityType(singers));
        performances.add(new Performance(bell, song, level.getGameTime(), parts));
        com.terrainlab.advancement.ModAdvancements.awardNearby(level, net.minecraft.world.phys.Vec3.atCenterOf(bell), LISTENER_RADIUS, "village_song");
    }

    /** Most common villager type among the singers, as a registry path ("plains", "desert", ...). */
    private static String majorityType(List<Villager> singers) {
        Map<String, Integer> counts = new HashMap<>();
        for (Villager v : singers) {
            String type = v.getVillagerData().type().unwrapKey()
                    .map(key -> key.identifier().getPath())
                    .orElse("plains");
            counts.merge(type, 1, Integer::sum);
        }
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("plains");
    }

    private static void tickPerformances(ServerLevel level) {
        Iterator<Performance> it = performances.iterator();
        while (it.hasNext()) {
            Performance p = it.next();
            long elapsed = level.getGameTime() - p.startTick;
            List<VillageSongs.Note> notes = p.song.notes();
            while (p.nextNote < notes.size() && notes.get(p.nextNote).tick() <= elapsed) {
                sing(level, p, notes.get(p.nextNote));
                p.nextNote++;
            }
            if (elapsed > p.song.lengthTicks() || elapsed < 0) {
                it.remove();
            }
        }
    }

    private static void sing(ServerLevel level, Performance p, VillageSongs.Note note) {
        List<UUID> voices = p.singers.get(note.part());
        if (voices == null) {
            return; // not enough villagers for this part
        }
        // Note-particle colour follows the pitch, the way note blocks do (0..1 across two octaves).
        double semitones = 12.0 * Math.log(note.pitch()) / Math.log(2.0);
        double colour = (semitones + 12.0) / 24.0;
        for (UUID uuid : voices) {
            Entity entity = level.getEntity(uuid);
            if (!(entity instanceof Villager villager) || !villager.isAlive() || villager.isSleeping()) {
                continue;
            }
            level.playSound(null, villager.getX(), villager.getEyeY(), villager.getZ(),
                    ModSounds.VILLAGER_SONG.get(), SoundSource.NEUTRAL, note.volume(), note.pitch());
            level.sendParticles(ParticleTypes.NOTE, villager.getX(), villager.getEyeY() + 0.7, villager.getZ(),
                    0, colour, 0.0, 0.0, 1.0);
            villager.getLookControl().setLookAt(p.bell.getX() + 0.5, p.bell.getY() + 0.5, p.bell.getZ() + 0.5);
        }
    }
}
