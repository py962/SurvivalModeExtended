
        package com.terrainlab.worldgen;

import com.mojang.datafixers.util.Pair;
import com.terrainlab.TerrainLab;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Isolation difficulty village rules: villages are rare (only {@link #VILLAGE_RARITY}
 * of village start positions generate) and always abandoned (the start piece is drawn
 * only from the start pool's zombie elements).
 *
 * Applied in JigsawStructureHardcoreVillageMixin, which handles structure generation
 * and structure checks.
 *
 * Worldgen threads cannot safely rely on LevelData access, so the world's difficulty
 * is copied into a static flag when the server is about to start and cleared when it
 * stops. This prevents one world from inheriting the previous world's state.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class HardcoreVillageRules {

    /** Fraction of village start positions that generate in Isolation difficulty. */
    public static final double VILLAGE_RARITY = 0.25;

    private static final long POSITION_SALT = 0x5A6F6D6269655649L;

    private static volatile boolean isolation;

    /** Zombie-only copy of each start pool, or empty if the pool has no zombie elements. */
    private static final Map<StructureTemplatePool, Optional<StructureTemplatePool>>
            ZOMBIE_START_POOLS = new ConcurrentHashMap<>();

    private HardcoreVillageRules() {
    }

    public static boolean isActive() {
        return isolation;
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        ZOMBIE_START_POOLS.clear();
        isolation = event.getServer().getWorldData().getDifficulty()
                == net.minecraft.world.Difficulty.HARD;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        isolation = false;
        ZOMBIE_START_POOLS.clear();
    }

    /**
     * A village is a structure in {@code #minecraft:village} or a jigsaw structure
     * whose start pool path begins with {@code village/}.
     */
    public static boolean isVillage(
            Structure structure,
            Holder<StructureTemplatePool> startPool,
            RegistryAccess registryAccess
    ) {
        if (startPool.unwrapKey()
                .map(key -> key.identifier().getPath().startsWith("village/"))
                .orElse(false)) {
            return true;
        }

        return registryAccess.lookupOrThrow(Registries.STRUCTURE)
                .wrapAsHolder(structure)
                .is(StructureTags.VILLAGE);
    }

    /**
     * Deterministic per-position roll from the world seed and chunk position.
     * The structure id is deliberately excluded so retries at the same position
     * share the same decision.
     */
    public static boolean keepVillagePosition(long worldSeed, ChunkPos pos) {
        long h = RandomSupport.mixStafford13(
                worldSeed ^ POSITION_SALT ^ RandomSupport.mixStafford13(pos.toLong())
        );

        return (h >>> 11) * 0x1.0p-53 < VILLAGE_RARITY;
    }

    /** Returns the pool restricted to zombie elements, preserving their weights. */
    public static Optional<StructureTemplatePool> zombieStartPool(
            StructureTemplatePool pool
    ) {
        return ZOMBIE_START_POOLS.computeIfAbsent(
                pool,
                HardcoreVillageRules::buildZombiePool
        );
    }

    private static Optional<StructureTemplatePool> buildZombiePool(
            StructureTemplatePool pool
    ) {
        List<Pair<StructurePoolElement, Integer>> zombies = new ArrayList<>();

        for (Pair<StructurePoolElement, Integer> entry : pool.getTemplates()) {
            if (entry.getSecond() > 0 && isZombieElement(entry.getFirst())) {
                zombies.add(entry);
            }
        }

        return zombies.isEmpty()
                ? Optional.empty()
                : Optional.of(new StructureTemplatePool(pool.getFallback(), zombies));
    }

    private static boolean isZombieElement(StructurePoolElement element) {
        if (!(element instanceof SinglePoolElement single)) {
            return false;
        }

        try {
            Identifier location = single.getTemplateLocation();
            return location.getPath().contains("zombie");
        } catch (RuntimeException inlineTemplate) {
            return false;
        }
    }
}
