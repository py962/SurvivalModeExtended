package com.terrainlab.village.work;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.DropChances;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * The display fishing rod a fisherman holds while fishing ({@link FishermanFishingBehavior}). The rod is
 * marked with a custom-data tag so only our own rod is ever removed, and the synced
 * {@code VILLAGER_FISHING} flag tells the client to render it as a held rod and vanilla's
 * ShowTradesToPlayer to leave the hand alone (ShowTradesToPlayerFishingMixin). The hand drop chance is 0
 * while held, so the rod is never loot. A rod saved with the entity (unloaded mid-fishing, or carried over
 * by a zombie conversion) is removed when the entity joins a level.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class FishermanRod {

    private static final String MARKER = "terrainlab_fisherman_rod";

    private FishermanRod() {
    }

    public static boolean isFishing(Villager villager) {
        return villager.getData(ModAttachments.VILLAGER_FISHING.get());
    }

    public static boolean isOurRod(ItemStack stack) {
        return stack.is(Items.FISHING_ROD)
                && stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains(MARKER);
    }

    private static ItemStack create() {
        ItemStack rod = new ItemStack(Items.FISHING_ROD);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MARKER, true);
        rod.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return rod;
    }

    /**
     * Puts the rod in the main hand (re-applied every fishing tick, so anything that replaced it is
     * overwritten) and sets the fishing flag. Returns the hand drop chance from before fishing began, or
     * {@code previousDropChance} when already fishing.
     */
    static float hold(Villager villager, float previousDropChance) {
        float saved = previousDropChance;
        if (!isFishing(villager)) {
            saved = villager.getDropChances().byEquipment(EquipmentSlot.MAINHAND);
            villager.setData(ModAttachments.VILLAGER_FISHING.get(), true);
        }
        if (!isOurRod(villager.getMainHandItem())) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, create());
        }
        if (villager.getDropChances().byEquipment(EquipmentSlot.MAINHAND) != 0.0F) {
            villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
        return saved;
    }

    /** Removes our rod (if still in hand), clears the flag and restores the hand drop chance. */
    static void putAway(Villager villager, float dropChance) {
        if (!isFishing(villager) && !isOurRod(villager.getMainHandItem())) {
            return;
        }
        if (isOurRod(villager.getMainHandItem())) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        villager.setDropChance(EquipmentSlot.MAINHAND, dropChance);
        villager.setData(ModAttachments.VILLAGER_FISHING.get(), false);
    }

    /** Stale rod from a save (or conversion): nothing is fishing on join, so remove it. */
    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (isOurRod(mob.getMainHandItem())) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            mob.setDropChance(EquipmentSlot.MAINHAND, DropChances.DEFAULT_EQUIPMENT_DROP_CHANCE);
        }
    }
}
