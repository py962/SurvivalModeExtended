package com.terrainlab.block;

import com.terrainlab.TerrainLab;
import com.terrainlab.portal.PortalNetworkSavedData;
import com.terrainlab.portal.RedstonePortalShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Interior fill block of an activated Redstone Portal, placed and removed only by
 * {@link RedstonePortalShape}. Entities inside are sent to the nearest other active portal on the
 * same channel.
 * <p>
 * {@link #AXIS} is horizontal as for the Nether portal. {@link #CHANNEL} is the redstone signal
 * strength the portal was activated at, and determines which portals pair with it.
 */
public class RedstonePortalBlock extends Block implements Portal {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final IntegerProperty CHANNEL = IntegerProperty.create("channel", 1, 15);

    public RedstonePortalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(AXIS, Direction.Axis.X)
                .setValue(CHANNEL, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, CHANNEL);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Thin outline slab across the portal's axis.
        return state.getValue(AXIS) == Direction.Axis.X
                ? Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0)
                : Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                 InsideBlockEffectApplier effectApplier, boolean p_451762_) {
        // Implementing Portal alone does nothing; the entity's portal timer must be started here,
        // as NetherPortalBlock does. canUsePortal(false): only the vehicle triggers, not passengers.
        if (entity.canUsePortal(false)) {
            // TerrainLab.LOGGER.info(
                    // "[REDSTONE-PORTAL] {} touched portal block at {} - starting portal-transition timer",
                    // entity.getName().getString(), pos);
            entity.setAsInsidePortal(this, pos);
        }
    }

    @Override
    @Nullable
    public TeleportTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        String who = entity.getName().getString();

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RedstonePortalBlock)) {
            // TerrainLab.LOGGER.info(
                    // "[REDSTONE-PORTAL] {} at {}: block is {} (not RedstonePortalBlock) - returning null",
                    // who, pos, state.getBlock());
            return null;
        }
        int channel = state.getValue(CHANNEL);

        // Exclude this portal by its registered origin; the network stores one corner per portal,
        // so the entity's own cell would not match and the portal could pick itself.
        Optional<BlockPos> resolvedOrigin = RedstonePortalShape.originOf(level, pos);
        BlockPos originPos = resolvedOrigin.orElse(pos);
        // TerrainLab.LOGGER.info(
                // "[REDSTONE-PORTAL] {} at {} (channel={}): resolved own portal origin to {}{}",
                // who, pos, channel, originPos,
                // resolvedOrigin.isPresent() ? "" : " (originOf found nothing - fell back to raw pos, frame may be broken)");

        Optional<PortalNetworkSavedData.PortalEntry> nearest =
                PortalNetworkSavedData.get(level).findNearest(level.dimension(), originPos, channel);
        if (nearest.isEmpty()) {
            // TerrainLab.LOGGER.info(
                    // "[REDSTONE-PORTAL] {}: no other active portal registered on channel {} in any dimension - returning null",
                    // who, channel);
            return null;
        }

        PortalNetworkSavedData.PortalEntry destination = nearest.get();
        // TerrainLab.LOGGER.info(
                // "[REDSTONE-PORTAL] {}: nearest other portal on channel {} is dimension={} pos={}",
                // who, channel, destination.dimension(), destination.pos());

        ServerLevel targetLevel = level.getServer().getLevel(destination.dimension());
        if (targetLevel == null) {
            // TerrainLab.LOGGER.info(
                    // "[REDSTONE-PORTAL] {}: target dimension {} has no loaded ServerLevel - returning null",
                    // who, destination.dimension());
            return null;
        }

        BlockPos targetPos = destination.pos();
        // TerrainLab.LOGGER.info(
                // "[REDSTONE-PORTAL] {}: teleporting to dimension={} pos={}",
                // who, destination.dimension(), targetPos);
        return new TeleportTransition(
                targetLevel,
                new Vec3(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5),
                Vec3.ZERO,
                entity.getYRot(),
                entity.getXRot(),
                TeleportTransition.PLACE_PORTAL_TICKET.then(travelled -> {
                    if (travelled instanceof net.minecraft.server.level.ServerPlayer player) {
                        com.terrainlab.advancement.ModAdvancements.award(player, "redstone_portal");
                    }
                }) // PLACE_PORTAL_TICKET keeps the destination chunk loaded for the arrival
        );
    }

    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return 1; // ticks; near-instant, unlike the Nether portal
    }

    // TODO: override getLocalTransition() for a client-side transition effect.
}
