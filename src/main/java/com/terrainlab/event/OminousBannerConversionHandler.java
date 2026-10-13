package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.item.OminousBannerItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Turns vanilla ominous banners (a white banner carrying exactly the ominous pattern) into the
 * mod's Ominous Banner, keeping count and custom name. Needed because a placed banner is a vanilla
 * banner block, so breaking one (including the wall banners in pillager outposts) drops a vanilla
 * white banner. Runs when a dropped banner enters the world, and on a player's inventory at login.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class OminousBannerConversionHandler {

    private OminousBannerConversionHandler() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ItemEntity itemEntity)) {
            return;
        }
        ItemStack converted = convert(event.getLevel(), itemEntity.getItem());
        if (converted != null) {
            itemEntity.setItem(converted);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack converted = convert(player.level(), inventory.getItem(slot));
            if (converted != null) {
                inventory.setItem(slot, converted);
            }
        }
    }

    /** The mod banner replacing a vanilla ominous banner, or null if the stack is something else. */
    private static ItemStack convert(Level level, ItemStack stack) {
        if (!stack.is(Items.BANNER.white())) {
            return null;
        }
        BannerPatternLayers patterns = stack.get(DataComponents.BANNER_PATTERNS);
        if (patterns == null || patterns.layers().isEmpty()) {
            return null;
        }
        ItemStack ominous = OminousBannerItem.createStack(
                level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN));
        if (!patterns.equals(ominous.get(DataComponents.BANNER_PATTERNS))) {
            return null;
        }
        ominous.setCount(stack.getCount());
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            ominous.set(DataComponents.CUSTOM_NAME, stack.get(DataComponents.CUSTOM_NAME));
        }
        return ominous;
    }
}
