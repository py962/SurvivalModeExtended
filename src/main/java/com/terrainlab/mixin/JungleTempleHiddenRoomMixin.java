package com.terrainlab.mixin;

import com.terrainlab.entity.OversizedMapFrameEntity;
import com.terrainlab.event.JungleTempleMapHandler;
import com.terrainlab.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.PistonType;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.structures.JungleTemplePiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a redstone dial lock and a hidden Ancient City map room to every jungle temple, built in the
 * same chunk-by-chunk pass as the temple.
 * <p>
 * Local coordinates: the temple is x 0-11, z 0-14; its lower floor stands at y -3 (floor blocks at
 * y -4). In local block states NORTH = +z, SOUTH = -z, EAST = +x, WEST = -x.
 *
 * <h2>Dial (east end of the lower north room, past both arrow traps)</h2>
 * An arrow in an item frame at (9,-2,2) on the east wall, beside vanilla's chest-guarding dispenser.
 * The chiselled stone brick below its wall block, at (10,-3,2), is the clue: point the arrow down
 * (three turns). The east wall is two blocks thick here, so the comparator fits inside it; the rest
 * of the circuit is in a sealed mossy cobblestone strip outside the east wall (local x 12-16), which
 * is why the temple's bounding box is widened by 5 on that side.
 * <ul>
 * <li>Comparator (11,-2,2) reads the frame (1-8) into a dust line (12..15,-2,2) then (15,-2,3). Dust
 *     loses one strength per block, so dust 4 is lit from 3 turns and dust 5 from 4 turns.</li>
 * <li>Dust 4 powers block (15,-3,2), switching off the wall torch on it at (15,-3,1). Dust 5 powers
 *     block (15,-3,3). The torch, and a repeater off the dust-5 block, both feed one "keep closed"
 *     dust network at y -5, which powers the blocks under two sticky pistons (11,-4,2) and
 *     (11,-4,3). Their heads hold the floor blocks (9,-4,2) and (9,-4,3) in place.</li>
 * <li>A second repeater off the dust-5 block feeds a dust line under the floor to block (7,-5,7),
 *     the base of a two-torch tower in the wall beside the temple's staircase: torch (7,-4,7) goes
 *     out, so torch (7,-2,7) lights and powers (7,-1,7), firing the lava dispenser (7,0,7) at the
 *     top of the stairs. Its top is hidden by moss carpet.</li>
 * <li>So the pistons only retract at exactly three turns, pulling both floor blocks into the wall.
 *     Four or more turns close the floor and send lava down the temple's stairs from the ground
 *     floor. The dispenser behaves like vanilla's: overshooting again scoops its lava back up.</li>
 * <li>Glass at (15,-2,1), (15,-4,2), (12,-3,2), (15,-3,6), (15,-4,7), (13,-3,4) and (13,-4,5) keeps
 *     the torch from feeding the dial line, lets dust lines step down, and keeps the dial line from
 *     quasi-powering the pistons.</li>
 * </ul>
 *
 * <h2>Room</h2>
 * The first floor block opens onto a one-block step, the second onto a ladder shaft down to a
 * short corridor and a 5x5x5 room lit by soul torches, interior x 5-9, z 8-12, y -12 to -8. Shells, outside in: mossy
 * cobblestone, reinforced deepslate (no tunnelling in), mossy cobblestone lining. The only visible
 * reinforced deepslate is a border around the extra large (3x3) map frame on the east wall, which
 * {@link JungleTempleMapHandler} fills with the Temple Map once the room loads.
 */
@Mixin(JungleTemplePiece.class)
public abstract class JungleTempleHiddenRoomMixin extends ScatteredFeaturePiece {

    /** Extra width on the local +x side for the circuit strip. That side is never the coordinate origin. */
    @Unique
    private static final int TERRAINLAB$STRIP_WIDTH = 5;

    protected JungleTempleHiddenRoomMixin(StructurePieceType type, CompoundTag tag) {
        super(type, tag);
    }

    @Inject(method = "<init>(Lnet/minecraft/util/RandomSource;II)V", at = @At("RETURN"))
    private void terrainlab$widenForCircuit(RandomSource random, int west, int north, CallbackInfo ci) {
        BoundingBox b = this.boundingBox;
        Direction orientation = this.getOrientation();
        if (orientation == null) return;
        // Local x runs along world x for north/south temples and along world z for east/west ones,
        // starting from minX / minZ in both cases, so growing the max side never moves the temple.
        if (orientation.getAxis() == Direction.Axis.Z) {
            this.boundingBox = new BoundingBox(b.minX(), b.minY(), b.minZ(), b.maxX() + TERRAINLAB$STRIP_WIDTH, b.maxY(), b.maxZ());
        } else {
            this.boundingBox = new BoundingBox(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ() + TERRAINLAB$STRIP_WIDTH);
        }
    }

