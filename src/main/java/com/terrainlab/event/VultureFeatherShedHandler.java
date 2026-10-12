package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.entity.VultureEntity;
import com.terrainlab.registry.ModItems;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Vultures occasionally shed a Vulture Feather (about once every 8 minutes each). */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class VultureFeatherShedHandler {

    private static final int SHED_CHANCE_DENOMINATOR = 1000; // per-tick chance 1/N

    @SubscribeEvent
    public static void onVultureTick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity().getClass() != VultureEntity.class) return;

        VultureEntity vulture = (VultureEntity) event.getEntity();
        if (vulture.getRandom().nextInt(SHED_CHANCE_DENOMINATOR) != 0) return;

        ItemEntity feather = new ItemEntity(
                vulture.level(), vulture.getX(), vulture.getY(), vulture.getZ(),
                new ItemStack(ModItems.VULTURE_FEATHER.get()));
        vulture.level().addFreshEntity(feather);
    }
}
