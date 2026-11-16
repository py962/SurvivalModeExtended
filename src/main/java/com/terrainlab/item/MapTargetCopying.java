package com.terrainlab.item;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Cartography table: copies location data from a source (top slot: a map with structure markers, or a
 * compass or Rangefinder Compass pointing somewhere) onto a recipient (bottom slot) - a plain map gains
 * the markers that lie on it, a compass or Rangefinder Compass is pointed at the first target. The
 * source is kept; the recipient is used up into the result. Compasses made here remember the
 * structure icon, so a later copy back onto a map shows it. Hooked in by {@code CartographyTableMenuMixin}; results are computed on the server
 * only (as vanilla's are) and synced to the client's result slot.
 */
public final class MapTargetCopying {

    /** Key vanilla (and the trail guides' charts) give an explorer map's target decoration. */
    private static final String EXPLORER_TARGET_KEY = "+";
    /** Height a compass target is placed at; compasses and the rangefinder only use X/Z. */
    private static final int TARGET_Y = 64;
    private static final String GUIDE_CHART_PREFIX = "item.terrainlab.guide_chart.";
    private static final String DESTINATION_PREFIX = "terrainlab.map_destination.";
    private static final String ICON_KEY = "terrainlab_target_icon";

    private MapTargetCopying() {
    }

    /** Items this feature accepts in the bottom slot (vanilla only accepts paper, empty maps and glass panes there). */
    public static boolean isRecipient(ItemStack stack) {
        return stack.is(Items.COMPASS)
                || stack.is(ModItems.RANGEFINDER_COMPASS.get())
                || stack.has(DataComponents.MAP_ID) && !stack.is(ModItems.ATLAS.get());
    }

    /** Whether the bottom-slot item belongs to vanilla's own recipes (cloning, zooming, locking). */
    private static boolean isVanillaAdditional(ItemStack stack) {
        return stack.is(Items.PAPER) || stack.is(Items.MAP) || stack.is(Items.GLASS_PANE);
    }

    /**
     * The result of copying {@code source}'s target(s) onto {@code recipient}, or empty when there is
     * nothing to copy (no target, wrong dimension, outside the recipient map, already copied ...).
     */
    public static ItemStack craft(Level level, ItemStack source, ItemStack recipient) {
        if (isCompassSource(source)) {
            return fromCompass(level, source, recipient);
        }
        List<Map.Entry<String, MapDecorations.Entry>> targets = targets(source);
        if (targets.isEmpty()) {
            return ItemStack.EMPTY;
        }
        MapItemSavedData sourceData = MapItem.getSavedData(source, level);
        if (sourceData == null) {
            return ItemStack.EMPTY;
        }
        ResourceKey<Level> dimension = sourceData.dimension;

        if (recipient.is(Items.COMPASS) || recipient.is(ModItems.RANGEFINDER_COMPASS.get())) {
            return pointCompass(source, recipient, targets.getFirst(), dimension);
        }
        return markMap(level, recipient, targets, dimension);
    }

    private static ItemStack pointCompass(ItemStack source, ItemStack recipient,
                                          Map.Entry<String, MapDecorations.Entry> target, ResourceKey<Level> dimension) {
        MapDecorations.Entry entry = target.getValue();
        BlockPos pos = BlockPos.containing(entry.x(), TARGET_Y, entry.z());
        return pointAt(recipient, GlobalPos.of(dimension, pos), destinationName(source, target), entry.type());
    }

    private static ItemStack pointAt(ItemStack recipient, GlobalPos target, Component destination,
                                     Holder<MapDecorationType> icon) {
        ItemStack result = recipient.copyWithCount(1);
        // Untracked: no lodestone needed, and CompassItem#inventoryTick never clears it.
        result.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(target), false));
        String nameKey = recipient.is(Items.COMPASS) ? "item.terrainlab.compass_to" : "item.terrainlab.rangefinder_to";
        result.set(DataComponents.CUSTOM_NAME,
                Component.translatable(nameKey, destination).withStyle(style -> style.withItalic(false)));
        icon.unwrapKey().ifPresent(key -> CustomData.update(DataComponents.CUSTOM_DATA, result,
                tag -> tag.putString(ICON_KEY, key.identifier().toString())));
        return ItemStack.isSameItemSameComponents(result, recipient) ? ItemStack.EMPTY : result;
    }

    /** A compass or Rangefinder Compass pointing somewhere. */
    public static boolean isCompassSource(ItemStack stack) {
        if (!stack.is(Items.COMPASS) && !stack.is(ModItems.RANGEFINDER_COMPASS.get())) {
            return false;
        }
        LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
        return tracker != null && tracker.target().isPresent();
    }

    private static ItemStack fromCompass(Level level, ItemStack source, ItemStack recipient) {
        GlobalPos target = source.get(DataComponents.LODESTONE_TRACKER).target().orElseThrow();
        Holder<MapDecorationType> icon = compassIcon(level, source);
        if (recipient.is(Items.COMPASS) || recipient.is(ModItems.RANGEFINDER_COMPASS.get())) {
            return pointAt(recipient, target, compassDestination(source), icon);
        }
        MapDecorations.Entry entry = new MapDecorations.Entry(icon, target.pos().getX(), target.pos().getZ(), 180.0F);
        return markMap(level, recipient, List.of(Map.entry("compass", entry)), target.dimension());
    }

    // The structure icon a compass made here remembers; any other pointed compass shows vanilla's target point.
    private static Holder<MapDecorationType> compassIcon(Level level, ItemStack compass) {
        CustomData custom = compass.get(DataComponents.CUSTOM_DATA);
        Identifier id = custom == null ? null : Identifier.tryParse(custom.copyTag().getStringOr(ICON_KEY, ""));
        if (id != null) {
            Optional<? extends Holder<MapDecorationType>> icon = level.registryAccess()
                    .lookupOrThrow(Registries.MAP_DECORATION_TYPE)
                    .get(ResourceKey.create(Registries.MAP_DECORATION_TYPE, id));
            if (icon.isPresent()) {
                return icon.get();
            }
        }
        return MapDecorationTypes.TARGET_POINT;
    }

    // A copied compass keeps the destination of its "Compass to ..." name, else its own name.
    private static Component compassDestination(ItemStack compass) {
        Component name = compass.get(DataComponents.CUSTOM_NAME);
        if (name != null && name.getContents() instanceof TranslatableContents translatable
                && (translatable.getKey().equals("item.terrainlab.compass_to")
                        || translatable.getKey().equals("item.terrainlab.rangefinder_to"))
                && translatable.getArgs().length > 0 && translatable.getArgs()[0] instanceof Component destination) {
            return destination;
        }
        return name != null ? name : Component.translatableWithFallback(DESTINATION_PREFIX + "lodestone", "Lodestone");
    }

    private static ItemStack markMap(Level level, ItemStack recipient,
                                     List<Map.Entry<String, MapDecorations.Entry>> targets, ResourceKey<Level> dimension) {
        if (!isPlainMap(recipient)) {
            return ItemStack.EMPTY;
        }
        MapItemSavedData data = MapItem.getSavedData(recipient, level);
        if (data == null || data.dimension != dimension) {
            return ItemStack.EMPTY;
        }
        MapDecorations decorations = recipient.getOrDefault(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY);
        MapDecorations updated = decorations;
        for (Map.Entry<String, MapDecorations.Entry> target : targets) {
            MapDecorations.Entry entry = target.getValue();
            if (!isOnMap(data, entry.x(), entry.z()) || hasMarker(updated, entry)) {
                continue;
            }
            // Same key format as the spyglass's markers, so a structure is only ever marked once.
            String key = TerrainLab.MODID + "_structure_" + Mth.floor(entry.x()) + "_" + Mth.floor(entry.z());
            if (!updated.decorations().containsKey(key)) {
                updated = updated.withDecoration(key, entry);
            }
        }
        if (updated == decorations) {
            return ItemStack.EMPTY;
        }
        ItemStack result = recipient.copyWithCount(1);
        result.set(DataComponents.MAP_DECORATIONS, updated);
        return result;
    }

    /** A filled map that is not itself an explorer map / chart. */
    private static boolean isPlainMap(ItemStack stack) {
        if (!stack.is(Items.FILLED_MAP)) {
            return false;
        }
        MapDecorations decorations = stack.get(DataComponents.MAP_DECORATIONS);
        return decorations == null || !decorations.decorations().containsKey(EXPLORER_TARGET_KEY);
    }

    private static boolean hasMarker(MapDecorations decorations, MapDecorations.Entry entry) {
        for (MapDecorations.Entry existing : decorations.decorations().values()) {
            if (existing.type().equals(entry.type())
                    && Mth.floor(existing.x()) == Mth.floor(entry.x())
                    && Mth.floor(existing.z()) == Mth.floor(entry.z())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOnMap(MapItemSavedData data, double x, double z) {
        int scale = 1 << data.scale;
        int px = Mth.floor((x - data.centerX) / scale) + 64;
        int pz = Mth.floor((z - data.centerZ) / scale) + 64;
        return px >= 0 && pz >= 0 && px < 128 && pz < 128;
    }

    /** The source's target decorations, the explorer target ("+") first. */
    private static List<Map.Entry<String, MapDecorations.Entry>> targets(ItemStack source) {
        List<Map.Entry<String, MapDecorations.Entry>> targets = new ArrayList<>();
        MapDecorations decorations = source.get(DataComponents.MAP_DECORATIONS);
        if (decorations == null) {
            return targets;
        }
        for (Map.Entry<String, MapDecorations.Entry> entry : decorations.decorations().entrySet()) {
            if (!isTarget(entry.getValue().type())) {
                continue;
            }
            if (entry.getKey().equals(EXPLORER_TARGET_KEY)) {
                targets.addFirst(entry);
            } else {
                targets.add(entry);
            }
        }
        return targets;
    }

    private static boolean isTarget(Holder<MapDecorationType> type) {
        return !type.is(MapDecorationTypes.PLAYER.unwrapKey().orElseThrow())
                && !type.is(MapDecorationTypes.PLAYER_OFF_MAP.unwrapKey().orElseThrow())
                && !type.is(MapDecorationTypes.PLAYER_OFF_LIMITS.unwrapKey().orElseThrow())
                && !type.is(MapDecorationTypes.FRAME.unwrapKey().orElseThrow());
    }

    /**
     * What the compass is named after: the source's custom name when it is an explorer map/chart
     * pointing at this target, else the trail guide chart's destination, else the marker's icon.
     */
    private static Component destinationName(ItemStack source, Map.Entry<String, MapDecorations.Entry> target) {
        boolean explorerTarget = target.getKey().equals(EXPLORER_TARGET_KEY);
        Component customName = source.get(DataComponents.CUSTOM_NAME);
        if (explorerTarget && customName != null) {
            return customName;
        }
        Component itemName = source.get(DataComponents.ITEM_NAME);
        if (explorerTarget && itemName != null
                && itemName.getContents() instanceof TranslatableContents translatable
                && translatable.getKey().startsWith(GUIDE_CHART_PREFIX)) {
            String destination = translatable.getKey().substring(GUIDE_CHART_PREFIX.length());
            return Component.translatableWithFallback(DESTINATION_PREFIX + destination, prettify(destination));
        }
        String type = target.getValue().type().unwrapKey().map(key -> key.identifier().getPath()).orElse("target");
        return Component.translatableWithFallback(DESTINATION_PREFIX + type, prettify(type));
    }

    private static String prettify(String id) {
        StringBuilder name = new StringBuilder();
        for (String word : id.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return name.toString();
    }

    /** Top slot: vanilla's maps except the Atlas (a fixed picture), plus pointed compasses. */
    public static final class SourceSlot extends Slot {
        private final Slot vanilla;

        public SourceSlot(Slot vanilla, Container container) {
            super(container, vanilla.getContainerSlot(), vanilla.x, vanilla.y);
            this.vanilla = vanilla;
            this.index = vanilla.index;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return this.vanilla.mayPlace(stack) && !stack.is(ModItems.ATLAS.get()) || isCompassSource(stack);
        }
    }

    /** Bottom slot: vanilla's items plus this feature's recipients. */
    public static final class RecipientSlot extends Slot {
        private final Slot vanilla;

        public RecipientSlot(Slot vanilla, Container container) {
            super(container, vanilla.getContainerSlot(), vanilla.x, vanilla.y);
            this.vanilla = vanilla;
            this.index = vanilla.index;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return this.vanilla.mayPlace(stack) || isRecipient(stack);
        }
    }

    /** Result slot: keeps the source map when a target copy is taken; vanilla recipes are delegated untouched. */
    public static final class ResultSlot extends Slot {
        private final Slot vanilla;
        private final AbstractContainerMenu menu;
        private final ContainerLevelAccess access;
        private long lastSoundTime;

        public ResultSlot(Slot vanilla, Container resultContainer, AbstractContainerMenu menu, ContainerLevelAccess access) {
            super(resultContainer, vanilla.getContainerSlot(), vanilla.x, vanilla.y);
            this.vanilla = vanilla;
            this.menu = menu;
            this.access = access;
            this.index = vanilla.index;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(Player player, ItemStack carried) {
            ItemStack additional = this.menu.slots.get(1).getItem();
            if (additional.isEmpty() || isVanillaAdditional(additional)) {
                this.vanilla.onTake(player, carried);
                return;
            }
            // Only the recipient is used up; the source map stays in the top slot.
            this.menu.slots.get(1).remove(1);
            carried.getItem().onCraftedBy(carried, player);
            this.access.execute((level, pos) -> {
                long gameTime = level.getGameTime();
                if (this.lastSoundTime != gameTime) {
                    level.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
                    this.lastSoundTime = gameTime;
                }
            });
            super.onTake(player, carried);
        }

        @Override
        public ItemStack safeClone(Player player) {
            ItemStack result = super.safeClone(player);
            result.getItem().onCraftedBy(result, player);
            return result;
        }
    }
}
