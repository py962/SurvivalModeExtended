package com.terrainlab.mixin;

import com.terrainlab.entity.HouseboatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.WaterlilyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Houseboats smash straight through lily pads: the pad has no collision for a houseboat, so it never
 * bumps or slows it (on the controlling client or the server), and vanilla's entityInside still breaks
 * the pad as the hull passes through it.
 */
@Mixin(WaterlilyBlock.class)
public abstract class HouseboatLilyPadMixin extends VegetationBlock {

    protected HouseboatLilyPadMixin(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof HouseboatEntity) {
            return Shapes.empty();
        }
        return super.getCollisionShape(state, level, pos, context);
    }
}
