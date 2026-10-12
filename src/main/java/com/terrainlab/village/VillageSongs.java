package com.terrainlab.village;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Song data for the tier-4 dusk gathering: one tune per villager type. Each tune repeats an
 * 8-beat phrase for about 30 seconds; the melody starts alone, bass joins on the second pass and
 * harmony on the third, and the last pass is a resolving cadence. Two quiet off-beat chromatic
 * notes are added on the second and second-to-last passes.
 * <p>
 * Notes are semitones relative to the tonic, shifted by {@code key}, and converted to a Minecraft
 * sound pitch (0.5..2.0), folding by octaves when out of range.
 */
public final class VillageSongs {

    private VillageSongs() {
    }

    public enum Part { MELODY, HARMONY, BASS }

    /** One sung note: part, tick offset from song start, sound pitch, volume. */
    public record Note(Part part, int tick, float pitch, float volume) {
    }

    /** A finished song: its notes (sorted by tick) and total length including ring-out. */
    public record Song(String id, List<Note> notes, int lengthTicks) {
    }

    /**
     * A fleeting chromatic note, sung on the off-beat just after {@code beat}.
     * Only MELODY or HARMONY.
     */
    private record Colour(int beat, int semitone, Part part) {
    }

    private static final int R = Integer.MIN_VALUE; // rest: previous note rings on
    private static final int TARGET_TICKS = 560;    // beat length target; ~30s with ring-out
    private static final int RING_OUT_TICKS = 40;
    private static final float COLOUR_VOLUME = 0.55f; // relative to the pass volume

    private record Tune(int ticksPerBeat, int key,
                        int[] melody, int[] cadence,
                        int[] harmony, int[] cadenceHarm,
                        int[] bass, int[] cadenceBass,
                        Colour[] colours) {
    }

    private static Colour mel(int beat, int semitone) {
        return new Colour(beat, semitone, Part.MELODY);
    }

    private static Colour har(int beat, int semitone) {
        return new Colour(beat, semitone, Part.HARMONY);
    }

    // Tunes keyed by VillagerType registry path. Each row is 8 beats; R = rest.

    private static final Map<String, Tune> TUNES = Map.of(
            // PLAINS: major pentatonic lullaby.
            "plains", new Tune(14, 0,
                    new int[]{2, 4, 7, 4, 0, 2, 4, R},
                    new int[]{2, 4, 7, 9, 7, 4, 2, 0},
                    new int[]{-5, R, 4, R, -3, R, 0, R},
                    new int[]{-5, R, 4, R, 4, R, -5, -8},
                    new int[]{-12, R, R, R, -5, R, R, R},
                    new int[]{-12, R, R, R, -5, R, R, -12},
                    new Colour[]{har(1, 3), mel(5, 3)}),

            // DESERT: Hijaz colour (b2, major 3rd) over a drone.
            "desert", new Tune(16, 0,
                    new int[]{4, 5, 1, 4, 7, 5, 4, R},
                    new int[]{4, 5, 1, 4, 5, 4, 1, 0},
                    new int[]{0, R, -2, R, 4, R, 0, R},
                    new int[]{0, R, -2, R, 1, R, -2, -5},
                    new int[]{-12, R, R, R, -12, R, R, R},
                    new int[]{-12, R, R, R, -5, R, R, -12},
                    new Colour[]{mel(2, 3), har(6, -1)}),

            // SAVANNA: bright pentatonic with a leap to the 5th, walking bass.
            "savanna", new Tune(12, 2,
                    new int[]{0, 2, 7, 4, 9, 7, 2, R},
                    new int[]{0, 2, 7, 4, 9, 7, 2, 0},
                    new int[]{-5, R, 4, R, 4, R, -3, R},
                    new int[]{-5, R, 4, R, 4, R, -3, -5},
                    new int[]{-12, R, -5, R, -12, R, -5, R},
                    new int[]{-12, R, -5, R, -12, R, -5, -12},
                    new Colour[]{mel(3, 8), har(5, -2)}),

            // SNOW: minor pentatonic, high, sparse and slow.
            "snow", new Tune(18, 0,
                    new int[]{7, 10, 12, 7, 10, 5, 3, R},
                    new int[]{7, 10, 12, 7, 5, 3, 2, 0},
                    new int[]{3, R, 7, R, 7, R, 0, R},
                    new int[]{3, R, 7, R, 0, R, -2, -5},
                    new int[]{-12, R, R, R, R, R, R, R},
                    new int[]{-12, R, R, R, -5, R, R, -12},
                    new Colour[]{har(2, 6), mel(5, 4)}),

            // TAIGA: dorian folk tune climbing by 4th and 5th leaps.
            "taiga", new Tune(14, 0,
                    new int[]{0, 5, 3, 7, 5, 10, 9, R},
                    new int[]{0, 5, 3, 7, 5, 3, 2, 0},
                    new int[]{-5, R, 0, R, 2, R, 5, R},
                    new int[]{-5, R, 0, R, 2, R, -3, -5},
                    new int[]{-12, R, R, R, -5, R, R, R},
                    new int[]{-12, R, R, R, -5, R, R, -12},
                    new Colour[]{mel(3, 6), har(5, 4)}),

            // JUNGLE: lydian, quick, circling the 3rd and #4.
            "jungle", new Tune(11, 1,
                    new int[]{4, 7, 6, 4, 9, 6, 7, R},
                    new int[]{4, 7, 6, 4, 2, 4, 2, 0},
                    new int[]{0, R, 2, R, 4, R, 4, R},
                    new int[]{0, R, 2, R, -1, R, -1, -5},
                    new int[]{-12, R, -5, R, -12, R, -5, R},
                    new int[]{-12, R, -5, R, -12, R, -5, -12},
                    new Colour[]{har(1, 1), mel(4, 8)}),

            // SWAMP: natural minor, low and slow, over a drone.
            "swamp", new Tune(17, 0,
                    new int[]{0, -2, 3, 2, 0, -4, -2, R},
                    new int[]{0, -2, 3, 2, -2, -4, -2, 0},
                    new int[]{-5, R, 0, R, -9, R, -5, R},
                    new int[]{-5, R, 0, R, -5, R, -5, -9},
                    new int[]{-12, R, R, R, -12, R, R, R},
                    new int[]{-12, R, R, R, -7, R, R, -12},
                    new Colour[]{har(2, 1), mel(5, -3)})
    );

