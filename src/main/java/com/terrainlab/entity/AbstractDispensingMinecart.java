package com.terrainlab.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Shared base of the dispenser and dropper minecarts: a 9-slot container minecart (vanilla
 * dispenser GUI, hopper/comparator interaction from AbstractMinecartContainer) that fires once each
 * time it passes onto a powered activator rail.
 *
 * <p>The firing direction is stored relative to the cart's travel ({@link RelativeFacing}) and
 * resolved against its heading: the direction of horizontal motion, or the last one while the cart
 * stands still. Both are synced, so the displayed dispenser/dropper block can face where the cart
 * shoots.
 */
public abstract class AbstractDispensingMinecart extends AbstractMinecartContainer {

    /** Firing direction relative to the cart's travel. */
    public enum RelativeFacing {
        FRONT, BACK, LEFT, RIGHT, UP, DOWN;

        public Direction toWorld(Direction heading) {
            return switch (this) {
                case FRONT -> heading;
                case BACK -> heading.getOpposite();
                case LEFT -> heading.getCounterClockWise();
                case RIGHT -> heading.getClockWise();
                case UP -> Direction.UP;
                case DOWN -> Direction.DOWN;
            };
        }

        RelativeFacing flipHorizontal() {
            return switch (this) {
                case FRONT -> BACK;
                case BACK -> FRONT;
                case LEFT -> RIGHT;
                case RIGHT -> LEFT;
                default -> this;
            };
        }

        static RelativeFacing byName(String name, RelativeFacing fallback) {
            for (RelativeFacing facing : values()) {
                if (facing.name().equalsIgnoreCase(name)) return facing;
            }
            return fallback;
        }
    }

    /** Pitch (degrees) beyond which placing the cart aims it straight down / up. */
    private static final float STEEP_PITCH = 55.0F;
    /** Squared horizontal speed above which the cart's motion sets its heading. */
    private static final double MIN_HEADING_SPEED_SQR = 1.0E-4;

    private static final EntityDataAccessor<Byte> DATA_FACING =
            SynchedEntityData.defineId(AbstractDispensingMinecart.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Direction> DATA_HEADING =
            SynchedEntityData.defineId(AbstractDispensingMinecart.class, EntityDataSerializers.DIRECTION);

    // Activator-rail edge detection (server only): vanilla calls activateMinecart every tick the cart
    // is on an activator rail; the cart fires when power rises or it enters a new powered rail block.
    private boolean activatedThisTick;
    private boolean lastActivatorPowered;
    private @Nullable BlockPos lastActivatorPos;

    protected AbstractDispensingMinecart(EntityType<?> type, Level level) {
        super(type, level);
    }

    /** Dispenser or dropper; its FACING-rotated state is the displayed block. */
    protected abstract Block displayBlock();

    /** Fires one item (or fails) as the matching block would, from {@code pos} towards {@code direction}. */
    protected abstract void fire(ServerLevel level, BlockPos pos, Direction direction);

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        // Default DOWN: what a dispenser-placed cart (no player to aim it) uses.
        entityData.define(DATA_FACING, (byte) RelativeFacing.DOWN.ordinal());
        entityData.define(DATA_HEADING, Direction.NORTH);
    }

    public RelativeFacing getRelativeFacing() {
        int ordinal = this.entityData.get(DATA_FACING);
        RelativeFacing[] values = RelativeFacing.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : RelativeFacing.DOWN;
    }

    public void setRelativeFacing(RelativeFacing facing) {
        this.entityData.set(DATA_FACING, (byte) facing.ordinal());
    }

    public Direction getHeading() {
        return this.entityData.get(DATA_HEADING);
    }

    private void setHeading(Direction heading) {
        if (heading.getAxis().isHorizontal() && heading != this.getHeading()) {
            this.entityData.set(DATA_HEADING, heading);
        }
    }

    /** The world direction the cart currently fires (and its displayed block faces). */
    public Direction getFiringDirection() {
        return this.getRelativeFacing().toWorld(this.getHeading());
    }

    /**
     * Aims a cart a player just placed on a rail of {@code shape}. Its heading is the rail direction
     * the player looks most along (any horizontal direction on a curve); a steep look down/up aims it
     * DOWN/UP, otherwise the look direction relative to that heading (FRONT when looking along the
     * rail, LEFT/RIGHT across it). Sneaking reverses a horizontal aim (FRONT to BACK, LEFT to RIGHT).
     */
    public void aimFromPlacement(Player player, RailShape shape) {
        Vec3 look = player.getLookAngle();
        Direction heading = switch (shape) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> look.z >= 0 ? Direction.SOUTH : Direction.NORTH;
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> look.x >= 0 ? Direction.EAST : Direction.WEST;
            default -> player.getDirection();
        };
        this.setHeading(heading);

