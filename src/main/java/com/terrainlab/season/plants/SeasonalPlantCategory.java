package com.terrainlab.season.plants;

import com.terrainlab.TerrainLab;
import com.terrainlab.season.Season;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * Seasonal ground-plant groups, each a block tag plus the calendar states in which naturally generated
 * plants of that group are present. See {@link SeasonalPlantService}.
 */
public enum SeasonalPlantCategory {
    LEAF_LITTER("seasonal/leaf_litter", EnumSet.of(Season.MIDWINTER, Season.EARLY_AUTUMN, Season.LATE_AUTUMN)),
    // Flowers, pumpkins and mushrooms each come in gradually across the season before their peak and go
    // out gradually across the season after it (a whole-season stagger): flowers build through early
    // spring, peak in late spring and are gone by the end of midsummer; pumpkins build through early
    // autumn, peak in late autumn and are gone by the end of midwinter; mushrooms build through late autumn,
    // peak in midwinter and are gone by the end of early spring.
    FLOWERS("seasonal/flowers", EnumSet.of(Season.EARLY_SPRING, Season.LATE_SPRING), true),
    PUMPKINS("seasonal/pumpkins", EnumSet.of(Season.EARLY_AUTUMN, Season.LATE_AUTUMN), true),
    MUSHROOMS("seasonal/mushrooms", EnumSet.of(Season.LATE_AUTUMN, Season.MIDWINTER), true);

    /** Biomes whose naturally generated ground plants follow the seasons. */
    public static final TagKey<Biome> AFFECTED_BIOMES =
            TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(TerrainLab.MODID, "has_seasonal_ground_plants"));

    private static final SeasonalPlantCategory[] VALUES = values();

    private final TagKey<Block> tag;
    private final EnumSet<Season> inSeason;
    private final long stagger;
    private final boolean wholeSeasonStagger;

    SeasonalPlantCategory(String tagPath, EnumSet<Season> inSeason) {
        this(tagPath, inSeason, SeasonalPlantService.STAGGER_TICKS, false);
    }

    /** A whole-season stagger: the flip spread is one calendar state, whatever the year length. */
    SeasonalPlantCategory(String tagPath, EnumSet<Season> inSeason, boolean wholeSeasonStagger) {
        this(tagPath, inSeason, 0L, wholeSeasonStagger);
    }

    SeasonalPlantCategory(String tagPath, EnumSet<Season> inSeason, long stagger, boolean wholeSeasonStagger) {
        this.tag = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(TerrainLab.MODID, tagPath));
        this.inSeason = inSeason;
        this.stagger = stagger;
        this.wholeSeasonStagger = wholeSeasonStagger;
    }

    /** Spread of the per-position flip delay after a boundary for this group. */
    public long stagger() {
        return wholeSeasonStagger ? Season.stateLengthTicks() : stagger;
    }

    /** Longest flip spread among the groups that change between {@code from} and {@code to}; 0 if none change. */
    public static long changeWindow(Season from, Season to) {
        long window = 0L;
        for (SeasonalPlantCategory category : VALUES) {
            if (category.isInSeason(from) != category.isInSeason(to)) {
                window = Math.max(window, category.stagger());
            }
        }
        return window;
    }

    public boolean isInSeason(Season season) {
        return inSeason.contains(season);
    }

    public static @Nullable SeasonalPlantCategory of(BlockState state) {
        for (SeasonalPlantCategory category : VALUES) {
            if (state.is(category.tag)) {
                return category;
            }
        }
        return null;
    }

    /** Whether any group appears or disappears when the calendar moves from {@code from} to {@code to}. */
    public static boolean anyChange(Season from, Season to) {
        for (SeasonalPlantCategory category : VALUES) {
            if (category.isInSeason(from) != category.isInSeason(to)) {
                return true;
            }
        }
        return false;
    }
}