    /** Built songs, by villager type id. */
    public static final Map<String, Song> SONGS = buildAll();

    /** Song for a villager type id (e.g. "desert"); falls back to plains for unknown/modded types. */
    public static Song forVillagerType(String typeId) {
        return SONGS.getOrDefault(typeId, SONGS.get("plains"));
    }

    private static Map<String, Song> buildAll() {
        Map<String, Song> out = new java.util.HashMap<>();
        TUNES.forEach((id, tune) -> out.put(id, build(id, tune)));
        return Map.copyOf(out);
    }

    private static Song build(String id, Tune t) {
        int passes = Math.max(4, Math.round(TARGET_TICKS / (8f * t.ticksPerBeat())));
        int tpb = t.ticksPerBeat();
        List<Note> notes = new ArrayList<>();

        for (int pass = 0; pass < passes; pass++) {
            boolean last = pass == passes - 1;
            int[] mel = last ? t.cadence() : t.melody();
            int[] har = last ? t.cadenceHarm() : t.harmony();
            int[] bas = last ? t.cadenceBass() : t.bass();
            // Gentle swell as voices join; the final pass softens slightly.
            float vol = last ? 0.75f : Math.min(0.9f, 0.65f + 0.05f * pass);

            for (int b = 0; b < 8; b++) {
                int tick = (pass * 8 + b) * tpb;
                if (mel[b] != R) {
                    notes.add(new Note(Part.MELODY, tick, pitch(mel[b] + t.key()), vol));
                }
                if (pass >= 1 && bas[b] != R) {
                    notes.add(new Note(Part.BASS, tick, pitch(bas[b] + t.key()), vol * 0.9f));
                }
                if (pass >= 2 && har[b] != R) {
                    notes.add(new Note(Part.HARMONY, tick, pitch(har[b] + t.key()), vol * 0.7f));
                }
            }

            // Chromatic colour notes; harmony ones only once the harmony has joined.
            boolean colourPass = !last && (pass == 1 || pass == passes - 2);
            if (colourPass) {
                for (Colour c : t.colours()) {
                    if (c.part() == Part.HARMONY && pass < 2) {
                        continue;
                    }
                    int tick = (pass * 8 + c.beat()) * tpb + tpb / 2; // the off-beat
                    float partVol = c.part() == Part.HARMONY ? vol * 0.7f : vol;
                    notes.add(new Note(c.part(), tick, pitch(c.semitone() + t.key()), partVol * COLOUR_VOLUME));
                }
            }
        }
        notes.sort((a, b) -> Integer.compare(a.tick(), b.tick()));
        int length = passes * 8 * tpb + RING_OUT_TICKS;
        return new Song(id, Collections.unmodifiableList(notes), length);
    }

    /** Semitones -> Minecraft pitch, folding by octaves into 0.5..2.0 (+/-12 semitones). */
    private static float pitch(int semitones) {
        while (semitones > 12) semitones -= 12;
        while (semitones < -12) semitones += 12;
        return (float) Math.pow(2.0, semitones / 12.0);
    }
}
