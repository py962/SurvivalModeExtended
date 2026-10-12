package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModItems;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Tamed parrots occasionally shed a Parrot Feather. Uses an exact class check so Owls
 * (which extend Parrot and drop feathers on death instead) are excluded.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class ParrotFeatherShedHandler {

    // Per-tick chance 1/N: roughly one feather every 8 minutes per parrot.
    private static final int SHED_CHANCE_DENOMINATOR = 1000;

    @SubscribeEvent
    public static void onParrotTick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity().getClass() != Parrot.class) return;

        Parrot parrot = (Parrot) event.getEntity();
        if (!parrot.isTame()) return;
        if (parrot.getRandom().nextInt(SHED_CHANCE_DENOMINATOR) != 0) return;

        ItemEntity feather = new ItemEntity(
                parrot.level(), parrot.getX(), parrot.getY(), parrot.getZ(),
                new ItemStack(ModItems.PARROT_FEATHER.get()));
        parrot.level().addFreshEntity(feather);
    }
}
