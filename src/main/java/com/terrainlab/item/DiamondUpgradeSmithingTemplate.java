package com.terrainlab.item;

import com.terrainlab.TerrainLab;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SmithingTemplateItem;

import java.util.List;

/**
 * Diamond Upgrade smithing template: iron equipment + diamond -> the diamond equivalent, built the
 * same way as vanilla's {@link SmithingTemplateItem#createNetheriteUpgradeTemplate}. The upgrade
 * recipes are plain {@code smithing_transform} JSONs ({@code data/terrainlab/recipe/diamond_*_smithing.json});
 * their result sets {@code minecraft:damage} to 0, applied after the iron item's components are copied,
 * so the diamond item keeps enchantments/name/trim/repair cost but comes out unworn.
 */
public final class DiamondUpgradeSmithingTemplate {

    private DiamondUpgradeSmithingTemplate() {}

    private static final Component APPLIES_TO = Component.translatable(key("applies_to")).withStyle(ChatFormatting.BLUE);
    private static final Component INGREDIENTS = Component.translatable(key("ingredients")).withStyle(ChatFormatting.BLUE);
    private static final Component BASE_SLOT_DESCRIPTION = Component.translatable(key("base_slot_description"));
    private static final Component ADDITIONS_SLOT_DESCRIPTION = Component.translatable(key("additions_slot_description"));

    /** Empty base-slot icons cycled by the smithing screen (vanilla's netherite list minus the nautilus armour). */
    private static final List<Identifier> BASE_SLOT_ICONS = List.of(
            slot("helmet"), slot("sword"), slot("chestplate"), slot("pickaxe"), slot("leggings"),
            slot("axe"), slot("boots"), slot("hoe"), slot("shovel"), slot("spear"));
    private static final List<Identifier> ADDITION_SLOT_ICONS = List.of(slot("diamond"));

    public static SmithingTemplateItem create(Item.Properties properties) {
        return new SmithingTemplateItem(APPLIES_TO, INGREDIENTS, BASE_SLOT_DESCRIPTION, ADDITIONS_SLOT_DESCRIPTION,
                BASE_SLOT_ICONS, ADDITION_SLOT_ICONS, properties);
    }

    /** {@code item.terrainlab.smithing_template.diamond_upgrade.<suffix>} */
    private static String key(String suffix) {
        return Util.makeDescriptionId("item",
                Identifier.fromNamespaceAndPath(TerrainLab.MODID, "smithing_template.diamond_upgrade." + suffix));
    }

    private static Identifier slot(String name) {
        return Identifier.withDefaultNamespace("container/slot/" + name);
    }
}
