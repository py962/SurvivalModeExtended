package com.terrainlab.item;

import com.terrainlab.client.ThermometerTooltipClient;
import com.terrainlab.temperature.ThermometerBand;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Thermometer item, held or frame-mounted. Its texture is driven by the {@code thermometer_reading}
 * model property; this class only adds the tooltip.
 */
public class ThermometerItem extends Item {

    public ThermometerItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay,
                                Consumer<Component> onAdd, TooltipFlag flag) {
        // Tooltips are built on the client; the client-only helper is never loaded on a dedicated server.
        Level level = context.level();
        if (level == null || !level.isClientSide()) {
            return;
        }
        ThermometerBand band = ThermometerTooltipClient.currentBand();
        if (band == null) {
            return;
        }
        onAdd.accept(Component.translatable("terrainlab.thermometer.band."
                + band.name().toLowerCase()));
    }
}
