package com.terrainlab.client.season;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import org.jspecify.annotations.Nullable;

/**
 * Wraps a vanilla leaves block-state model and swaps in a seasonal texture variant when
 * {@link SeasonalLeaves#lookAt} asks for one. Looks with no entry in {@code variants} (VANILLA, and
 * tint-only looks such as oak BROWN) render the vanilla model.
 */
final class SeasonalLeavesModel extends DelegateBlockStateModel {

    private final SeasonalLeaves.Tree tree;
    private final Map<SeasonalLeaves.Look, BlockModelPart> variants;

    SeasonalLeavesModel(BlockStateModel delegate, SeasonalLeaves.Tree tree, Map<SeasonalLeaves.Look, BlockModelPart> variants) {
        super(delegate);
        this.tree = tree;
        this.variants = variants.isEmpty() ? Map.of() : new EnumMap<>(variants);
    }

    private @Nullable BlockModelPart variant(BlockAndTintGetter level, BlockPos pos) {
        if (this.variants.isEmpty()) {
            return null;
        }
        return this.variants.get(SeasonalLeaves.lookAt(level, pos, this.tree));
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockModelPart> parts) {
        BlockModelPart part = variant(level, pos);
        if (part != null) {
            parts.add(part);
        } else {
            this.delegate.collectParts(level, pos, state, random, parts);
        }
    }

    @Override
    public TextureAtlasSprite particleIcon(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        BlockModelPart part = variant(level, pos);
        return part != null ? part.particleIcon() : this.delegate.particleIcon(level, pos, state);
    }
}
