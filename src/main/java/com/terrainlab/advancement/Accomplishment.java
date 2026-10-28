
        package com.terrainlab.advancement;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * End-of-game accomplishments shown on the accomplishment card, highest
 * existing rank first.
 *
 * Each accomplishment has an advancement at
 * data/terrainlab/advancement/survival/<id>.json, awarded through the
 * terrainlab:event trigger with the same event name.
 */
public enum Accomplishment {
    ULTIMATE_SURVIVOR("ultimate_survivor", "dragon_egg"),
    SURVIVOR("survivor", "elytra"),
    NOMAD("nomad", "red_bed"),
    PACIFIST("pacifist", "lily_of_the_valley"),
    HERMIT("hermit", "moss_block");

    private final String id;
    private final String icon;

    Accomplishment(String id, String icon) {
        this.id = id;
        this.icon = icon;
    }

    /** Event name, advancement path (survival/<id>) and language-key segment. */
    public String id() {
        return id;
    }

    public Item icon() {
        return BuiltInRegistries.ITEM.getValue(
                Identifier.withDefaultNamespace(icon)
        );
    }

    public String advancementPath() {
        return "survival/" + id;
    }

    /** Language key of the large card title. */
    public String cardTitleKey() {
        return "gui.terrainlab.accomplishment.title." + id;
    }

    /** Language key of the advancement title. */
    public String nameKey() {
        return "advancements.terrainlab." + id + ".title";
    }

    public static Accomplishment byId(String id) {
        for (Accomplishment accomplishment : values()) {
            if (accomplishment.id.equals(id)) {
                return accomplishment;
            }
        }
        return null;
    }
}
