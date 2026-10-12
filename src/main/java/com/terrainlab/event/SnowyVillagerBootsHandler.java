package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Gives snow-type villagers leather boots when they join the level. */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class SnowyVillagerBootsHandler {

    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Villager villager)) return;

        boolean isSnowVillager = villager.getVillagerData().type().is(VillagerType.SNOW);
        if (!isSnowVillager) return;

        if (villager.getItemBySlot(EquipmentSlot.FEET).isEmpty()) {
            villager.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            // Never dropped.
            villager.setDropChance(EquipmentSlot.FEET, 0.0f);
        }
    }
}
