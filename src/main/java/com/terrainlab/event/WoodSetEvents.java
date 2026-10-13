package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Common hookups for the Pine, Azalea and Enderwood wood sets (blocks in ModBlocks):
 * signs join vanilla's sign / hanging sign block entity types, and the pine and azalea blocks
 * burn like vanilla wood (enderwood, like the nether woods, does not).
 *
 * <p>Since 26.3 axe stripping is data-driven (block transformers): the strippables live in
 * data/neoforge/data_maps/block/transformables.json. The potted pine sapling is linked to the
 * empty flower pot automatically by NeoForge when it is registered.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class WoodSetEvents {

    private WoodSetEvents() {
    }

    // Mod bus.
    @SubscribeEvent
    public static void onBlockEntityValidBlocks(BlockEntityTypeAddBlocksEvent event) {
        event.modify(BlockEntityTypes.SIGN,
                ModBlocks.PINE_SIGN.get(), ModBlocks.PINE_WALL_SIGN.get(),
                ModBlocks.AZALEA_SIGN.get(), ModBlocks.AZALEA_WALL_SIGN.get(),
                ModBlocks.ENDERWOOD_SIGN.get(), ModBlocks.ENDERWOOD_WALL_SIGN.get());
        event.modify(BlockEntityTypes.HANGING_SIGN,
                ModBlocks.PINE_HANGING_SIGN.get(), ModBlocks.PINE_WALL_HANGING_SIGN.get(),
                ModBlocks.AZALEA_HANGING_SIGN.get(), ModBlocks.AZALEA_WALL_HANGING_SIGN.get(),
                ModBlocks.ENDERWOOD_HANGING_SIGN.get(), ModBlocks.ENDERWOOD_WALL_HANGING_SIGN.get());
    }

    // Mod bus.
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
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

    // FireBlock.setFlammable is private in 26.3 (it was public under NeoForge 1.21.11) and there is
    // no other registration hook, so it is called reflectively. Minecraft is an automatic module,
    // so its packages are open to reflection.
    private static Method setFlammable;

    @SafeVarargs
    private static void flammable(FireBlock fire, int encouragement, int flammability,
                                  Supplier<? extends Block>... blocks) {
        try {
            if (setFlammable == null) {
                setFlammable = FireBlock.class.getDeclaredMethod("setFlammable", Block.class, int.class, int.class);
                setFlammable.setAccessible(true);
            }
            for (Supplier<? extends Block> block : blocks) {
                setFlammable.invoke(fire, block.get(), encouragement, flammability);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            TerrainLab.LOGGER.error("Could not register wood-set flammability", e);
        }
    }
}
