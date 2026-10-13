package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Common hookups for the Pine, Azalea and Enderwood wood sets (blocks in ModBlocks):
 * signs join vanilla's sign / hanging sign block entity types, axes strip logs and wood,
 * the pine sapling can be potted, and the pine and azalea blocks burn like vanilla wood
 * (enderwood, like the nether woods, does not).
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class WoodSetEvents {

    private WoodSetEvents() {
    }

    // Mod bus.
    @SubscribeEvent
    public static void onBlockEntityValidBlocks(BlockEntityTypeAddBlocksEvent event) {
        event.modify(BlockEntityType.SIGN,
                ModBlocks.PINE_SIGN.get(), ModBlocks.PINE_WALL_SIGN.get(),
                ModBlocks.AZALEA_SIGN.get(), ModBlocks.AZALEA_WALL_SIGN.get(),
                ModBlocks.ENDERWOOD_SIGN.get(), ModBlocks.ENDERWOOD_WALL_SIGN.get());
        event.modify(BlockEntityType.HANGING_SIGN,
                ModBlocks.PINE_HANGING_SIGN.get(), ModBlocks.PINE_WALL_HANGING_SIGN.get(),
                ModBlocks.AZALEA_HANGING_SIGN.get(), ModBlocks.AZALEA_WALL_HANGING_SIGN.get(),
                ModBlocks.ENDERWOOD_HANGING_SIGN.get(), ModBlocks.ENDERWOOD_WALL_HANGING_SIGN.get());
    }

    // Mod bus.
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ((FlowerPotBlock) Blocks.FLOWER_POT).addPlant(ModBlocks.PINE_SAPLING.getId(), ModBlocks.POTTED_PINE_SAPLING);

            // Encouragement / flammability values from vanilla FireBlock.bootStrap.
            FireBlock fire = (FireBlock) Blocks.FIRE;
            flammable(fire, 5, 20,
                    ModBlocks.PINE_PLANKS, ModBlocks.PINE_SLAB, ModBlocks.PINE_STAIRS,
                    ModBlocks.PINE_FENCE, ModBlocks.PINE_FENCE_GATE,
                    ModBlocks.AZALEA_PLANKS, ModBlocks.AZALEA_SLAB, ModBlocks.AZALEA_STAIRS,
                    ModBlocks.AZALEA_FENCE, ModBlocks.AZALEA_FENCE_GATE);
            flammable(fire, 5, 5,
                    ModBlocks.PINE_LOG, ModBlocks.PINE_WOOD, ModBlocks.STRIPPED_PINE_LOG, ModBlocks.STRIPPED_PINE_WOOD,
                    ModBlocks.AZALEA_LOG, ModBlocks.AZALEA_WOOD, ModBlocks.STRIPPED_AZALEA_LOG, ModBlocks.STRIPPED_AZALEA_WOOD);
            flammable(fire, 30, 60, ModBlocks.PINE_LEAVES);
        });
    }

    @SafeVarargs
    private static void flammable(FireBlock fire, int encouragement, int flammability,
                                  Supplier<? extends Block>... blocks) {
        for (Supplier<? extends Block> block : blocks) {
            fire.setFlammable(block.get(), encouragement, flammability);
        }
    }

    // Game bus: axe stripping (NeoForge replacement for AxeItem.STRIPPABLES).
    @SubscribeEvent
    public static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (event.getItemAbility() != ItemAbilities.AXE_STRIP) {
            return;
        }
        BlockState state = event.getState();
        Block stripped = strippedFor(state.getBlock());
        if (stripped != null) {
            event.setFinalState(stripped.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, state.getValue(RotatedPillarBlock.AXIS)));
        }
    }

    private static Map<Block, Block> strippables;

    private static Block strippedFor(Block block) {
        if (strippables == null) {
            strippables = Map.of(
                    ModBlocks.PINE_LOG.get(), ModBlocks.STRIPPED_PINE_LOG.get(),
                    ModBlocks.PINE_WOOD.get(), ModBlocks.STRIPPED_PINE_WOOD.get(),
                    ModBlocks.AZALEA_LOG.get(), ModBlocks.STRIPPED_AZALEA_LOG.get(),
                    ModBlocks.AZALEA_WOOD.get(), ModBlocks.STRIPPED_AZALEA_WOOD.get());
        }
        return strippables.get(block);
    }
}
