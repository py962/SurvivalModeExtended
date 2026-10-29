package com.terrainlab.client;

import com.terrainlab.TerrainLab;
import com.terrainlab.forestfloor.SeasonOffsetSync;
import com.terrainlab.season.Season;
import com.terrainlab.season.SeasonManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import java.util.List;

/**
 * Pink petals turn crimson, like the cherry leaves, through early autumn, late autumn
 * and midwinter. The petal faces of the pink_petals models carry tintindex 2.
 */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public final class CrimsonPetals {
    private CrimsonPetals() {}

    /** Multiplier taking the vanilla pink petal texture to crimson. */
    private static final int CRIMSON = 0xFFBA263A;
    private static boolean lastCrimson;

    private static final BlockTintSource PETAL_TINT = new BlockTintSource() {
        @Override
        public int color(BlockState state) {
            return -1;
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            return isCrimson() ? CRIMSON : -1;
        }
    };

    static boolean isCrimson() {
        ClientLevel level = Minecraft.getInstance().level;
        long offset = SeasonOffsetSync.clientOffset;
        if (level == null || offset == Long.MIN_VALUE) return false;
        Season season = Season.fromYearTick(SeasonManager.yearRelativeTick(level.getGameTime(), offset));
        return season == Season.EARLY_AUTUMN || season == Season.LATE_AUTUMN || season == Season.MIDWINTER;
    }

    @SubscribeEvent
    public static void onBlockTints(RegisterColorHandlersEvent.BlockTintSources event) {
        // Indices 0 and 1 repeat vanilla's registration (blank, grass for the stems).
        event.register(List.of(BlockTintSources.constant(-1), BlockTintSources.grass(), PETAL_TINT), Blocks.PINK_PETALS);
    }

    /** Re-mesh the loaded area when the petals change colour, at the season boundary or on join. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.getGameTime() % 20 != 0) return;
        boolean crimson = isCrimson();
        if (crimson == lastCrimson) return;
        lastCrimson = crimson;
        BlockPos centre = mc.gameRenderer.mainCamera().blockPosition();
        int r = mc.options.getEffectiveRenderDistance() + 1;
        int cx = centre.getX() >> 4, cz = centre.getZ() >> 4;
        level.setSectionRangeDirty(cx - r, level.getMinSectionY(), cz - r, cx + r, level.getMaxSectionY(), cz + r);
    }
}
