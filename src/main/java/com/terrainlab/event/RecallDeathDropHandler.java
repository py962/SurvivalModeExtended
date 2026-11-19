package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Recall enchantment: on death, Recall items are removed from the drops and stashed in the
 * player's persisted NBT (copied across respawn and saved with the player); on respawn they
 * are dropped at the player's feet, wherever that is. Works for any enchantable item, including
 * one carried in a bundle (the bundle itself drops as normal); a
 * Recall saddle still on a mount stays there. With keepInventory nothing drops, so nothing
 * is stashed.
 */
public class RecallDeathDropHandler {

    private static final String STASH = "terrainlab_recall_items";

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Holder<Enchantment> recall = ModEnchantments.holder(player.registryAccess(), ModEnchantments.RECALL);
        RegistryOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);

        CompoundTag persisted = persisted(player);
        ListTag stash = persisted.getListOrEmpty(STASH);

        Iterator<ItemEntity> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next().getItem();
            if (stack.isEmpty()) {
                continue;
            }
            if (EnchantmentHelper.getItemEnchantmentLevel(recall, stack) > 0) {
                if (stash(stack, stash, ops, player)) {
                    iterator.remove();
                }
                continue;
            }
            takeRecallFromBundle(stack, recall, stash, ops, player);
        }

        if (!stash.isEmpty()) {
            persisted.put(STASH, stash);
            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        CompoundTag persisted = persisted(player);
        ListTag stash = persisted.getListOrEmpty(STASH);
        if (stash.isEmpty()) {
            return;
        }
        persisted.remove(STASH);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);

        RegistryOps<Tag> ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        for (Tag tag : stash) {
            ItemStack.CODEC.parse(ops, tag).result().ifPresent(stack -> {
                ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.25, player.getZ(), stack);
                item.setDeltaMovement(0.0, 0.1, 0.0);
                item.setDefaultPickUpDelay();
                player.level().addFreshEntity(item);
            });
        }
    }

    private static boolean stash(ItemStack stack, ListTag stash, RegistryOps<Tag> ops, ServerPlayer player) {
        var encoded = ItemStack.CODEC.encodeStart(ops, stack).result();
        if (encoded.isEmpty()) {
            TerrainLab.LOGGER.warn("[RECALL] couldn't save {} for {} - left in the drops",
                    stack, player.getName().getString());
            return false;
        }
        stash.add(encoded.get());
        return true;
    }

    /** Stashes Recall items held in a bundle (and bundles within it); returns whether its contents changed. */
    private static boolean takeRecallFromBundle(ItemStack bundle, Holder<Enchantment> recall, ListTag stash,
                                                RegistryOps<Tag> ops, ServerPlayer player) {
        BundleContents contents = bundle.get(DataComponents.BUNDLE_CONTENTS);
        if (contents == null || contents.isEmpty()) {
            return false;
        }

        List<ItemStack> kept = new ArrayList<>();
        boolean changed = false;
        for (ItemStack inner : contents.itemsCopy()) {
            if (EnchantmentHelper.getItemEnchantmentLevel(recall, inner) > 0 && stash(inner, stash, ops, player)) {
                changed = true;
                continue;
            }
            changed |= takeRecallFromBundle(inner, recall, stash, ops, player);
            kept.add(inner);
        }

        if (changed) {
            bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(kept));
        }
        return changed;
    }

    private static CompoundTag persisted(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).orElseGet(CompoundTag::new);
    }
}
