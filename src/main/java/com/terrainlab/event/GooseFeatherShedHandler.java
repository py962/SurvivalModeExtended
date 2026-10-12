package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.entity.GooseEntity;
import com.terrainlab.registry.ModItems;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Geese randomly shed a Goose Feather. Geese are never tamed, so every goose sheds regardless of state.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class GooseFeatherShedHandler {

    // Per-tick 1-in-N chance.
    private static final int SHED_CHANCE_DENOMINATOR = 1000;

    @SubscribeEvent
    public static void onGooseTick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity().getClass() != GooseEntity.class) return;

        GooseEntity goose = (GooseEntity) event.getEntity();
        if (goose.getRandom().nextInt(SHED_CHANCE_DENOMINATOR) != 0) return;

        ItemEntity feather = new ItemEntity(
                goose.level(), goose.getX(), goose.getY(), goose.getZ(),
                new ItemStack(ModItems.GOOSE_FEATHER.get()));
        goose.level().addFreshEntity(feather);
    }
}
