package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.entity.TrapperRaiderEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Gives Trapper raiders leather boots on joining the level so they do not sink in powder
 * snow. Done here because the Trapper is structure-placed from NBT and never goes through
 * finalizeSpawn. The boots are not rendered (illager models have no armor layer).
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class TrapperBootsHandler {

    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof TrapperRaiderEntity trapper)) return;

        if (trapper.getItemBySlot(EquipmentSlot.FEET).isEmpty()) {
            trapper.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            // Never dropped, so the boots are not farmable.
            trapper.setDropChance(EquipmentSlot.FEET, 0.0f);
        }
    }
}
