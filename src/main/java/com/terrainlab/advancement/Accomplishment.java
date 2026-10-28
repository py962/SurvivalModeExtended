package com.terrainlab.advancement;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * The five end-of-game accomplishments shown on the accomplishment card, highest rank first.
 * Each is a challenge advancement data/terrainlab/advancement/survival/&lt;id&gt;.json awarded through
 * the terrainlab:event trigger with the same event name. The icons mirror the advancement JSON.
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

    /** Event name, advancement path (survival/&lt;id&gt;) and lang-key segment. */
    public String id() {
        return id;
    }

    public Item icon() {
        return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(icon));
    }

    public String advancementPath() {
        return "survival/" + id;
    }

    /** Lang key of the large card title, e.g. "ULTIMATE SURVIVOR". */
    public String cardTitleKey() {
        return "gui.terrainlab.accomplishment.title." + id;
    }

    /** Lang key of the advancement title, used as the badge name. */
    public String nameKey() {
        return "advancements.terrainlab." + id + ".title";
    }

    public static Accomplishment byId(String id) {
        for (Accomplishment a : values()) {
            if (a.id.equals(id)) {
                return a;
            }
        }
        return null;
    }
}
