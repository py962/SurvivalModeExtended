package com.terrainlab.mixin;

import com.terrainlab.registry.ModBlocks;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Replaces vanilla fletching tables in placed structure templates with the mod's fletching table. */
@Mixin(StructureTemplate.class)
public abstract class StructurePlacementMixin {

    @ModifyArg(
            method = "placeInWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/ServerLevelAccessor;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
            ),
            index = 1
    )
    private BlockState terrainlab$swapFletchingTable(BlockState blockstate) {

        if (blockstate.is(Blocks.FLETCHING_TABLE)) {

            // System.out.println(
                    // "[TerrainLab] FLETCHING TABLE FOUND during StructureTemplate placement."
            // );

            // System.out.println(
                    // "[TerrainLab] Original block: "
                            // + blockstate
            // );

            // System.out.println(
                    // "[TerrainLab] Replacement block: "
                            // + ModBlocks.FLETCHING_TABLE.get()
            // );

            BlockState replacement =
                    ModBlocks.FLETCHING_TABLE.get()
                            .defaultBlockState();

            // System.out.println(
                    // "[TerrainLab] Replacement state: "
                            // + replacement
            // );

            return replacement;
        }

        return blockstate;
    }
}
