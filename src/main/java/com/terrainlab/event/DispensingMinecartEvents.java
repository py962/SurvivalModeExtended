package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModEntities;
import com.terrainlab.registry.ModItems;
import net.minecraft.core.dispenser.MinecartDispenseItemBehavior;
import net.minecraft.world.level.block.DispenserBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

/**
 * Common hookups for the dispenser and dropper minecarts: dispensers place them on rails like
 * vanilla minecart items (aimed DOWN, the cart default), and they expose their inventory as an
 * item capability like NeoForge does for vanilla's chest and hopper minecarts.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class DispensingMinecartEvents {

    private DispensingMinecartEvents() {
    }

    // Mod bus.
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DispenserBlock.registerBehavior(ModItems.DISPENSER_MINECART.get(),
                    new MinecartDispenseItemBehavior(ModEntities.DISPENSER_MINECART.get()));
            DispenserBlock.registerBehavior(ModItems.DROPPER_MINECART.get(),
                    new MinecartDispenseItemBehavior(ModEntities.DROPPER_MINECART.get()));
        });
    }

    // Mod bus.
    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerEntity(Capabilities.Item.ENTITY, ModEntities.DISPENSER_MINECART.get(),
                (cart, side) -> VanillaContainerWrapper.of(cart));
        event.registerEntity(Capabilities.Item.ENTITY_AUTOMATION, ModEntities.DISPENSER_MINECART.get(),
                (cart, side) -> VanillaContainerWrapper.of(cart));
        event.registerEntity(Capabilities.Item.ENTITY, ModEntities.DROPPER_MINECART.get(),
                (cart, side) -> VanillaContainerWrapper.of(cart));
        event.registerEntity(Capabilities.Item.ENTITY_AUTOMATION, ModEntities.DROPPER_MINECART.get(),
                (cart, side) -> VanillaContainerWrapper.of(cart));
    }
}
