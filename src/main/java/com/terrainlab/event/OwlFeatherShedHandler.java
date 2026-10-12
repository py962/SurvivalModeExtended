package com.terrainlab.event;


import com.terrainlab.TerrainLab;
import com.terrainlab.entity.OwlEntity;
import com.terrainlab.registry.ModItems;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

    /**
     * Owls randomly shed an Owl Feather.
     */
    @EventBusSubscriber(modid = TerrainLab.MODID)
    public class OwlFeatherShedHandler {

        // Per-tick 1-in-N chance.
        private static final int SHED_CHANCE_DENOMINATOR = 1000;

        @SubscribeEvent
        public static void onGooseTick(EntityTickEvent.Post event) {
            if (event.getEntity().level().isClientSide()) return;
            if (event.getEntity().getClass() != OwlEntity.class) return;

            OwlEntity owl = (OwlEntity) event.getEntity();
            if (owl.getRandom().nextInt(SHED_CHANCE_DENOMINATOR) != 0) return;

            ItemEntity feather = new ItemEntity(
                    owl.level(), owl.getX(), owl.getY(), owl.getZ(),
                    new ItemStack(ModItems.OWL_FEATHER.get()));
            owl.level().addFreshEntity(feather);
        }
    }


