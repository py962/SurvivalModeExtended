package com.terrainlab.mixin;

import com.terrainlab.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Builds the hidden Ancient Codex chamber as part of the desert pyramid itself, in the same
 * chunk-by-chunk pass that places the vanilla treasure room, so every pyramid has one.
 * <p>
 * Layout, in the pyramid's local coordinates (footprint 0-20, treasure room centred on 10,10):
 * a 2-block passage leads out from behind one of the four treasure chests, at the treasure room's
 * floor level, and stops at the chamber's wall, which has to be dug through. The chamber is 5x5
 * (3x3x3 inside), centred 7 blocks from the pressure plate, so it stays inside the pyramid's
 * footprint and therefore inside the chunks the pyramid generates in. The chamber and passage are
 * fully walled in sandstone, so they stay sealed even where they cut through a cave or water.
 * <p>
 * The direction comes from the world seed and the pyramid's position, not the per-chunk random,
 * so every chunk agrees on it.
 */
@Mixin(DesertPyramidPiece.class)
public abstract class DesertPyramidHiddenChamberMixin extends ScatteredFeaturePiece {

    /** Local x/z offsets for north, east, south and west. */
    @Unique
    private static final int[][] TERRAINLAB$DIRECTIONS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    @Unique
    private static final int TERRAINLAB$CENTRE = 10;

    /** Treasure room floor level (the pressure plate's block is at -11). */
    @Unique
    private static final int TERRAINLAB$FLOOR_Y = -12;

    @Unique
    private static final int TERRAINLAB$PASSAGE_START = 3;

    @Unique
    private static final int TERRAINLAB$PASSAGE_LENGTH = 2;

    @Unique
    private static final int TERRAINLAB$CHAMBER_DISTANCE = 7;

    protected DesertPyramidHiddenChamberMixin(StructurePieceType type, CompoundTag tag) {
        super(type, tag);
    }

    @Inject(method = "postProcess", at = @At("TAIL"))
    private void terrainlab$addHiddenChamber(WorldGenLevel level, StructureManager structureManager,
            ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos,
            BlockPos referencePos, CallbackInfo ci) {
        // Vanilla only builds once it has settled the pyramid's height.
        if (this.heightPosition < 0) return;

        long hash = level.getSeed()
                ^ this.boundingBox.minX() * 341873128712L
                ^ this.boundingBox.minZ() * 132897987541L;
        int[] dir = TERRAINLAB$DIRECTIONS[Math.floorMod(Long.hashCode(hash), 4)];
        int dx = dir[0];
        int dz = dir[1];
        int px = -dz; // perpendicular
        int pz = dx;

        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState sandstone = Blocks.SANDSTONE.defaultBlockState();
        BlockState chiselled = Blocks.CHISELED_SANDSTONE.defaultBlockState();

        // Passage: 1 wide, 2 high, sealed with a sandstone shell. Its first ceiling block is the
        // vanilla chiselled sandstone behind the chest, which is left alone.
        for (int i = 0; i < TERRAINLAB$PASSAGE_LENGTH; i++) {
            int d = TERRAINLAB$PASSAGE_START + i;
            for (int s = -1; s <= 1; s++) {
                for (int y = TERRAINLAB$FLOOR_Y - 1; y <= TERRAINLAB$FLOOR_Y + 2; y++) {
                    boolean inside = s == 0 && (y == TERRAINLAB$FLOOR_Y || y == TERRAINLAB$FLOOR_Y + 1);
                    if (!inside && s == 0 && y == TERRAINLAB$FLOOR_Y + 2 && i == 0) continue;
                    terrainlab$place(level, inside ? air : sandstone, dx, dz, px, pz, d, s, y, chunkBB);
                }
            }
        }

        // Chamber: 5x5 shell from the floor below to the ceiling above.
        for (int a = -2; a <= 2; a++) {
            for (int b = -2; b <= 2; b++) {
                int d = TERRAINLAB$CHAMBER_DISTANCE + b;
                boolean wall = Math.abs(a) == 2 || Math.abs(b) == 2;
                boolean corner = Math.abs(a) == 2 && Math.abs(b) == 2;
                terrainlab$place(level, sandstone, dx, dz, px, pz, d, a, TERRAINLAB$FLOOR_Y - 1, chunkBB);
                terrainlab$place(level, sandstone, dx, dz, px, pz, d, a, TERRAINLAB$FLOOR_Y + 3, chunkBB);
                for (int y = 0; y <= 2; y++) {
                    BlockState state = !wall ? air : corner ? chiselled : sandstone;
                    terrainlab$place(level, state, dx, dz, px, pz, d, a, TERRAINLAB$FLOOR_Y + y, chunkBB);
                }
            }
        }

        // Chiselled dais under and around the codex.
        int c = TERRAINLAB$CHAMBER_DISTANCE;
        terrainlab$place(level, chiselled, dx, dz, px, pz, c, 0, TERRAINLAB$FLOOR_Y - 1, chunkBB);
        terrainlab$place(level, chiselled, dx, dz, px, pz, c - 1, 0, TERRAINLAB$FLOOR_Y - 1, chunkBB);
        terrainlab$place(level, chiselled, dx, dz, px, pz, c + 1, 0, TERRAINLAB$FLOOR_Y - 1, chunkBB);
        terrainlab$place(level, chiselled, dx, dz, px, pz, c, -1, TERRAINLAB$FLOOR_Y - 1, chunkBB);
        terrainlab$place(level, chiselled, dx, dz, px, pz, c, 1, TERRAINLAB$FLOOR_Y - 1, chunkBB);

        terrainlab$place(level, ModBlocks.ANCIENT_CODEX.get().defaultBlockState(), dx, dz, px, pz, c, 0, TERRAINLAB$FLOOR_Y, chunkBB);
        terrainlab$place(level, Blocks.LANTERN.defaultBlockState(), dx, dz, px, pz, c, 0, TERRAINLAB$FLOOR_Y + 3, chunkBB);

        BlockState candle = Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true);
        for (int a = -1; a <= 1; a += 2) {
            for (int b = -1; b <= 1; b += 2) {
                terrainlab$place(level, candle, dx, dz, px, pz, c + b, a, TERRAINLAB$FLOOR_Y, chunkBB);
            }
        }
    }

    /**
     * Places a block {@code distance} blocks from the treasure room centre along the chosen direction
     * and {@code side} blocks across it. placeBlock applies the pyramid's orientation and only
     * writes inside the chunk being generated.
     */
    @Unique
    private void terrainlab$place(WorldGenLevel level, BlockState state, int dx, int dz, int px, int pz,
            int distance, int side, int y, BoundingBox chunkBB) {
        int x = TERRAINLAB$CENTRE + dx * distance + px * side;
        int z = TERRAINLAB$CENTRE + dz * distance + pz * side;
        this.placeBlock(level, state, x, y, z, chunkBB);
    }
}
