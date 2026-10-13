package com.terrainlab.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

/** World-wide mapping from Ancient Codex glyph triples to enchantment ids, stored on the overworld. */
public class CodexSavedData extends SavedData {

    public static final SavedDataType<CodexSavedData> TYPE = new SavedDataType<>(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(com.terrainlab.TerrainLab.MODID, "codex_mappings"),
            CodexSavedData::new,
            level -> RecordCodecBuilder.create(instance -> instance.group(
                    RecordCodecBuilder.point(level),
                    Codec.unboundedMap(Codec.STRING, Identifier.CODEC.optionalFieldOf("id")
                                    .codec()).fieldOf("mappings")
                            .forGetter(sd -> {
                                Map<String, Optional<Identifier>> out = new HashMap<>();
                                sd.mappings.forEach((k, v) -> out.put(k, Optional.ofNullable(v)));
                                return out;
                            })
            ).apply(instance, (lvl, map) -> {
                CodexSavedData data = new CodexSavedData(lvl);
                map.forEach((k, v) -> data.mappings.put(k, v.orElse(null)));
                return data;
            }))
    );

    private final Map<String, Identifier> mappings = new HashMap<>();
    private final ServerLevel level;

    public CodexSavedData() {
        this.level = null;
    }

    public CodexSavedData(ServerLevel level) {
        this.level = level;
    }

    public static CodexSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(TYPE);
    }

    /** Namespace whose enchantments are always given a glyph combination. */
    private static final String MOD_NAMESPACE = "terrainlab";

    /**
     * Assigns enchantments to the 56 glyph combinations; idempotent, safe to call on every server start.
     * <ul>
     *   <li>New world: every registered enchantment gets a combination, shuffled with the world
     *       seed; unused combinations stay empty.</li>
     *   <li>Existing world: unmapped enchantments are placed on free combinations in a seeded
     *       order, TerrainLab's own first. Existing mappings never change.</li>
     *   <li>Enchantments beyond the 56 combinations are left out; TerrainLab's own are kept.</li>
     * </ul>
     */
    public void generateMappings(long worldSeed,
                                 net.minecraft.core.HolderLookup.Provider registries) {
        List<Identifier> modEnchantments = new ArrayList<>();
        List<Identifier> otherEnchantments = new ArrayList<>();
        registries.lookup(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .ifPresent(registry -> registry.listElements().forEach(holder -> {
                    Identifier id = holder.key().identifier();
                    (MOD_NAMESPACE.equals(id.getNamespace()) ? modEnchantments : otherEnchantments).add(id);
                }));
        // Registry iteration order isn't guaranteed stable - sort before shuffling.
        modEnchantments.sort(Comparator.comparing(Identifier::toString));
        otherEnchantments.sort(Comparator.comparing(Identifier::toString));

        List<int[]> combinations = new ArrayList<>();
        for (int a = 0; a < 6; a++)
            for (int b = a + 1; b < 7; b++)
                for (int c = b + 1; c < 8; c++)
                    combinations.add(new int[]{a, b, c});

        if (mappings.isEmpty()) {
            Random rng = new Random(worldSeed ^ 0x436F646578L);
            Collections.shuffle(combinations, rng);
            Collections.shuffle(otherEnchantments, rng);

            int total = Math.min(combinations.size(), modEnchantments.size() + otherEnchantments.size());
            List<Identifier> chosen = new ArrayList<>(modEnchantments);
            for (Identifier id : otherEnchantments) {
                if (chosen.size() >= total) break;
                chosen.add(id);
            }
            Collections.shuffle(chosen, rng);
            for (int i = 0; i < combinations.size(); i++) {
                mappings.put(toKey(combinations.get(i)), i < chosen.size() ? chosen.get(i) : null);
            }
            setDirty();
            return;
        }

        // Existing world: add missing enchantments to free combinations (TerrainLab's own first).
        Set<Identifier> mapped = new HashSet<>(mappings.values());
        List<String> freeKeys = new ArrayList<>();
        for (int[] combo : combinations) {
            String key = toKey(combo);
            if (mappings.get(key) == null) {
                freeKeys.add(key);
            }
        }
        Random migrateRng = new Random(worldSeed ^ 0x4D6F64456EL);
        Collections.shuffle(freeKeys, migrateRng);
        Collections.shuffle(otherEnchantments, migrateRng);
        List<Identifier> wanted = new ArrayList<>(modEnchantments);
        wanted.addAll(otherEnchantments);
        boolean changed = false;
        for (Identifier id : wanted) {
            if (!mapped.contains(id) && !freeKeys.isEmpty()) {
                mappings.put(freeKeys.remove(0), id);
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    public Identifier getEnchantment(int[] glyphs) {
        if (glyphs.length != 3) return null;
        int[] sorted = glyphs.clone();
        Arrays.sort(sorted);
        return mappings.get(toKey(sorted));
    }

    public boolean isMappingGenerated() {
        return !mappings.isEmpty();
    }

    private static String toKey(int[] triple) {
        return triple[0] + "," + triple[1] + "," + triple[2];
    }
}
