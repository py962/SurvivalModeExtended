package com.terrainlab.advancement;

import com.terrainlab.TerrainLab;
import com.terrainlab.compat.CuriosCompat;
import com.terrainlab.data.SeasonSavedData;
import com.terrainlab.entity.ElephantEntity;
import com.terrainlab.entity.HouseboatEntity;
import com.terrainlab.item.CelestialAmuletItem;
import com.terrainlab.registry.ModItems;
import com.terrainlab.season.SeasonManager;
import com.terrainlab.season.SolarTime;
import com.terrainlab.village.VillageTier;
import com.terrainlab.village.VillageTierTransitionEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Awards advancements that depend on a state rather than a single action
 * (event names are listed in ModAdvancements).
 * <ul>
 *   <li><b>The River Wild</b> - while a player is in a boat on water, the
 *       boat's height is remembered for RAPIDS_WINDOW ticks. A drop of at
 *       least RAPIDS_STEP blocks inside that window with the boat in water
 *       the whole time is a run down rapids; a rise of the same is a run up
 *       them. Both are needed. Bubble columns don't count, and nor does
 *       falling off a cliff (the boat leaves the water).</li>
 *   <li><b>Turn of the Year</b> - the current season, checked every
 *       SEASON_CHECK ticks.</li>
 *   <li><b>Wrapped Up Warm / Star-Touched</b> - fur cloak in the Curios
 *       "body" slot, any celestial amulet in "necklace".</li>
 *   <li><b>Rocked to Sleep / Asleep on the March</b> - waking up in the
 *       morning (solar day) next to a houseboat / elephant.</li>
 *   <li><b>Bespoke Defence</b> - crafting any terrainlab:*_shield.</li>
 *   <li><b>Moving Up / Master Planner</b> - a village tier-up credits every
 *       player within VILLAGE_CREDIT_RADIUS of any member; reaching tier 4
 *       also credits that village's (majority) villager type.</li>
 * </ul>
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class AdvancementWatcher {

    private static final int RAPIDS_WINDOW = 40;        // ticks
    private static final double RAPIDS_STEP = 0.9;      // blocks
    private static final int SEASON_CHECK = 200;        // ticks
    private static final int CURIO_CHECK = 40;          // ticks
    private static final double VILLAGE_CREDIT_RADIUS = 96.0; // blocks

    private static final Map<UUID, ArrayDeque<Double>> BOAT_HEIGHTS = new HashMap<>();

    private AdvancementWatcher() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        tickRapids(player);

        long time = player.level().getGameTime();
        if ((time + player.getId()) % SEASON_CHECK == 0) {
            checkSeason(player);
        }
        if ((time + player.getId()) % CURIO_CHECK == 0) {
            checkCurios(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        BOAT_HEIGHTS.remove(event.getEntity().getUUID());
    }

    private static void tickRapids(ServerPlayer player) {
        Entity vehicle = player.getVehicle();
        if (!(vehicle instanceof AbstractBoat boat)
                || !boat.isInWater()
                || boat.level().getBlockState(boat.blockPosition()).is(Blocks.BUBBLE_COLUMN)) {
            BOAT_HEIGHTS.remove(player.getUUID());
            return;
        }

        ArrayDeque<Double> heights = BOAT_HEIGHTS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        double y = boat.getY();
        heights.addLast(y);
        if (heights.size() > RAPIDS_WINDOW) {
            heights.removeFirst();
        }

        double highest = Double.NEGATIVE_INFINITY;
        double lowest = Double.POSITIVE_INFINITY;
        for (double h : heights) {
            highest = Math.max(highest, h);
            lowest = Math.min(lowest, h);
        }

        // Down: well below the window's highest point. Up: well above its lowest.
        if (highest - y >= RAPIDS_STEP) {
            ModAdvancements.award(player, "river_wild_down");
            heights.clear();
        } else if (y - lowest >= RAPIDS_STEP) {
            ModAdvancements.award(player, "river_wild_up");
            heights.clear();
        }
    }

    private static void checkSeason(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        SeasonSavedData data = SeasonSavedData.get(level);
        data.generateOffsetIfAbsent(level.getSeed());
        String season = SeasonManager.getCurrentVisualSeason(level.getGameTime(), data.getSeasonalOffset())
                .name().toLowerCase(Locale.ROOT);
        ModAdvancements.award(player, "season_" + season);
    }

    private static void checkCurios(ServerPlayer player) {
        if (CuriosCompat.isWorn(player, "body", ModItems.FUR_CLOAK.get())) {
            ModAdvancements.award(player, "fur_cloak");
        }
        if (CuriosCompat.findStack(player, "necklace",
                stack -> stack.getItem() instanceof CelestialAmuletItem).isPresent()) {
            ModAdvancements.award(player, "amulet");
        }
    }

    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || !SolarTime.isSolarDay(level)) {
            return;   // only waking in the morning counts
        }

        AABB around = player.getBoundingBox();
        if (!level.getEntitiesOfClass(HouseboatEntity.class, around.inflate(3.0)).isEmpty()) {
            ModAdvancements.award(player, "houseboat_night");
        }
        // The howdah bed sits up on the elephant's back.
        if (!level.getEntitiesOfClass(ElephantEntity.class, around.inflate(6.0)).isEmpty()) {
            ModAdvancements.award(player, "howdah_night");
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack crafted = event.getCrafting();
        Identifier id = BuiltInRegistries.ITEM.getKey(crafted.getItem());
        if (TerrainLab.MODID.equals(id.getNamespace()) && id.getPath().endsWith("_shield")) {
            ModAdvancements.award(player, "specialist_shield");
        }
    }

    @SubscribeEvent
    public static void onVillageTier(VillageTierTransitionEvent event) {
        if (!event.isLevelUp() || event.getMembers().isEmpty()) {
            return;
        }
        ServerLevel level = event.getLevel();
        boolean tier4 = event.getNewTier() == VillageTier.TIER_4_TOWN;
        String type = tier4 ? majorityType(event) : null;
        double r2 = VILLAGE_CREDIT_RADIUS * VILLAGE_CREDIT_RADIUS;

        for (ServerPlayer player : level.players()) {
            boolean near = false;
            for (Villager villager : event.getMembers()) {
                if (villager.distanceToSqr(player) <= r2) {
                    near = true;
                    break;
                }
            }
            if (!near) {
                continue;
            }
            ModAdvancements.award(player, "village_upgrade");
            if (type != null) {
                ModAdvancements.award(player, "village_tier4_" + type);
            }
        }
    }

    private static String majorityType(VillageTierTransitionEvent event) {
        Map<String, Integer> counts = new HashMap<>();
        for (Villager villager : event.getMembers()) {
            String type = villager.getVillagerData().type().unwrapKey()
                    .map(key -> key.identifier().getPath())
                    .orElse("plains");
            counts.merge(type, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("plains");
    }
}
