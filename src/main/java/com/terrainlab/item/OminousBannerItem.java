package com.terrainlab.item;

import com.terrainlab.registry.ModItems;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;

/**
 * Registered Ominous Banner item with its own ID and translation key, so it can be used in trades and
 * loot tables and renamed normally. Places as a plain white banner.
 * <p>
 * BannerPattern is a datapack registry, so the pattern layers cannot be default components; use
 * {@link #createStack} wherever a live registry lookup is available.
 */
public class OminousBannerItem extends BannerItem {

    public OminousBannerItem(Item.Properties properties) {
        super(Blocks.WHITE_BANNER, Blocks.WHITE_WALL_BANNER, properties);
    }

    /** Builds the decorated stack with vanilla's Ominous Banner pattern layers, hidden from the tooltip. */
    public static ItemStack createStack(HolderGetter<BannerPattern> patternGetter) {
        ItemStack stack = new ItemStack(ModItems.OMINOUS_BANNER.get());
        BannerPatternLayers patterns = new BannerPatternLayers.Builder()
                .addIfRegistered(patternGetter, BannerPatterns.RHOMBUS_MIDDLE, DyeColor.CYAN)
                .addIfRegistered(patternGetter, BannerPatterns.STRIPE_BOTTOM, DyeColor.LIGHT_GRAY)
                .addIfRegistered(patternGetter, BannerPatterns.STRIPE_CENTER, DyeColor.GRAY)
                .addIfRegistered(patternGetter, BannerPatterns.BORDER, DyeColor.LIGHT_GRAY)
                .addIfRegistered(patternGetter, BannerPatterns.STRIPE_MIDDLE, DyeColor.BLACK)
                .addIfRegistered(patternGetter, BannerPatterns.HALF_HORIZONTAL, DyeColor.LIGHT_GRAY)
                .addIfRegistered(patternGetter, BannerPatterns.CIRCLE_MIDDLE, DyeColor.LIGHT_GRAY)
                .addIfRegistered(patternGetter, BannerPatterns.BORDER, DyeColor.BLACK)
                .build();
        stack.set(DataComponents.BANNER_PATTERNS, patterns);
        stack.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.BANNER_PATTERNS, true));
        return stack;
    }

    public static ItemStack createStack(RegistryAccess registryAccess) {
        return createStack(registryAccess.lookupOrThrow(Registries.BANNER_PATTERN));
    }
}
