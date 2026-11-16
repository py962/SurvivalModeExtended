package com.terrainlab.hearth;

import com.google.common.collect.ImmutableSet;
import com.terrainlab.TerrainLab;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModHearth {

    private ModHearth() {
    }

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TerrainLab.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TerrainLab.MODID);
    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, TerrainLab.MODID);

    public static final DeferredBlock<HearthBlock> HEARTH =
            BLOCKS.register("hearth", id -> new HearthBlock(
                    BlockBehaviour.Properties.of()
                            .setId(ResourceKey.create(Registries.BLOCK, id))
                            .mapColor(MapColor.COLOR_RED)
                            .instrument(NoteBlockInstrument.BASEDRUM)
                            .strength(2.0F)
                            .sound(SoundType.STONE)
                            .lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 15 : 0)
                            .noOcclusion()));

    public static final DeferredItem<BlockItem> HEARTH_ITEM =
            ITEMS.register("hearth", id -> new BlockItem(
                    HEARTH.get(),
                    new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id))));

    public static final DeferredHolder<PoiType, PoiType> HEARTH_POI =
            POI_TYPES.register("hearth", () -> new PoiType(
                    ImmutableSet.copyOf(HEARTH.get().getStateDefinition().getPossibleStates()), 0, 1));

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        POI_TYPES.register(modEventBus);
        modEventBus.addListener(ModHearth::onBlockEntityTypeAddBlocks);
        modEventBus.addListener(ModHearth::onBuildCreativeTabs);
    }

    private static void onBlockEntityTypeAddBlocks(BlockEntityTypeAddBlocksEvent event) {
        event.modify(HearthCompat.CAMPFIRE_BLOCK_ENTITY, HEARTH.get());
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.insertAfter(new ItemStack(Items.SOUL_CAMPFIRE), new ItemStack(HEARTH_ITEM.get()),
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }
}
