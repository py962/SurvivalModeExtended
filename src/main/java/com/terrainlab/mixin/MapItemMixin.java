package com.terrainlab.mixin;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.terrainlab.TerrainLab;
import com.terrainlab.event.SpyglassMapFillHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Paints spyglass-flagged chunks onto the offhand map at the end of
 * {@code MapItem#update}, while the player looks through a spyglass, and marks the visible
 * structures in them (see {@link com.terrainlab.item.SpyglassStructureMarker}).
 */
@Mixin(MapItem.class)
public abstract class MapItemMixin implements MapItemFluidStateAccessor {

    @Inject(method = "update", at = @At("TAIL"))
    private void terrainlab$fillFromSpyglass(Level level, Entity entity, MapItemSavedData data, CallbackInfo ci) {
        if (!(entity instanceof Player player) || level.isClientSide()) return;

        // Vanilla's update body draws only in the map's own dimension; match it.
        if (level.dimension() != data.dimension) return;

        if (!player.isUsingItem() || !player.getUseItem().is(Items.SPYGLASS)) return;

        ItemStack offhand = player.getOffhandItem();
        if (offhand.isEmpty() || !offhand.is(Items.FILLED_MAP)) return;

        MapItemSavedData offhandData = MapItem.getSavedData(offhand, level);
        if (offhandData != data) return; // this update() call wasn't for the offhand map

        // Each flagged chunk is handed out once per scoping session.
        Set<ChunkPos> flagged = SpyglassMapFillHandler.takeUnpaintedChunks(player);
        // TerrainLab.LOGGER.info("[SPYGLASS_MAP] mixin fired for {}, {} flagged chunks to paint", player.getUUID(), flagged.size());
        if (flagged.isEmpty()) return;

        int i = 1 << data.scale;
        int centerX = data.centerX;
        int centerZ = data.centerZ;
        int painted = 0;

        for (ChunkPos cp : flagged) {
            int worldX = cp.getMiddleBlockX();
            int worldZ = cp.getMiddleBlockZ();
            int k1 = Mth.floor((double) worldX - centerX) / i + 64;
            int l1 = Mth.floor((double) worldZ - centerZ) / i + 64;
            if (k1 < 0 || l1 < 0 || k1 >= 128 || l1 >= 128) continue;

            terrainlab$samplePixel(level, data, i, centerX, centerZ, k1, l1);
            painted++;
        }

        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            for (ChunkPos cp : flagged) {
                com.terrainlab.item.SpyglassStructureMarker.markChunk(serverLevel, offhand, data, cp);
            }
        }

        // TerrainLab.LOGGER.info("[SPYGLASS_MAP] painted {} of {} flagged chunks (rest out of map bounds)", painted, flagged.size());
    }

    /**
     * Samples one map pixel the way vanilla's per-column body in {@code MapItem#update} does.
     * Always uses {@code Brightness.NORMAL}: vanilla shading needs the previous column
     * in the scan line, which an isolated pixel lacks.
     */
    @Unique
    private void terrainlab$samplePixel(Level level, MapItemSavedData data, int i, int centerX, int centerZ, int k1, int l1) {
        int j2 = (centerX / i + k1 - 64) * i;
        int k2 = (centerZ / i + l1 - 64) * i;
        LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(j2), SectionPos.blockToSectionCoord(k2));
        if (chunk.isEmpty()) return;

        if (level.dimensionType().hasCeiling()) {
            int seed = j2 + k2 * 231871;
            seed = seed * seed * 31287121 + seed * 11;
            MapColor c = (seed >> 20 & 1) == 0
                    ? Blocks.DIRT.defaultBlockState().getMapColor(level, BlockPos.ZERO)
                    : Blocks.STONE.defaultBlockState().getMapColor(level, BlockPos.ZERO);
            data.updateColor(k1, l1, c.getPackedId(MapColor.Brightness.NORMAL));
            return;
        }

        Multiset<MapColor> multiset = LinkedHashMultiset.create();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos pos2 = new BlockPos.MutableBlockPos();

        for (int dx = 0; dx < i; dx++) {
            for (int dz = 0; dz < i; dz++) {
                pos.set(j2 + dx, 0, k2 + dz);
                int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) + 1;
                BlockState state;
                if (height <= level.getMinY()) {
                    state = Blocks.BEDROCK.defaultBlockState();
                } else {
                    do {
                        pos.setY(--height);
                        state = chunk.getBlockState(pos);
                    } while (state.getMapColor(level, pos) == MapColor.NONE && height > level.getMinY());

                    if (height > level.getMinY() && !state.getFluidState().isEmpty()) {
                        int fy = height - 1;
                        pos2.set(pos);
                        BlockState under;
                        do {
                            pos2.setY(fy--);
                            under = chunk.getBlockState(pos2);
                        } while (fy > level.getMinY() && !under.getFluidState().isEmpty());

                        state = this.terrainlab$getCorrectStateForFluidBlock(level, state, pos);
                    }
                }
                data.checkBanners(level, pos.getX(), pos.getZ());
                multiset.add(state.getMapColor(level, pos));
            }
        }

        MapColor mapColor = Iterables.getFirst(Multisets.copyHighestCountFirst(multiset), MapColor.NONE);
        data.updateColor(k1, l1, mapColor.getPackedId(MapColor.Brightness.NORMAL));
    }
}
