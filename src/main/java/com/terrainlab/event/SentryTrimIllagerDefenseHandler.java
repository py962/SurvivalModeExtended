package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimPatterns;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.List;

/**
 * Sentry armor trim: while any armor piece carries the vanilla Sentry trim, illagers
 * (AbstractIllager, Ravager and Vex; Witches excluded) do not target the player, and nearby
 * illagers turn on whoever damages the player.
 * <p>
 * Targeting is blocked by cancelling {@link LivingChangeTargetEvent}, which only stops new
 * targeting: an illager already targeting the player when the trim is equipped keeps that
 * target until it changes for another reason.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class SentryTrimIllagerDefenseHandler {

    private static final double RALLY_RADIUS = 24.0; // blocks

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** The illager faction; also used by CelestialEventHandler to exclude it from Peace. */
    static boolean isIllager(Entity entity) {
        return entity instanceof AbstractIllager || entity instanceof Ravager || entity instanceof Vex;
    }

    /** Also used by VillagerBehaviorHandler for the Sentry-trim reputation check. */
    static boolean isWearingSentryTrim(Player player) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            ArmorTrim trim = stack.get(DataComponents.TRIM);
            if (trim != null && trim.pattern().is(TrimPatterns.SENTRY)) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onIllagerChangeTarget(LivingChangeTargetEvent event) {
        if (!isIllager(event.getEntity())) {
            return;
        }

        if (event.getNewAboutToBeSetTarget() instanceof Player player && isWearingSentryTrim(player)) {
            event.setCanceled(true);
            if (event.getEntity() instanceof net.minecraft.world.entity.monster.illager.Pillager
                    && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                com.terrainlab.advancement.ModAdvancements.award(serverPlayer, "sentry_pass");
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!isWearingSentryTrim(player)) {
            return;
        }

        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof LivingEntity attacker)) {
            return;
        }

        List<Mob> nearbyIllagers = level.getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(RALLY_RADIUS),
                SentryTrimIllagerDefenseHandler::isIllager);

        for (Mob illager : nearbyIllagers) {
            illager.setTarget(attacker);
        }
    }
}