    @Inject(method = "postProcess", at = @At("TAIL"))
    private void terrainlab$addHiddenRoom(WorldGenLevel level, StructureManager structureManager,
            ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos,
            BlockPos referencePos, CallbackInfo ci) {
        if (this.heightPosition < 0) return;

        BlockState mossy = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        BlockState reinforced = Blocks.REINFORCED_DEEPSLATE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();

        // ---- Shells. Outside in: mossy cobblestone, reinforced deepslate (so nobody tunnels in),
        // then a mossy cobblestone lining, so the reinforced layer stays hidden.
        terrainlab$box(level, chunkBB, mossy, 2, -15, 0, 12, -5, 14);
        terrainlab$box(level, chunkBB, reinforced, 3, -14, 0, 11, -6, 14);
        terrainlab$box(level, chunkBB, mossy, 4, -13, 7, 10, -7, 13);   // room lining
        terrainlab$box(level, chunkBB, mossy, 8, -13, 1, 10, -5, 7);    // stairwell lining
        terrainlab$box(level, chunkBB, mossy, 12, -6, 0, 16, 0, 8);     // circuit strip

        // ---- Way down: the two floor blocks (9,-4,2) and (9,-4,3) open. The first is a one-block step
        // down onto (9,-5,2); the second is the top of a ladder shaft (9,-12..-5,3), then a short
        // corridor south into the room. Climbing out: up the ladder, onto the step, one jump.
        BlockState ladder = Blocks.LADDER.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
        for (int y = -12; y <= -5; y++) {
            this.placeBlock(level, ladder, 9, y, 3, chunkBB);
        }
        terrainlab$box(level, chunkBB, air, 9, -12, 4, 9, -10, 7);

        // ---- Room: interior x 5-9, z 8-12, y -12 to -8.
        terrainlab$box(level, chunkBB, air, 5, -12, 8, 9, -8, 12);
        // Reinforced deepslate border around the map wall (east wall, map over z 9-11, y -11 to -9).
        for (int y = -12; y <= -8; y++) {
            this.placeBlock(level, reinforced, 10, y, 8, chunkBB);
            this.placeBlock(level, reinforced, 10, y, 12, chunkBB);
        }
        for (int z = 9; z <= 11; z++) {
            this.placeBlock(level, reinforced, 10, -12, z, chunkBB);
            this.placeBlock(level, reinforced, 10, -8, z, chunkBB);
        }
        BlockState pillar = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        for (int y = -12; y <= -8; y++) {
            this.placeBlock(level, pillar, 5, y, 8, chunkBB);
            this.placeBlock(level, pillar, 5, y, 12, chunkBB);
        }
        this.placeBlock(level, Blocks.CHISELED_DEEPSLATE.defaultBlockState(), 8, -12, 10, chunkBB);
        BlockState candle = Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, true);
        this.placeBlock(level, candle, 8, -12, 9, chunkBB);
        this.placeBlock(level, candle, 8, -12, 11, chunkBB);
        BlockState torch = Blocks.SOUL_WALL_TORCH.defaultBlockState();
        this.placeBlock(level, torch.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), 5, -10, 9, chunkBB);
        this.placeBlock(level, torch.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), 5, -10, 11, chunkBB);
        this.placeBlock(level, torch.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), 7, -10, 8, chunkBB);
        this.placeBlock(level, torch.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), 7, -10, 12, chunkBB);

        // ---- Dial corner of the north room.
        this.placeBlock(level, air, 9, -3, 2, chunkBB);                       // vanilla decor block, now the standing spot
        this.placeBlock(level, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 10, -3, 2, chunkBB); // clue
        // Two floor blocks, each held by a sticky piston under the east wall.
        for (int z = 2; z <= 3; z++) {
            this.placeBlock(level, mossy, 9, -4, z, chunkBB);
            this.placeBlock(level, Blocks.STICKY_PISTON.defaultBlockState()
                    .setValue(BlockStateProperties.FACING, Direction.WEST)
                    .setValue(BlockStateProperties.EXTENDED, true), 11, -4, z, chunkBB);
            this.placeBlock(level, Blocks.PISTON_HEAD.defaultBlockState()
                    .setValue(BlockStateProperties.FACING, Direction.WEST)
                    .setValue(BlockStateProperties.PISTON_TYPE, PistonType.STICKY)
                    .setValue(BlockStateProperties.SHORT, false), 10, -4, z, chunkBB);
        }
        this.placeBlock(level, Blocks.COMPARATOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST), 11, -2, 2, chunkBB);

        // ---- Circuit strip.
        this.placeBlock(level, glass, 15, -2, 1, chunkBB);
        this.placeBlock(level, glass, 15, -4, 2, chunkBB);
        this.placeBlock(level, glass, 12, -3, 2, chunkBB);
        this.placeBlock(level, glass, 15, -3, 6, chunkBB);
        this.placeBlock(level, glass, 15, -4, 7, chunkBB);
        this.placeBlock(level, glass, 13, -3, 4, chunkBB);
        this.placeBlock(level, glass, 13, -4, 5, chunkBB);

        // Dial line: comparator -> 5 dust.
        terrainlab$wire(level, chunkBB, 12, -2, 2, 0, null, null, RedstoneSide.SIDE, RedstoneSide.SIDE);
        terrainlab$wire(level, chunkBB, 13, -2, 2, 0, null, null, RedstoneSide.SIDE, RedstoneSide.SIDE);
        terrainlab$wire(level, chunkBB, 14, -2, 2, 0, null, null, RedstoneSide.SIDE, RedstoneSide.SIDE);
        terrainlab$wire(level, chunkBB, 15, -2, 2, 0, RedstoneSide.SIDE, null, null, RedstoneSide.SIDE);
        terrainlab$wire(level, chunkBB, 15, -2, 3, 0, RedstoneSide.SIDE, RedstoneSide.SIDE, null, null);

        // "Keep closed" network at y -5: fed by the torch on the dust-4 block (fewer than 3 turns) and
        // by a repeater off the dust-5 block (4 or more turns). It powers the blocks under both
        // pistons: (11,-5,2) from the east and (11,-5,3) from the south. Powers are the steady state
        // with the torch lit.
        this.placeBlock(level, Blocks.REDSTONE_WALL_TORCH.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
                .setValue(BlockStateProperties.LIT, true), 15, -3, 1, chunkBB);
        terrainlab$wire(level, chunkBB, 15, -4, 1, 15, RedstoneSide.SIDE, RedstoneSide.SIDE, null, null);
        terrainlab$wire(level, chunkBB, 15, -5, 2, 14, RedstoneSide.SIDE, RedstoneSide.UP, null, RedstoneSide.SIDE);
        terrainlab$wire(level, chunkBB, 14, -5, 2, 13, null, null, RedstoneSide.SIDE, RedstoneSide.SIDE);
        terrainlab$wire(level, chunkBB, 13, -5, 2, 12, null, null, RedstoneSide.SIDE, RedstoneSide.SIDE);
        terrainlab$wire(level, chunkBB, 12, -5, 2, 11, null, null, RedstoneSide.SIDE, RedstoneSide.SIDE);
        terrainlab$wire(level, chunkBB, 15, -5, 3, 13, RedstoneSide.SIDE, RedstoneSide.SIDE, null, null);
        terrainlab$wire(level, chunkBB, 15, -5, 4, 12, RedstoneSide.SIDE, RedstoneSide.SIDE, null, null);
        terrainlab$wire(level, chunkBB, 15, -5, 5, 11, null, RedstoneSide.SIDE, null, RedstoneSide.SIDE);
        terrainlab$wire(level, chunkBB, 14, -5, 5, 10, null, null, RedstoneSide.SIDE, RedstoneSide.SIDE);
        terrainlab$wire(level, chunkBB, 13, -5, 5, 9, null, RedstoneSide.UP, RedstoneSide.SIDE, RedstoneSide.SIDE);
        terrainlab$wire(level, chunkBB, 12, -5, 5, 8, null, null, RedstoneSide.SIDE, RedstoneSide.SIDE);
        terrainlab$wire(level, chunkBB, 11, -5, 5, 7, null, RedstoneSide.SIDE, RedstoneSide.SIDE, null);
        terrainlab$wire(level, chunkBB, 11, -5, 4, 6, RedstoneSide.SIDE, RedstoneSide.SIDE, null, null);
        // Too far, part 1: repeater off the dust-5 block, stepping down into the network.
        this.placeBlock(level, Blocks.REPEATER.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), 14, -3, 3, chunkBB);
        terrainlab$wire(level, chunkBB, 13, -3, 3, 7, RedstoneSide.SIDE, null, RedstoneSide.SIDE, null);
        terrainlab$wire(level, chunkBB, 13, -4, 4, 8, RedstoneSide.SIDE, RedstoneSide.UP, null, null);

        // Too far, part 2: a second repeater off the dust-5 block, a dust line down and west under
        // the floor to (7,-5,7), then a two-torch tower up the wall beside the temple's staircase to
        // the lava dispenser at the top of the stairs.
        this.placeBlock(level, Blocks.REPEATER.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH), 15, -3, 4, chunkBB);
        terrainlab$wire(level, chunkBB, 15, -3, 5, 0, RedstoneSide.SIDE, RedstoneSide.SIDE, null, null);
        terrainlab$wire(level, chunkBB, 15, -4, 6, 0, RedstoneSide.SIDE, RedstoneSide.UP, null, null);
        terrainlab$wire(level, chunkBB, 15, -5, 7, 0, null, RedstoneSide.UP, null, RedstoneSide.SIDE);
        for (int x = 14; x >= 8; x--) {
            terrainlab$wire(level, chunkBB, x, -5, 7, 0, null, null, RedstoneSide.SIDE, RedstoneSide.SIDE);
        }
        this.placeBlock(level, mossy, 7, -5, 7, chunkBB);
        this.placeBlock(level, Blocks.REDSTONE_TORCH.defaultBlockState()
                .setValue(BlockStateProperties.LIT, true), 7, -4, 7, chunkBB);
        this.placeBlock(level, Blocks.REDSTONE_TORCH.defaultBlockState()
                .setValue(BlockStateProperties.LIT, false), 7, -2, 7, chunkBB);
        this.placeBlock(level, Blocks.DISPENSER.defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.WEST), 7, 0, 7, chunkBB);
        this.placeBlock(level, Blocks.MOSS_CARPET.defaultBlockState(), 7, 1, 7, chunkBB); // hides the dispenser's top
        // Vines hang over the dispenser's face at the top of the stairs. Vines are replaceable, so the
        // lava still pours out through them (and burns them away).
        this.placeBlock(level, Blocks.VINE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.VineBlock.EAST, true), 6, 0, 7, chunkBB);
        BlockPos dispenserPos = this.getWorldPos(7, 0, 7);
        if (chunkBB.isInside(dispenserPos) && level.getBlockEntity(dispenserPos) instanceof DispenserBlockEntity dispenser) {
            dispenser.setItem(0, new ItemStack(Items.LAVA_BUCKET));
        }

        // ---- Item frames (only in the chunk that contains them).
        BlockPos dialPos = this.getWorldPos(9, -2, 2);
        if (chunkBB.isInside(dialPos)) {
            ItemFrame dial = new ItemFrame(level.getLevel(), dialPos.immutable(),
                    terrainlab$facing(this.getWorldPos(10, -2, 2), dialPos));
            dial.setItem(new ItemStack(Items.ARROW), false);
            level.addFreshEntity(dial);
        }
        // One extra large (3x3) map frame, centred on the east wall. It is generated empty and
        // JungleTempleMapHandler puts the Temple Map in it once the room loads.
        BlockPos mapPos = this.getWorldPos(9, -10, 10);
        if (chunkBB.isInside(mapPos)) {
            OversizedMapFrameEntity mapFrame = new OversizedMapFrameEntity(ModEntities.EXTRA_LARGE_MAP_FRAME.get(),
                    level.getLevel(), mapPos.immutable(), terrainlab$facing(this.getWorldPos(10, -10, 10), mapPos));
            mapFrame.getPersistentData().putBoolean(JungleTempleMapHandler.MAP_TAG, true);
            level.addFreshEntity(mapFrame);
        }
    }

    /**
     * Places redstone dust with the given power and sides (local north = +z, south = -z, east = +x,
     * west = -x; null means not connected). placeBlock rotates and mirrors the sides with the temple.
     */
    @Unique
    private void terrainlab$wire(WorldGenLevel level, BoundingBox chunkBB, int x, int y, int z, int power,
            RedstoneSide north, RedstoneSide south, RedstoneSide east, RedstoneSide west) {
        BlockState wire = Blocks.REDSTONE_WIRE.defaultBlockState().setValue(BlockStateProperties.POWER, power);
        wire = terrainlab$side(wire, BlockStateProperties.NORTH_REDSTONE, north);
        wire = terrainlab$side(wire, BlockStateProperties.SOUTH_REDSTONE, south);
        wire = terrainlab$side(wire, BlockStateProperties.EAST_REDSTONE, east);
        wire = terrainlab$side(wire, BlockStateProperties.WEST_REDSTONE, west);
        this.placeBlock(level, wire, x, y, z, chunkBB);
    }

    @Unique
    private static BlockState terrainlab$side(BlockState state, EnumProperty<RedstoneSide> property, RedstoneSide side) {
        return state.setValue(property, side == null ? RedstoneSide.NONE : side);
    }

    @Unique
    private void terrainlab$box(WorldGenLevel level, BoundingBox chunkBB, BlockState state,
            int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    this.placeBlock(level, state, x, y, z, chunkBB);
                }
            }
        }
    }

    /** World direction from the wall block to the frame's block, i.e. the way the frame faces. */
    @Unique
    private static Direction terrainlab$facing(BlockPos wall, BlockPos frame) {
        int dx = frame.getX() - wall.getX();
        int dz = frame.getZ() - wall.getZ();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (d.getStepX() == dx && d.getStepZ() == dz) return d;
        }
        return Direction.NORTH;
    }
}
