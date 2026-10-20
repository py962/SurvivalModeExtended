package com.terrainlab.advancement;

import com.terrainlab.TerrainLab;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * TerrainLab advancements (the "Survival Mode Extended" tab,
 * data/terrainlab/advancement/survival/*.json).
 * <p>
 * Most use the custom terrainlab:event trigger, fired by name from the code
 * that performs the action. Event names and their sources:
 * <pre>
 *   river_wild_down / river_wild_up   AdvancementWatcher (boat on rapids)
 *   season_winter/spring/summer/autumn AdvancementWatcher
 *   fur_cloak, amulet                 AdvancementWatcher (Curios slots)
 *   houseboat_night, howdah_night     AdvancementWatcher (wake-up)
 *   specialist_shield                 AdvancementWatcher (item crafted)
 *   village_upgrade, village_tier4_&lt;type&gt;  AdvancementWatcher (tier event)
 *   borrowing_dream                   BorrowingSleepMixin
 *   goat_horn_summon                  GoatHornSummonHandler
 *   village_song                      VillageSongService
 *   enderman_sequence                 ModEndermanHandler
 *   guide_relic, quest_&lt;guide&gt;        AbstractTrailTraderEntity
 *   sentry_pass                       SentryTrimIllagerDefenseHandler
 *   specialist_arrow                  FletchingTableMenu
 *   houseboat_ghast                   ModLeashHandler
 *   spyglass_map                      SpyglassMapFillHandler
 *   redstone_portal                   RedstonePortalBlock
 *   codex_enchant                     AncientCodexMenu
 *   celestial_forge                   CelestialForgeBlock
 *   replicator                        ReplicatorMenu
 *   conduit_&lt;variant&gt;                 CelestialConduitBlockEntity /
 *                                     ConduitBlockEntityCelestialMixin (breath)
 *   beacon_weather                    BeaconMenuWeatherRodMixin
 *   piglin_cure                       PiglinWardUtil
 *   pacifist                          EndReturnChallenges (End -&gt; Overworld, zero kills)
 *   nomad                             EndReturnChallenges (End -&gt; Overworld, no chunk slept in twice)
 *   survivor                          EndReturnChallenges (End -&gt; Overworld, never Creative, not hardcore)
 *   ultimate_survivor                 EndReturnChallenges (End -&gt; Overworld, never Creative, hardcore)
 * </pre>
 * The golems use vanilla's minecraft:summoned_entity (ModGraniteGolemHandler
 * fires it for the granite golem).
 */
public final class ModAdvancements {

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, TerrainLab.MODID);

    public static final DeferredHolder<CriterionTrigger<?>, TerrainLabEventTrigger> EVENT =
            TRIGGERS.register("event", TerrainLabEventTrigger::new);

    private ModAdvancements() {
    }

    public static void register(IEventBus modEventBus) {
        TRIGGERS.register(modEventBus);
    }

    /** Fire terrainlab:event for one player. */
    public static void award(ServerPlayer player, String event) {
        EVENT.get().trigger(player, event);
    }

    /** Fire terrainlab:event for every player within radius of a point. */
    public static void awardNearby(ServerLevel level, Vec3 centre, double radius, String event) {
        double r2 = radius * radius;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.distanceToSqr(centre) <= r2) {
                award(player, event);
            }
        }
    }
}
