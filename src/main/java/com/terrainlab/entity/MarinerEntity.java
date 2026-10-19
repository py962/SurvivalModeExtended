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
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Trail Trader variant found on beaches and stony shores. Trade chain:
 * Treasure Chest, i.e. vanilla Buried Treasure (Trident), Pirates Cove
 * (Loyalty book), Ocean Monument (a found map), Trial Chamber (Heavy Core).
 */
public class MarinerEntity extends AbstractTrailTraderEntity {

    private static final TagKey<Structure> TAG_TREASURE_CHEST = locateTag("treasure_chest");
    private static final TagKey<Structure> TAG_PIRATES_COVE = locateTag("pirates_cove");
    private static final TagKey<Structure> TAG_OCEAN_MONUMENT = locateTag("ocean_monument");
    private static final TagKey<Structure> TAG_TRIAL_CHAMBER = locateTag("trial_chamber");

    private static TagKey<Structure> locateTag(String path) {
        return TagKey.create(Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath(TerrainLab.MODID, "locate/" + path));
    }

    private static final ItemLike[] RELICS = {
            Items.TRIDENT,
            Items.ENCHANTED_BOOK, // must carry Loyalty, see getRequiredRelicEnchantment
            Items.BURIED_TRIAL_CHAMBERS_MAP, // the Sunken Chart relic, see AbstractTrailTraderEntity#isValidRelic
            Items.HEAVY_CORE,
    };

    private static final String[] MAP_DESTINATIONS = {
            "Treasure Chest",
            "Pirates Cove",
            "Ocean Monument",
            "Trial Chamber",
    };

    private static final TagKey<Structure>[] MAP_DESTINATION_TAGS = new TagKey[]{
            TAG_TREASURE_CHEST,
            TAG_PIRATES_COVE,
            TAG_OCEAN_MONUMENT,
            TAG_TRIAL_CHAMBER,
    };

    public MarinerEntity(EntityType<? extends WanderingTrader> type, Level level) {
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

    /** Tier 2's book must carry Loyalty (any level). */
    @Override
    protected ResourceKey<Enchantment> getRequiredRelicEnchantment(int tier) {
        return tier == 2 ? Enchantments.LOYALTY : null;
    }

    @Override
    protected String[] getMapDestinationNames() {
        return MAP_DESTINATIONS;
    }

    @Override
    protected TagKey<Structure>[] getMapDestinationStructureTags() {
        return MAP_DESTINATION_TAGS;
    }

    @Override
    protected String getDialogueTranslationKeyPrefix() {
        return "message.terrainlab.trail_trader.mariner";
    }

    /** Sunken Chart: found in the hidden room beneath the Ocean Monument. */
    @Override
    protected String getRequiredRelicMap() {
        return "sunken_chart";
    }

    @Override
    protected ResourceKey<LootTable> getDonkeyCargoLootTable() {
        return ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath(TerrainLab.MODID, "donkey_cargo/mariner"));
    }
}
