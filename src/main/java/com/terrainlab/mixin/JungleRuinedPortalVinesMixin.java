package com.terrainlab.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.structures.RuinedPortalPiece;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Jungle ruined portals (the only setup that is overgrown with vines) are smothered in vines: every
 * side of every solid block of the portal that faces open air gets a vine, not just vanilla's one
 * random side per block.
 */
@Mixin(RuinedPortalPiece.class)
public abstract class JungleRuinedPortalVinesMixin {

    @Shadow @Final private RuinedPortalPiece.Properties properties;

    @Inject(method = "postProcess", at = @At("TAIL"), require = 1)
    private void terrainlab$smotherInVines(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                                           RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos,
                                           CallbackInfo ci) {
        if (!(this.properties.overgrown && this.properties.vines)) {
            return;
        }
        BoundingBox box = ((StructurePiece) (Object) this).getBoundingBox();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    pos.set(x, y, z);
                    if (!chunkBB.isInside(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.is(Blocks.VINE)) continue;
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        side.setWithOffset(pos, dir);
                        if (!chunkBB.isInside(side)) continue;
                        if (!Block.isFaceFull(state.getCollisionShape(level, pos), dir)) continue;
                        BlockState there = level.getBlockState(side);
                        BooleanProperty face = VineBlock.getPropertyForFace(dir.getOpposite());
                        if (there.isAir()) {
                            level.setBlock(side, Blocks.VINE.defaultBlockState().setValue(face, true), Block.UPDATE_CLIENTS);
                        } else if (there.is(Blocks.VINE) && !there.getValue(face)) {
                            level.setBlock(side, there.setValue(face, true), Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        }
    }
}
