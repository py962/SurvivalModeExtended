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
 * Trail Trader variant found in badlands. Trade chain: Outlaws Hideout
 * (Ominous Banner), Trappers Lodge (Sentry armour trim), Pillager Outpost
 * (a found map), Woodland Mansion (Totem of Undying).
 */
public class RangerEntity extends AbstractTrailTraderEntity {

    private static final TagKey<Structure> TAG_OUTLAWS_HIDEOUT = locateTag("outlaws_hideout");
    private static final TagKey<Structure> TAG_TRAPPERS_LODGE = locateTag("trappers_lodge");
    private static final TagKey<Structure> TAG_PILLAGERS_OUTPOST = locateTag("pillagers_outpost");
    private static final TagKey<Structure> TAG_WOODLAND_MANSION = locateTag("woodland_mansion");

    private static TagKey<Structure> locateTag(String path) {
        return TagKey.create(Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath(TerrainLab.MODID, "locate/" + path));
    }

    private static final ItemLike[] RELICS = {
            ModItems.OMINOUS_BANNER.get(),
            Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE,
            Items.FILLED_MAP, // a found map, see AbstractTrailTraderEntity#isValidRelic
            Items.TOTEM_OF_UNDYING,
    };

    private static final String[] MAP_DESTINATIONS = {
            "Outlaws Hideout",
            "Trappers Lodge",
            "Pillagers Outpost",
            "Woodland Mansion",
    };

    private static final TagKey<Structure>[] MAP_DESTINATION_TAGS = new TagKey[]{
            TAG_OUTLAWS_HIDEOUT,
            TAG_TRAPPERS_LODGE,
            TAG_PILLAGERS_OUTPOST,
            TAG_WOODLAND_MANSION,
    };

    public RangerEntity(EntityType<? extends WanderingTrader> type, Level level) {
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

    @Override
    protected String getDialogueTranslationKeyPrefix() {
        return "message.terrainlab.trail_trader.ranger";
    }

    /** Pillager Map: found in Pillager Outpost chests. */
    @Override
    protected String getRequiredRelicMap() {
        return "pillager_map";
    }

    @Override
    protected ResourceKey<LootTable> getDonkeyCargoLootTable() {
        return ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath(TerrainLab.MODID, "donkey_cargo/ranger"));
    }
}
