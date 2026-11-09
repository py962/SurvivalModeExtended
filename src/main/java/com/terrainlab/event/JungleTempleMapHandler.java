package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Fills the extra large (3x3) map frame in the jungle temple's hidden room (built by
 * JungleTempleHiddenRoomMixin). The frame is generated empty, because the structure search behind
 * an explorer map can't run during world generation. The first time it loads into the live world,
 * the next level tick puts in a roll of terrainlab:chests/jungle_temple_hidden_map: the Temple Map
 * the temple chest used to give, an explorer map centred on the nearest Ancient City and the item
 * the Archeologist asks for. If no Ancient City is in range, it gets a plain map of the temple's
 * own area instead.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class JungleTempleMapHandler {

    public static final String MAP_TAG = "terrainlab_jungle_map";

    private static final ResourceKey<LootTable> MAP_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "chests/jungle_temple_hidden_map"));

    /** The Temple Map's own zoom, used for the fallback map. */
    private static final byte FALLBACK_SCALE = 2;

    private static final Set<ItemFrame> PENDING = new LinkedHashSet<>();

    private JungleTempleMapHandler() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof ItemFrame frame && frame.getPersistentData().getBooleanOr(MAP_TAG, false)) {
            PENDING.add(frame);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (PENDING.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) return;
        List<ItemFrame> ready = new ArrayList<>();
        PENDING.removeIf(frame -> {
            if (frame.isRemoved()) return true;
            if (frame.level() != level) return false;
            ready.add(frame);
            return true;
        });
        for (ItemFrame frame : ready) {
            fill(level, frame);
        }
    }

    private static void fill(ServerLevel level, ItemFrame frame) {
        frame.getPersistentData().remove(MAP_TAG);
        LootTable table = level.getServer().reloadableRegistries().getLootTable(MAP_LOOT);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(frame.blockPosition()))
                .create(LootContextParamSets.CHEST);
        List<ItemStack> items = table.getRandomItems(params);
        ItemStack map = items.isEmpty() ? ItemStack.EMPTY : items.get(0);
        if (map.isEmpty() || MapItem.getSavedData(map, level) == null) {
            map = MapItem.create(level, frame.blockPosition().getX(), frame.blockPosition().getZ(), FALLBACK_SCALE, true, false);
            MapItem.renderBiomePreviewMap(level, map);
        }
        frame.setItem(map, true);
    }
}