        float pitch = player.getXRot();
        RelativeFacing facing;
        if (pitch >= STEEP_PITCH) {
            facing = RelativeFacing.DOWN;
        } else if (pitch <= -STEEP_PITCH) {
            facing = RelativeFacing.UP;
        } else {
            Direction lookDir = player.getDirection();
            if (lookDir == heading) facing = RelativeFacing.FRONT;
            else if (lookDir == heading.getOpposite()) facing = RelativeFacing.BACK;
            else if (lookDir == heading.getClockWise()) facing = RelativeFacing.RIGHT;
            else facing = RelativeFacing.LEFT;
            if (player.isShiftKeyDown()) facing = facing.flipHorizontal();
        }
        this.setRelativeFacing(facing);
    }

    private void updateHeadingFromMotion() {
        Vec3 motion = this.getDeltaMovement();
        if (motion.horizontalDistanceSqr() > MIN_HEADING_SPEED_SQR) {
            this.setHeading(Direction.getApproximateNearest(motion.x, 0.0, motion.z));
        }
    }

    @Override
    public void tick() {
        this.activatedThisTick = false;
        super.tick();
        if (!this.level().isClientSide()) {
            if (!this.activatedThisTick) {
                this.lastActivatorPowered = false;
                this.lastActivatorPos = null;
            }
            this.updateHeadingFromMotion();
        }
    }

    @Override
    public void activateMinecart(ServerLevel level, int xt, int yt, int zt, boolean powered) {
        this.activatedThisTick = true;
        BlockPos railPos = new BlockPos(xt, yt, zt);
        boolean newRail = !railPos.equals(this.lastActivatorPos);
        boolean fire = powered && (!this.lastActivatorPowered || newRail);
        this.lastActivatorPowered = powered;
        this.lastActivatorPos = railPos;
        if (fire) {
            this.updateHeadingFromMotion();
            this.fire(level, this.blockPosition(), this.getFiringDirection());
        }
    }

    /** A random non-empty slot, like DispenserBlockEntity#getRandomSlot; -1 when empty. */
    public int getRandomSlot(RandomSource random) {
        int replaceSlot = -1;
        int replaceOdds = 1;
        for (int i = 0; i < this.getContainerSize(); i++) {
            if (!this.getItem(i).isEmpty() && random.nextInt(replaceOdds++) == 0) {
                replaceSlot = i;
            }
        }
        return replaceSlot;
    }

    /** Merges {@code stack} into the inventory like DispenserBlockEntity#insertItem; returns the rest. */
    public ItemStack insertItem(ItemStack stack) {
        int maxStackSize = this.getMaxStackSize(stack);
        for (int i = 0; i < this.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack target = this.getItem(i);
            if (target.isEmpty() || ItemStack.isSameItemSameComponents(stack, target)) {
                int transfer = Math.min(stack.getCount(), maxStackSize - target.getCount());
                if (transfer > 0) {
                    if (target.isEmpty()) {
                        this.setItem(i, stack.split(transfer));
                    } else {
                        stack.shrink(transfer);
                        target.grow(transfer);
                    }
                }
            }
        }
        return stack;
    }

    @Override
    public BlockState getDefaultDisplayBlockState() {
        return this.displayBlock().defaultBlockState().setValue(DispenserBlock.FACING, this.getFiringDirection());
    }

    @Override
    public int getContainerSize() {
        return 9;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new DispenserMenu(containerId, inventory, this);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Facing", this.getRelativeFacing().name().toLowerCase(java.util.Locale.ROOT));
        output.putInt("Heading", this.getHeading().get2DDataValue());
        output.putBoolean("ActivatorPowered", this.lastActivatorPowered);
        if (this.lastActivatorPos != null) {
            output.putLong("ActivatorPos", this.lastActivatorPos.asLong());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setRelativeFacing(RelativeFacing.byName(input.getStringOr("Facing", ""), RelativeFacing.DOWN));
        this.setHeading(Direction.from2DDataValue(input.getIntOr("Heading", Direction.NORTH.get2DDataValue())));
        this.lastActivatorPowered = input.getBooleanOr("ActivatorPowered", false);
        this.lastActivatorPos = input.getLong("ActivatorPos").map(BlockPos::of).orElse(null);
    }
}
