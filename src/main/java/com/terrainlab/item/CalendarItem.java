package com.terrainlab.item;

import com.terrainlab.client.ClientSeasonState;
import com.terrainlab.data.SeasonSavedData;
import com.terrainlab.season.Season;
import com.terrainlab.season.SeasonManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Calendar dial item. The needle is driven by the season-angle item model; this class only adds the
 * current-season tooltip.
 */
public class CalendarItem extends Item {

    public CalendarItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay,
                                Consumer<Component> onAdd, TooltipFlag flag) {
        Level level = context.level();
        if (level == null) {
            return;
        }
        // ClientSeasonState has no client-only imports, so this is safe on a dedicated server.
        long offset;
        if (level.isClientSide()) {
            offset = ClientSeasonState.getSeasonalOffset();
        } else if (level instanceof ServerLevel serverLevel) {
            offset = SeasonSavedData.get(serverLevel).getSeasonalOffset();
        } else {
            offset = 0L;
        }
        Season season = SeasonManager.getCurrentSeason(level.getGameTime(), offset);
        onAdd.accept(Component.translatable("terrainlab.calendar.season." + season.name().toLowerCase()));
    }
}
