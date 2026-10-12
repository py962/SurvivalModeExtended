package com.terrainlab.food;

/** Custom food tiers applied by {@link FoodCategoryTable}; items without a category keep vanilla behaviour. */
public enum FoodCategory {
    HEALTH, // Carrot, Apple (heal over time), Golden Carrot (instant); zero hunger
    COOL, // Melon Slice; zero hunger, cooling
    WARMTH, // Mushroom/Rabbit Stew, Beetroot Soup; zero hunger, warming
    ENERGY, // Cocoa Beans, Blue Orchid Stew; zero hunger, energy instead
    HUNGER_1, // Snack, 2 points
    HUNGER_2, // Light meal, 6 points
    HUNGER_3, // Standard meal, 10 points
    RISKY // Vanilla behaviour (raw meats, Rotten Flesh, Spider Eye, Pufferfish); not in the table
}
