package com.terrainlab.client.renderer;

import com.terrainlab.entity.AbstractDispensingMinecart;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MinecartRenderer;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Vanilla minecart renderer for the dispenser / dropper minecarts. The display block is drawn in the
 * cart's rotated frame, so its FACING is turned back by the cart's render yaw: the front face then
 * points at the world direction the cart fires, whichever way the cart model is turned.
 */
public class DispensingMinecartRenderer extends MinecartRenderer {

    private final BlockModelResolver blockModelResolver;

    public DispensingMinecartRenderer(EntityRendererProvider.Context context, ModelLayerLocation model) {
        super(context, model);
        this.blockModelResolver = context.getBlockModelResolver();
    }

    @Override
    public void extractRenderState(AbstractMinecart entity, MinecartRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        BlockState display = entity.getDisplayBlockState();
        if (entity instanceof AbstractDispensingMinecart && display.getBlock() instanceof DispenserBlock) {
            Direction world = display.getValue(DispenserBlock.FACING);
            if (world.getAxis().isHorizontal()) {
                // Quarter turns the block model is rotated by (about +Y, counter-clockwise seen from
                // above); undo them so the model shows the world facing.
                int quarterTurns = Mth.floor(renderYaw(state) / 90.0F + 0.5F) & 3;
                Direction local = world;
                for (int i = 0; i < quarterTurns; i++) {
                    local = local.getClockWise();
                }
                this.blockModelResolver.update(state.displayBlockModel,
                        display.setValue(DispenserBlock.FACING, local), AbstractMinecartRenderer.BLOCK_DISPLAY_CONTEXT);
            }
        }
    }

    /**
     * Total yaw (degrees) applied to the display block by AbstractMinecartRenderer#submit: the cart
     * rotation (oldRender / newRender) followed by the fixed 90 degree turn of the contents.
     */
    private static float renderYaw(MinecartRenderState state) {
        if (state.isNewRender) {
            return state.yRot + 90.0F;
        }
        float rotation = state.yRot;
        if (state.posOnRail != null && state.frontPos != null && state.backPos != null) {
            Vec3 direction = state.backPos.subtract(state.frontPos);
            if (direction.length() != 0.0) {
                direction = direction.normalize();
                rotation = (float) (Math.atan2(direction.z, direction.x) * 180.0 / Math.PI);
            }
        }
        return 180.0F - rotation + 90.0F;
    }
}
