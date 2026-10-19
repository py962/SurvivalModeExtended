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
    FLOWERS("seasonal/flowers", EnumSet.of(Season.EARLY_SPRING, Season.LATE_SPRING, Season.MIDSUMMER)),
    PUMPKINS("seasonal/pumpkins", EnumSet.of(Season.MIDSUMMER, Season.EARLY_AUTUMN, Season.LATE_AUTUMN)),
    MUSHROOMS("seasonal/mushrooms", EnumSet.of(Season.EARLY_AUTUMN, Season.LATE_AUTUMN, Season.MIDWINTER, Season.EARLY_SPRING));

    /** Biomes whose naturally generated ground plants follow the seasons. */
    public static final TagKey<Biome> AFFECTED_BIOMES =
            TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(TerrainLab.MODID, "has_seasonal_ground_plants"));

    private static final SeasonalPlantCategory[] VALUES = values();

    private final TagKey<Block> tag;
    private final EnumSet<Season> inSeason;

    SeasonalPlantCategory(String tagPath, EnumSet<Season> inSeason) {
        this.tag = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(TerrainLab.MODID, tagPath));
        this.inSeason = inSeason;
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
