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
 * Trail Trader variant found in old-growth and taiga forests. Trade chain:
 * Trail Ruins (Ancient Artefact), Desert Temple (Swift Sneak book), Jungle
 * Temple (a found map), Ancient City (Echo Shard).
 */
public class ArcheologistEntity extends AbstractTrailTraderEntity {

    private static final TagKey<Structure> TAG_TRAIL_RUINS = locateTag("trail_ruins");
    private static final TagKey<Structure> TAG_DESERT_TEMPLE = locateTag("desert_temple");
    private static final TagKey<Structure> TAG_JUNGLE_TEMPLE = locateTag("jungle_temple");
    private static final TagKey<Structure> TAG_ANCIENT_CITY = locateTag("ancient_city");

    private static TagKey<Structure> locateTag(String path) {
        return TagKey.create(Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath(TerrainLab.MODID, "locate/" + path));
    }

    private static final ItemLike[] RELICS = {
            ModItems.ANCIENT_ARTEFACT.get(),
            Items.ENCHANTED_BOOK, // must carry Swift Sneak, see getRequiredRelicEnchantment
            Items.FILLED_MAP, // a found map, see AbstractTrailTraderEntity#isValidRelic
            Items.ECHO_SHARD,
    };

    private static final String[] MAP_DESTINATIONS = {
            "Trail Ruins",
            "Desert Temple",
            "Jungle Temple",
            "Ancient City",
    };

    private static final TagKey<Structure>[] MAP_DESTINATION_TAGS = new TagKey[]{
            TAG_TRAIL_RUINS,
            TAG_DESERT_TEMPLE,
            TAG_JUNGLE_TEMPLE,
            TAG_ANCIENT_CITY,
    };

    public ArcheologistEntity(EntityType<? extends WanderingTrader> type, Level level) {
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

    /** Tier 2's book must carry Swift Sneak (any level). */
    @Override
    protected ResourceKey<Enchantment> getRequiredRelicEnchantment(int tier) {
        return tier == 2 ? Enchantments.SWIFT_SNEAK : null;
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
        return "message.terrainlab.trail_trader.archeologist";
    }

    /** Temple Map: found in Jungle Temple chests. */
    @Override
    protected String getRequiredRelicMap() {
        return "temple_map";
    }

    @Override
    protected ResourceKey<LootTable> getDonkeyCargoLootTable() {
        return ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath(TerrainLab.MODID, "donkey_cargo/archeologist"));
    }
}
