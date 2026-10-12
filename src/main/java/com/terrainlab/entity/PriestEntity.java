package com.terrainlab.entity;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Trail Trader variant found in swamps and marshes. Trade chain: Ruined
 * Portal (Netherrack), Bone Temple (Nether Idol), Bastion (a found map),
 * Nether Fortress (Wither Skeleton Skull). The Priest stays in the
 * Overworld; tiers 2-4 are searched in the Nether at the scaled position.
 */
public class PriestEntity extends AbstractTrailTraderEntity {

    private static final TagKey<Structure> TAG_RUINED_PORTAL =
            locateTag("ruined_portal");

    private static final TagKey<Structure> TAG_BONE_TEMPLE =
            locateTag("bone_temple");

    private static final TagKey<Structure> TAG_BASTION_FORTRESS =
            locateTag("bastion_fortress");

    private static final TagKey<Structure> TAG_NETHER_FORTRESS =
            locateTag("nether_fortress");

    private static TagKey<Structure> locateTag(String path) {
        return TagKey.create(
                Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath(
                        TerrainLab.MODID,
                        "locate/" + path
                )
        );
    }

    private static final ItemLike[] RELICS = {
            Items.NETHERRACK,
            ModItems.NETHER_IDOL.get(),
            Items.FILLED_MAP, // a found map, see AbstractTrailTraderEntity#isValidRelic
            Items.WITHER_SKELETON_SKULL,
    };

    private static final String[] MAP_DESTINATIONS = {
            "Ruined Portal",
            "Bone Temple",
            "Bastion Fortress",
            "Nether Fortress",
    };

    private static final TagKey<Structure>[] MAP_DESTINATION_TAGS =
            new TagKey[]{
                    TAG_RUINED_PORTAL,
                    TAG_BONE_TEMPLE,
                    TAG_BASTION_FORTRESS,
                    TAG_NETHER_FORTRESS,
            };

    public PriestEntity(
            EntityType<? extends WanderingTrader> type,
            Level level) {

        super(type, level);
    }

    @Override
    protected ItemLike[] getRelicItems() {
        return RELICS;
    }

    @Override
    protected ItemLike getExperienceBottleItem() {
        return ModItems.EXPERIENCE_BOTTLE.get();
    }

    @Override
    protected String[] getMapDestinationNames() {
        return MAP_DESTINATIONS;
    }

    @Override
    protected TagKey<Structure>[] getMapDestinationStructureTags() {
        return MAP_DESTINATION_TAGS;
    }

    /** Tier 1 is in the Overworld; tiers 2-4 are in the Nether. */
    @Override
    protected ResourceKey<Level> getMapDestinationDimension(int tier) {
        return tier == 1
                ? Level.OVERWORLD
                : Level.NETHER;
    }

    @Override
    protected String getDialogueTranslationKeyPrefix() {
        return "message.terrainlab.trail_trader.priest";
    }

    /** Piglin Map: found in Bastion chests. */
    @Override
    protected String getRequiredRelicMap() {
        return "piglin_map";
    }

    @Override
    protected ResourceKey<LootTable> getDonkeyCargoLootTable() {
        return ResourceKey.create(
                Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath(
                        TerrainLab.MODID,
                        "donkey_cargo/priest"
                )
        );
    }
}
