package com.terrainlab.cushion;

import com.terrainlab.TerrainLab;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/**
 * Cushions, backported from 26.3: one entity type and 16 coloured items. In the 26.3 version these ids are
 * aliases of the vanilla cushion ids, so items and placed cushions carry over when a world is upgraded.
 */
public final class Cushions {
    private Cushions() {}

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, TerrainLab.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TerrainLab.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CushionEntity>> CUSHION =
            ENTITY_TYPES.register("cushion", id -> EntityType.Builder.<CushionEntity>of(CushionEntity::new, MobCategory.MISC)
                    .noLootTable()
                    .sized(1.0F, 0.25F)
                    .clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    public static final Map<DyeColor, DeferredItem<CushionItem>> ITEMS_BY_COLOR = new EnumMap<>(DyeColor.class);

    static {
        for (DyeColor color : DyeColor.values()) {
            ITEMS_BY_COLOR.put(color, ITEMS.registerItem(color.getName() + "_cushion",
                    props -> new CushionItem(color, props.stacksTo(16))));
        }
    }

    /** Blocks whose collision shape (not outline) a cushion is placed against: cauldrons, hopper, composter. */
    public static final TagKey<Block> USES_COLLISION_SHAPE =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(TerrainLab.MODID, "cushion_uses_collision_shape"));

    public static Item item(DyeColor color) {
        return ITEMS_BY_COLOR.get(color).get();
    }

    public static Block wool(DyeColor color) {
        return switch (color) {
            case WHITE -> Blocks.WHITE_WOOL;
            case ORANGE -> Blocks.ORANGE_WOOL;
            case MAGENTA -> Blocks.MAGENTA_WOOL;
            case LIGHT_BLUE -> Blocks.LIGHT_BLUE_WOOL;
            case YELLOW -> Blocks.YELLOW_WOOL;
            case LIME -> Blocks.LIME_WOOL;
            case PINK -> Blocks.PINK_WOOL;
            case GRAY -> Blocks.GRAY_WOOL;
            case LIGHT_GRAY -> Blocks.LIGHT_GRAY_WOOL;
            case CYAN -> Blocks.CYAN_WOOL;
            case PURPLE -> Blocks.PURPLE_WOOL;
            case BLUE -> Blocks.BLUE_WOOL;
            case BROWN -> Blocks.BROWN_WOOL;
            case GREEN -> Blocks.GREEN_WOOL;
            case RED -> Blocks.RED_WOOL;
            case BLACK -> Blocks.BLACK_WOOL;
        };
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
