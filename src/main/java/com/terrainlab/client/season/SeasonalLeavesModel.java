package com.terrainlab.client.season;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import org.jspecify.annotations.Nullable;

/**
 * Wraps a vanilla leaves block-state model and swaps in a seasonal texture variant when
 * {@link SeasonalLeaves#lookAt} asks for one. Looks with no entry in {@code variants} (VANILLA, and
 * tint-only looks such as oak BROWN) render the vanilla model. A look with several parts is a shaded autumn
 * look: the part is picked by {@link SeasonalLeaves#shadeAt}, a position hash, so it costs nothing per frame.
 */
final class SeasonalLeavesModel extends DelegateBlockStateModel {

    private final SeasonalLeaves.Tree tree;
    private final Map<SeasonalLeaves.Look, BlockStateModelPart[]> variants;

    SeasonalLeavesModel(BlockStateModel delegate, SeasonalLeaves.Tree tree, Map<SeasonalLeaves.Look, BlockStateModelPart[]> variants) {
        super(delegate);
        this.tree = tree;
        this.variants = variants.isEmpty() ? Map.of() : new EnumMap<>(variants);
    }

    private @Nullable BlockStateModelPart variant(BlockAndTintGetter level, BlockPos pos) {
        if (this.variants.isEmpty()) {
            return null;
        }
        BlockStateModelPart[] parts = this.variants.get(SeasonalLeaves.lookAt(level, pos, this.tree));
        if (parts == null) {
            return null;
        }
        return parts.length == 1 ? parts[0] : parts[SeasonalLeaves.shadeAt(pos) % parts.length];
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        BlockStateModelPart part = variant(level, pos);
        if (part != null) {
            parts.add(part);
        } else {
            this.delegate.collectParts(level, pos, state, random, parts);
        }
    }

    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        BlockStateModelPart part = variant(level, pos);
        return part != null ? part.particleMaterial() : this.delegate.particleMaterial(level, pos, state);
    }

    @Override
    @BakedQuad.MaterialFlags
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        BlockStateModelPart part = variant(level, pos);
        return part != null ? part.materialFlags() : this.delegate.materialFlags(level, pos, state);
    }
}
