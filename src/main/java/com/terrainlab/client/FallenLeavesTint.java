package com.terrainlab.client;

import com.terrainlab.TerrainLab;
import com.terrainlab.forestfloor.FallenLeavesBlock;
import com.terrainlab.forestfloor.ForestFloor;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import java.util.List;
import java.util.Set;

/** Tints fallen leaves by the tree they came from. */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public final class FallenLeavesTint {
    private FallenLeavesTint() {}

    private static final BlockTintSource TREE_TINT = new BlockTintSource() {
        @Override
        public int color(BlockState state) {
            return state.getValue(FallenLeavesBlock.TREE).color;
        }

        @Override
        public Set<Property<?>> relevantProperties() {
            return Set.of(FallenLeavesBlock.TREE);
        }
    };

    @SubscribeEvent
    public static void onBlockTints(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(List.of(TREE_TINT), ForestFloor.FALLEN_LEAVES.get());
    }
}
