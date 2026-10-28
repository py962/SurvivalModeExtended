package com.terrainlab.entity;

import com.terrainlab.registry.ModEntities;
import com.terrainlab.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A 2x2 or 3x3 item frame that displays one item enlarged across its
 * whole footprint. 2x2 frames take any item; 3x3 frames only maps,
 * paintings and banners (see {@link #canDisplay}).
 *
 * Footprint convention (entity block pos = block in front of the clicked face):
 * <ul>
 *   <li>3x3: centred on the entity block.</li>
 *   <li>2x2: the entity block is the bottom-left corner as seen by the
 *       placer; the frame grows to their right and "up". On walls "up" is
 *       world up; on floors/ceilings it is away from the placer.</li>
 * </ul>
 * Floor/ceiling frames store the placer's horizontal facing
 * ({@link #getFlatFacing()}), which orients both the footprint and the map.
 * It is saved as "FlatFacing", synced as entity data and also packed into
 * the spawn packet so the client has the right bounding box immediately.
 */
public class OversizedMapFrameEntity extends ItemFrame {

    private static final EntityDataAccessor<Direction> DATA_FLAT_FACING =
            SynchedEntityData.defineId(
                    OversizedMapFrameEntity.class,
                    EntityDataSerializers.DIRECTION
            );

    private static final Direction DEFAULT_FLAT_FACING = Direction.NORTH;
    private static final String TAG_FLAT_FACING = "FlatFacing";
    private static final String TAG_FIXED = "Fixed";

    /**
     * Mirror of vanilla's private ItemFrame#fixed. The only way vanilla sets
     * it is the "Fixed" save tag (readAdditionalSaveData, which also covers
     * /summon, /data and item ENTITY_DATA), so re-reading it keeps both equal.
     */
    private boolean fixedFrame;

    public OversizedMapFrameEntity(
            EntityType<? extends ItemFrame> type,
            Level level
    ) {
        super(type, level);
    }

    public OversizedMapFrameEntity(
            EntityType<? extends ItemFrame> type,
            Level level,
            BlockPos pos,
            Direction direction
    ) {
        super(type, level, pos, direction);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_FLAT_FACING, DEFAULT_FLAT_FACING);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);

        if (accessor.equals(DATA_FLAT_FACING) && getPos() != null) {
            recalculateBoundingBox();
        }
    }

    // ------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------

    public int getFrameSize() {
        return getType() == ModEntities.EXTRA_LARGE_MAP_FRAME.get() ? 3 : 2;
    }

    /**
     * Horizontal facing of the player who placed a floor/ceiling frame.
     * Unused for wall frames.
     */
    public Direction getFlatFacing() {
        return getEntityData().get(DATA_FLAT_FACING);
    }

    public void setFlatFacing(Direction flatFacing) {
        getEntityData().set(
                DATA_FLAT_FACING,
                flatFacing.getAxis().isHorizontal()
                        ? flatFacing
                        : DEFAULT_FLAT_FACING
        );
    }

    public boolean isFixed() {
        return fixedFrame;
    }

    /**
     * In-plane axis along which the footprint extends to the placer's right.
     */
    private Direction rightAxis(Direction facing) {
        return facing.getAxis().isHorizontal()
                ? facing.getCounterClockWise()
                : getFlatFacing().getClockWise();
    }

    /**
     * In-plane "up" axis: world up on walls, away from the placer on
     * floors and ceilings.
     */
    private Direction upAxis(Direction facing) {
        return facing.getAxis().isHorizontal()
                ? Direction.UP
                : getFlatFacing();
    }

    public int getFootprintStart() {
        return getFrameSize() == 3 ? -1 : 0;
    }

    private List<BlockPos> footprint(BlockPos origin, Direction facing) {
        Direction right = rightAxis(facing);
        Direction up = upAxis(facing);
        int size = getFrameSize();
        int start = getFootprintStart();
        List<BlockPos> blocks = new ArrayList<>(size * size);

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                blocks.add(origin
                        .relative(right, start + i)
                        .relative(up, start + j));
            }
        }

        return blocks;
    }

    /**
     * Every block (in the entity's layer, in front of the support) covered
     * by the frame.
     */
    public List<BlockPos> getFootprint() {
        return footprint(getPos(), getDirection());
    }

    /**
     * Bounding box covering the whole footprint, 1/16 thick against the
     * supporting surface. Used for interaction, hit detection, culling,
     * overlap and survival checks. The entity position becomes its centre.
     */
    @Override
    protected AABB calculateBoundingBox(BlockPos blockPos, Direction direction) {
        Direction right = rightAxis(direction);
        Direction up = upAxis(direction);
        int start = getFootprintStart();
        int end = start + getFrameSize() - 1;

        Vec3 first = Vec3.atCenterOf(
                blockPos.relative(right, start).relative(up, start)
        ).relative(direction, -0.46875);
        Vec3 last = Vec3.atCenterOf(
                blockPos.relative(right, end).relative(up, end)
        ).relative(direction, -0.46875);

        Direction.Axis normal = direction.getAxis();

        return new AABB(first, last).inflate(
                normal == Direction.Axis.X ? 0.03125 : 0.5,
                normal == Direction.Axis.Y ? 0.03125 : 0.5,
                normal == Direction.Axis.Z ? 0.03125 : 0.5
        );
    }

    @Override
    protected AABB getPopBox() {
        return calculateBoundingBox(getPos(), getDirection());
    }

    /**
     * The entity centre is not inside its anchor block for 2x2 frames,
     * so sync the anchor block position (as vanilla paintings do).
     */
    @Override
    public Vec3 trackingPosition() {
        return Vec3.atLowerCornerOf(getPos());
    }

    /**
     * Keep the client's packet position base on the anchor corner, the
     * same position the server tracks and sends ({@link #trackingPosition}).
     *
     * ItemFrame#recalculateBoundingBox (final) re-bases the codec on the
     * entity centre. Move packets carry deltas against the server base
     * (the anchor corner), so the client decoded them against the centre
     * instead and passed the centre to BlockAttachedEntity#setPos, which
     * takes BlockPos.containing(centre) as the new anchor. For a 3x3 frame
     * the centre lies inside the anchor block, but for a 2x2 frame it sits
     * exactly on the block boundary (+0.5 right, +0.5 up from the anchor
     * centre), so the client anchor jumped one block up (and one block
     * right on south/west walls, sideways on floors/ceilings). The server
     * sends such a packet as soon as the frame is tracked (needsSync is
     * set, tickCount 0 % 60 == 0) and with later data updates.
     */
    @Override
    public void syncPacketPositionCodec(double x, double y, double z) {
        BlockPos anchor = getPos();

        if (anchor != null) {
            super.syncPacketPositionCodec(
                    anchor.getX(),
                    anchor.getY(),
                    anchor.getZ()
            );
        } else {
            super.syncPacketPositionCodec(x, y, z);
        }
    }

    /**
     * Vanilla ItemFrame packs only the facing into the spawn packet. Also
     * pack the flat facing (data % 6 stays the 3D facing, so ItemFrame's
     * own decoding is unaffected).
     */
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(
            ServerEntity serverEntity
    ) {
        int data = getDirection().get3DDataValue()
                + 6 * getFlatFacing().get2DDataValue();
        return new ClientboundAddEntityPacket(this, data, getPos());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        // Set first so ItemFrame#setDirection computes the right box.
        setFlatFacing(Direction.from2DDataValue(packet.getData() / 6));
        super.recreateFromPacket(packet);
    }

    @Override
    public float rotate(Rotation rotation) {
        if (getDirection().getAxis().isVertical()) {
            setFlatFacing(rotation.rotate(getFlatFacing()));
        }

        return super.rotate(rotation);
    }

    // ------------------------------------------------------------------
    // Survival
    // ------------------------------------------------------------------

    /**
     * Fixed frames always survive (as vanilla). Otherwise every block
     * behind the footprint (wall, floor below or ceiling above) must have a
     * sturdy face towards the frame, the footprint must be clear, and no
     * other hanging entity facing the same way may overlap it.
     */
    @Override
    public boolean survives() {
        if (fixedFrame) {
            return true;
        }

        Direction facing = getDirection();

        for (BlockPos block : getFootprint()) {
            BlockPos support = block.relative(facing.getOpposite());

            if (!level().getBlockState(support)
                    .isFaceSturdy(level(), support, facing)) {
                return false;
            }
        }

        AABB footprint = getPopBox();

        if (hasLevelCollision(footprint)) {
            return false;
        }

        // Like vanilla ItemFrame#canCoexist(true), only hanging entities on
        // the same surface orientation block us; this lets a floor frame sit
        // against a wall frame whose box touches the floor at the corner.
        return !level().hasEntities(
                EntityTypeTest.forClass(HangingEntity.class),
                footprint,
                other -> other != this && other.getDirection() == facing
        );
    }

    // ------------------------------------------------------------------
    // Save data
    // ------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putByte(
                TAG_FLAT_FACING,
                (byte) getFlatFacing().get2DDataValue()
        );
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        fixedFrame = input.getBooleanOr(TAG_FIXED, false);
        setFlatFacing(Direction.from2DDataValue(input.getByteOr(
                TAG_FLAT_FACING,
                (byte) DEFAULT_FLAT_FACING.get2DDataValue()
        )));
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    /**
     * Spawn protection / world border across the whole footprint, not just
     * the anchor block.
     */
    public boolean mayInteractWithFootprint(Player player) {
        for (BlockPos block : getFootprint()) {
            if (!level().mayInteract(player, block)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean skipAttackInteraction(Entity source) {
        if (source instanceof Player player
                && !mayInteractWithFootprint(player)) {
            return true;
        }

        return super.skipAttackInteraction(source);
    }

    /**
     * True for frames that cannot be modified by players (e.g. "Fixed"
     * or invulnerable frames). hurtClient has no side effects.
     */
    private boolean isLocked() {
        return fixedFrame || !hurtClient(damageSources().generic());
    }

    /**
     * Map-like items: every MapItem (filled maps, all explorer / treasure
     * maps, trail trader charts and relic maps, terrainlab:atlas) and any
     * stack carrying a map id.
     */
    private static boolean isMapLike(ItemStack stack) {
        return stack.getItem() instanceof MapItem
                || stack.has(DataComponents.MAP_ID);
    }

    /**
     * 2x2 frames accept any item, like a vanilla frame. 3x3 frames only
     * take pictorial items: maps (see {@link #isMapLike}), paintings and
     * banners.
     */
    public boolean canDisplay(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        if (getFrameSize() == 2) {
            return true;
        }

        return isMapLike(stack)
                || stack.is(Items.PAINTING)
                || stack.is(ItemTags.BANNERS);
    }

    @Override
    public InteractionResult interact(
            Player player,
            InteractionHand hand,
            Vec3 location
    ) {
        if (fixedFrame) {
            return InteractionResult.PASS;
        }

        ItemStack held = player.getItemInHand(hand);

        if (!getItem().isEmpty()) {
            // Sneaking with an empty hand removes the displayed item,
            // exactly as punching a vanilla frame does (drops it, or
            // just clears it in creative).
            if (player.isShiftKeyDown() && held.isEmpty()) {
                if (isLocked()) {
                    return InteractionResult.PASS;
                }

                if (level() instanceof ServerLevel serverLevel
                        && mayInteractWithFootprint(player)) {
                    hurtServer(
                            serverLevel,
                            damageSources().playerAttack(player),
                            0.0F
                    );
                }

                return InteractionResult.SUCCESS;
            }

            // Otherwise rotate: maps by 90 degrees per click (as the
            // renderer draws maps at rotation / 2 quarter turns), other
            // items by 45 degrees like vanilla.
            if (!level().isClientSide()
                    && !isRemoved()
                    && mayInteractWithFootprint(player)) {
                playSound(getRotateItemSound(), 1.0F, 1.0F);
                int rotation = getFramedMapId(getItem()) != null
                        ? (getRotation() / 2 + 1) * 2
                        : getRotation() + 1;
                setRotation(rotation % NUM_ROTATIONS);
                updateFootprintOutputSignal();
                gameEvent(GameEvent.BLOCK_CHANGE, player);
            }

            return InteractionResult.SUCCESS;
        }

        // Refused items PASS so the other hand can try.
        if (!canDisplay(held)) {
            return InteractionResult.PASS;
        }

        if (!level().isClientSide() && !mayInteractWithFootprint(player)) {
            return InteractionResult.FAIL;
        }

        // Vanilla handles insertion, map data and item-frame persistence.
        return super.interact(player, hand, location);
    }

    @Override
    public void setItem(ItemStack itemStack, boolean updateNeighbours) {
        super.setItem(itemStack, updateNeighbours);

        if (updateNeighbours) {
            updateFootprintOutputSignal();
        }
    }

    /**
     * Vanilla only notifies comparators around the anchor block; notify
     * around every footprint block so a comparator behind any of them
     * (comparators find frames anywhere on the bounding box) updates.
     */
    private void updateFootprintOutputSignal() {
        BlockPos anchor = getPos();

        if (anchor == null) {
            return;
        }

        for (BlockPos block : getFootprint()) {
            if (!block.equals(anchor)) {
                level().updateNeighbourForOutputSignal(block, Blocks.AIR);
            }
        }
    }

    @Override
    protected ItemStack getFrameItemStack() {
        if (getFrameSize() == 3) {
            return new ItemStack(ModItems.EXTRA_LARGE_ITEM_FRAME.get());
        }

        return new ItemStack(ModItems.LARGE_ITEM_FRAME.get());
    }
}
