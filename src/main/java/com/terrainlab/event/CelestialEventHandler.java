
package com.terrainlab.event;

import com.terrainlab.celestial.BreathConduitHooks;
import com.terrainlab.celestial.CelestialConduitRegistry;
import com.terrainlab.celestial.CelestialDebug;
import com.terrainlab.celestial.CelestialEffects;
import com.terrainlab.celestial.CelestialVariant;
import com.terrainlab.celestial.DeathRespawnManager;
import com.terrainlab.celestial.EyeOreSense;
import com.terrainlab.compat.CuriosCompat;
import com.terrainlab.item.CelestialAmuletItem;
import com.terrainlab.registry.ModAttachments;
import com.terrainlab.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageTypes;
import com.terrainlab.network.DeathOptionsPacket;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import com.terrainlab.entity.AbstractTrailTraderEntity;

import javax.annotation.Nullable;
import java.util.Optional;
import net.minecraft.world.entity.NeutralMob;
import net.neoforged.neoforge.event.entity.living.EndermanAngerEvent;
import java.util.UUID;

public class CelestialEventHandler {

    private static final int STAGGER = 10;
    private static final double PEACE_RETARGET_RADIUS = 64.0D;

    /**
     * Mobs Peace protects against: every Enemy mob except illagers, which are
     * handled by the Sentry armour trim. Vexes remain covered.
     */
    private static boolean isPeaceAffected(LivingEntity entity) {
        return entity instanceof Mob
                && entity instanceof Enemy
                && (entity instanceof net.minecraft.world.entity.monster.Vex
                || !SentryTrimIllagerDefenseHandler.isIllager(entity));
    }

    /**
     * Recognises villagers and Trail Trader guide entities.
     *
     */

    private static boolean isPeaceProtectedNpc(LivingEntity entity) {
        return entity instanceof Villager
                || entity instanceof AbstractTrailTraderEntity;
    }

    private static boolean isProvoked(Mob mob) {
        return mob.getData(ModAttachments.PEACE_PROVOKED.get());
    }

    /** Clears existing hostile targets against a protected player. */
    private static void dropPeaceTargets(ServerPlayer player) {
        for (Mob mob : player.level().getEntitiesOfClass(
                Mob.class,
                player.getBoundingBox().inflate(PEACE_RETARGET_RADIUS),
                m -> isPeaceAffected(m) && !isProvoked(m)
        )) {
            boolean goalTarget = mob.getTarget() == player;
            boolean brainTarget = mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                    && mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == player;
            // Piglins and brutes hold a grudge in ANGRY_AT and re-acquire the target from it.
            boolean brainAnger = mob.getBrain().hasMemoryValue(MemoryModuleType.ANGRY_AT)
                    && player.getUUID().equals(mob.getBrain().getMemory(MemoryModuleType.ANGRY_AT).orElse(null));
            // Endermen (and other neutral mobs) keep a persistent anger target.
            boolean neutralAnger = mob instanceof NeutralMob neutral
                    && neutral.getPersistentAngerTarget() != null
                    && player.getUUID().equals(neutral.getPersistentAngerTarget().getUUID());

            if (goalTarget) {
                mob.setTarget(null);
            }
            if (brainTarget) {
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
            if (brainAnger) {
                mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
            }
            if (neutralAnger) {
                ((NeutralMob) mob).stopBeingAngry();
            }
            goalTarget |= brainAnger || neutralAnger;

            if ((goalTarget || brainTarget) && CelestialDebug.VERBOSE) {
                CelestialDebug.log(
                        "PEACE: {} dropped its chase of {}",
                        mob.getType().getDescriptionId(),
                        player.getName().getString()
                );
            }
        }
    }

    /**
     * Called by the Peace conduit on its ordinary conduit cadence.
     * Removes existing hostile targets against villagers and guides inside
     * the conduit sphere. New targeting is blocked by onChangeTarget.
     */
    public static void clearPeaceNpcTargets(
            ServerLevel level,
            BlockPos center,
            int radius
    ) {
        if (radius <= 0) {
            return;
        }

        double centerX = center.getX() + 0.5D;
        double centerY = center.getY() + 0.5D;
        double centerZ = center.getZ() + 0.5D;
        double radiusSquared = (double) radius * radius;

        AABB area = new AABB(center).inflate(radius);

        for (Mob mob : level.getEntitiesOfClass(
                Mob.class,
                area,
                candidate -> isPeaceAffected(candidate) && !isProvoked(candidate)
        )) {
            LivingEntity target = mob.getTarget();

            if (target == null || !isPeaceProtectedNpc(target)) {
                continue;
            }

            // The NPC itself must be within the conduit sphere.
            if (target.distanceToSqr(centerX, centerY, centerZ) > radiusSquared) {
                continue;
            }

            boolean goalTarget = mob.getTarget() == target;
            boolean brainTarget = mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                    && mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == target;

            if (goalTarget) {
                mob.setTarget(null);
            }
            if (brainTarget) {
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }

            if ((goalTarget || brainTarget) && CelestialDebug.VERBOSE) {
                CelestialDebug.log(
                        "PEACE: {} dropped target {} at {}",
                        mob.getType().getDescriptionId(),
                        target.getType().getDescriptionId(),
                        target.blockPosition()
                );
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.tickCount % STAGGER == 0) {
            DeathRespawnManager.tick(player);
        }

        if (player.tickCount % STAGGER != 0) {
            return;
        }

        BreathConduitHooks.tickPlayer(player);

        boolean luckConduitNearby = false;
        boolean peaceConduitNearby = false;
        boolean endConduitNearby = false;

        if (player.level() instanceof ServerLevel nearbyCheckLevel) {
            CelestialConduitRegistry registry = CelestialConduitRegistry.get(nearbyCheckLevel);
            BlockPos playerPos = player.blockPosition();

            luckConduitNearby = registry.hasActiveNearby(
                    CelestialVariant.LUCK,
                    nearbyCheckLevel.dimension(),
                    playerPos
            );
            peaceConduitNearby = registry.hasActiveNearby(
                    CelestialVariant.PEACE,
                    nearbyCheckLevel.dimension(),
                    playerPos
            );
            endConduitNearby = registry.hasActiveNearby(
                    CelestialVariant.END,
                    nearbyCheckLevel.dimension(),
                    playerPos
            );
        }

        if (CelestialDebug.VERBOSE
                && (player.getData(ModAttachments.LUCK_CONDUIT_NEARBY.get()) != luckConduitNearby
                || player.getData(ModAttachments.PEACE_CONDUIT_NEARBY.get()) != peaceConduitNearby
                || player.getData(ModAttachments.END_CONDUIT_NEARBY.get()) != endConduitNearby)) {
            CelestialDebug.log(
                    "{} proximity flags changed at {}: luck={} peace={} end={}",
                    player.getName().getString(),
                    player.blockPosition(),
                    luckConduitNearby,
                    peaceConduitNearby,
                    endConduitNearby
            );
        }

        if (player.level() instanceof ServerLevel eyeLevel
                && CelestialConduitRegistry.get(eyeLevel).hasActiveNearby(
                CelestialVariant.EYE,
                eyeLevel.dimension(),
                player.blockPosition()
        )) {
            EyeOreSense.scanForPlayer(eyeLevel, player);
        }

        player.setData(ModAttachments.LUCK_CONDUIT_NEARBY.get(), luckConduitNearby);
        player.setData(ModAttachments.PEACE_CONDUIT_NEARBY.get(), peaceConduitNearby);
        player.setData(ModAttachments.END_CONDUIT_NEARBY.get(), endConduitNearby);

        Optional<ItemStack> amuletStack = CuriosCompat.findStack(
                player,
                "necklace",
                stack -> stack.getItem() instanceof CelestialAmuletItem
        );

        CelestialVariant wornVariant = null;
        boolean pairedConduitActive = false;

        if (amuletStack.isPresent()) {
            ItemStack stack = amuletStack.get();
            wornVariant = ((CelestialAmuletItem) stack.getItem()).variant();
            UUID pairId = stack.get(ModDataComponents.CELESTIAL_PAIR_ID.get());

            if (player.level() instanceof ServerLevel serverLevel) {
                pairedConduitActive = CelestialConduitRegistry.isAmuletActive(
                        player,
                        serverLevel,
                        pairId,
                        wornVariant
                );
            }

            Boolean currentGlint = stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);

            if (!Boolean.valueOf(pairedConduitActive).equals(currentGlint)) {
                stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, pairedConduitActive);
            }

            if (pairedConduitActive) {
                CelestialEffects.tickAmulet(player, wornVariant);

                if (wornVariant == CelestialVariant.LUCK) {
                    player.setData(ModAttachments.LUCK_AMULET_ACTIVE.get(), true);
                }
            }
        }

        if (wornVariant != CelestialVariant.LUCK || !pairedConduitActive) {
            player.setData(ModAttachments.LUCK_AMULET_ACTIVE.get(), false);
        }

        CelestialEffects.updateAmuletMarkers(
                player,
                pairedConduitActive ? wornVariant : null
        );

        if (peaceConduitNearby
                || (wornVariant == CelestialVariant.PEACE && pairedConduitActive)) {
            dropPeaceTargets(player);
        }

        boolean inEnd = player.level().dimension() == Level.END;

        boolean wantsFloat = (
                (wornVariant == CelestialVariant.END && pairedConduitActive)
                        || endConduitNearby
        ) && inEnd;

        updateEndFloat(player, wantsFloat);

        boolean wantsFlight = inEnd
                && player.level() instanceof ServerLevel flightLevel
                && CelestialConduitRegistry.get(flightLevel).hasFullPowerNearby(
                CelestialVariant.END,
                flightLevel.dimension(),
                player.blockPosition()
        );

        updateEndFlight(player, wantsFlight);

        player.setData(
                ModAttachments.END_AMULET_ACTIVE.get(),
                wornVariant == CelestialVariant.END && pairedConduitActive
        );
    }

    private static final String END_FLOAT_TAG = "terrainlab_end_float";
    private static final String END_FLIGHT_TAG = "terrainlab_end_flight";

    private static void updateEndFloat(ServerPlayer player, boolean wantsFloat) {
        boolean applied = player.entityTags().contains(END_FLOAT_TAG);
        boolean exempt = player.getAbilities().instabuild || player.isSpectator();

        if (wantsFloat && applied && !exempt && !player.isNoGravity()) {
            player.setNoGravity(true);

            if (CelestialDebug.VERBOSE) {
                CelestialDebug.log("END: float RE-APPLIED for {}", player.getName().getString());
            }
        } else if (wantsFloat && !applied && !exempt) {
            player.setNoGravity(true);
            player.addTag(END_FLOAT_TAG);

            if (CelestialDebug.VERBOSE) {
                CelestialDebug.log("END: float ON for {}", player.getName().getString());
            }
        } else if (applied && (!wantsFloat || exempt)) {
            player.setNoGravity(false);
            player.removeTag(END_FLOAT_TAG);

            if (CelestialDebug.VERBOSE) {
                CelestialDebug.log(
                        "END: float OFF for {} (wants={}, exempt={})",
                        player.getName().getString(),
                        wantsFloat,
                        exempt
                );
            }
        }
    }

    private static void updateEndFlight(ServerPlayer player, boolean wantsFlight) {
        boolean applied = player.entityTags().contains(END_FLIGHT_TAG);
        boolean exempt = player.getAbilities().instabuild || player.isSpectator();

        if (exempt) {
            if (applied) {
                player.removeTag(END_FLIGHT_TAG);

                if (CelestialDebug.VERBOSE) {
                    CelestialDebug.log(
                            "END: flight tag cleared for {} (creative/spectator)",
                            player.getName().getString()
                    );
                }
            }
            return;
        }

        if (wantsFlight && applied && !player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();

            if (CelestialDebug.VERBOSE) {
                CelestialDebug.log("END: flight RE-GRANTED to {}", player.getName().getString());
            }
        } else if (wantsFlight && !applied) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
            player.addTag(END_FLIGHT_TAG);

            if (CelestialDebug.VERBOSE) {
                CelestialDebug.log("END: flight GRANTED to {}", player.getName().getString());
            }
        } else if (!wantsFlight && applied) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.removeTag(END_FLIGHT_TAG);

            if (CelestialDebug.VERBOSE) {
                CelestialDebug.log("END: flight REVOKED from {}", player.getName().getString());
            }
        }
    }

    /**
     * Peace targeting:
     * - Players are protected by an active nearby Peace conduit or their active
     *   paired Peace amulet.
     * - Villagers and Trail Trader guides are protected when they are within an
     *   active Peace conduit radius.
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!isPeaceAffected(event.getEntity())) {
            return;
        }

        LivingEntity monster = event.getEntity();

        if (isProvoked((Mob) monster)) {
            return;
        }

        LivingEntity target = event.getNewAboutToBeSetTarget();

        if (target == null) {
            return;
        }

        if (isPeaceProtectedNpc(target)) {
            if (target.level() instanceof ServerLevel serverLevel
                    && CelestialConduitRegistry.get(serverLevel).hasActiveNearby(
                    CelestialVariant.PEACE,
                    serverLevel.dimension(),
                    target.blockPosition()
            )) {
                event.setCanceled(true);

                if (CelestialDebug.VERBOSE) {
                    CelestialDebug.log(
                            "PEACE: {} stopped from targeting protected NPC {} at {}",
                            monster.getType().getDescriptionId(),
                            target.getType().getDescriptionId(),
                            target.blockPosition()
                    );
                }
            }
            return;
        }

        if (!(target instanceof ServerPlayer player)) {
            return;
        }

        if (player.getData(ModAttachments.PEACE_CONDUIT_NEARBY.get())) {
            event.setCanceled(true);

            if (CelestialDebug.VERBOSE) {
                CelestialDebug.log(
                        "PEACE: {} stopped from targeting {} (Peace conduit in range)",
                        monster.getType().getDescriptionId(),
                        player.getName().getString()
                );
            }
            return;
        }

        Optional<ItemStack> amuletStack = CuriosCompat.findStack(
                player,
                "necklace",
                stack -> stack.getItem() instanceof CelestialAmuletItem amulet
                        && amulet.variant() == CelestialVariant.PEACE
        );

        if (amuletStack.isEmpty()) {
            return;
        }

        UUID pairId = amuletStack.get().get(ModDataComponents.CELESTIAL_PAIR_ID.get());

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (CelestialConduitRegistry.isAmuletActive(
                player,
                serverLevel,
                pairId,
                CelestialVariant.PEACE
        )) {
            event.setCanceled(true);

            if (CelestialDebug.VERBOSE) {
                CelestialDebug.log(
                        "PEACE: {} stopped from targeting {} (Peace amulet active)",
                        monster.getType().getDescriptionId(),
                        player.getName().getString()
                );
            }
        }
    }

    /**
     * Looking at an enderman does not anger it while the player is under Peace (conduit in range,
     * or an active paired Peace amulet). Hitting it still does, as with every Peace mob.
     */
    @SubscribeEvent
    public static void onEnderManAnger(EndermanAngerEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && !isProvoked(event.getEntity())
                && isPeaceProtectedPlayer(player)) {
            event.setCanceled(true);
        }
    }

    /** Whether Peace protects this player now: a Peace conduit in range or an active paired amulet. */
    private static boolean isPeaceProtectedPlayer(ServerPlayer player) {
        if (player.getData(ModAttachments.PEACE_CONDUIT_NEARBY.get())) {
            return true;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        Optional<ItemStack> amulet = CuriosCompat.findStack(
                player,
                "necklace",
                stack -> stack.getItem() instanceof CelestialAmuletItem item
                        && item.variant() == CelestialVariant.PEACE
        );
        return amulet.isPresent() && CelestialConduitRegistry.isAmuletActive(
                player,
                serverLevel,
                amulet.get().get(ModDataComponents.CELESTIAL_PAIR_ID.get()),
                CelestialVariant.PEACE
        );
    }

    @SubscribeEvent
    public static void onMonsterStruck(LivingDamageEvent.Post event) {
        if (!isPeaceAffected(event.getEntity())) {
            return;
        }

        LivingEntity monster = event.getEntity();

        if (event.getSource().getEntity() instanceof ServerPlayer) {
            monster.setData(ModAttachments.PEACE_PROVOKED.get(), true);
        }
    }

    @SubscribeEvent
    public static void onHostileSpawnCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getEntityType().getCategory() != MobCategory.MONSTER) {
            return;
        }

        ServerLevelAccessor levelAccessor = event.getLevel();

        if (!(levelAccessor instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos pos = event.getPos();

        if (CelestialConduitRegistry.get(serverLevel).hasActiveNearby(
                CelestialVariant.PEACE,
                serverLevel.dimension(),
                pos
        )) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);

            if (CelestialDebug.VERBOSE) {
                CelestialDebug.log(
                        "PEACE: blocked {} spawn at {}",
                        event.getEntityType().getDescriptionId(),
                        pos
                );
            }
        }
    }

    /** Which Death-conduit options a death at the player's current position qualifies for. */
    private record DeathEligibility(boolean conduitEligible, boolean amuletEligible) {
    }

    private static DeathEligibility computeDeathEligibility(ServerPlayer player, ServerLevel serverLevel) {
        boolean hasActiveDeathAmulet = CuriosCompat.findStack(
                        player,
                        "necklace",
                        stack -> stack.getItem() instanceof CelestialAmuletItem amulet
                                && amulet.variant() == CelestialVariant.DEATH
                )
                .filter(stack -> {
                    UUID pairId = stack.get(ModDataComponents.CELESTIAL_PAIR_ID.get());

                    return CelestialConduitRegistry.isAmuletActive(
                            player,
                            serverLevel,
                            pairId,
                            CelestialVariant.DEATH
                    );
                })
                .isPresent();

        boolean diedNearActiveDeathConduit = CelestialConduitRegistry.get(serverLevel)
                .hasActiveNearby(
                        CelestialVariant.DEATH,
                        serverLevel.dimension(),
                        player.blockPosition()
                );

        boolean anyActiveDeathConduit = CelestialConduitRegistry.get(serverLevel)
                .findNearestActive(
                        CelestialVariant.DEATH,
                        serverLevel.dimension(),
                        player.blockPosition()
                )
                .isPresent();

        if (CelestialDebug.VERBOSE) {
            CelestialDebug.log(
                    "DEATH: {} died at {} - anyActiveDeathConduit={} amulet={} diedInRange={}",
                    player.getName().getString(),
                    player.blockPosition(),
                    anyActiveDeathConduit,
                    hasActiveDeathAmulet,
                    diedNearActiveDeathConduit
            );
        }

        // Respawning at a Death conduit: from anywhere with an active Death amulet, otherwise only
        // when the death happened within an active Death conduit's range.
        return new DeathEligibility(
                hasActiveDeathAmulet ? anyActiveDeathConduit : diedNearActiveDeathConduit,
                hasActiveDeathAmulet || diedNearActiveDeathConduit
        );
    }

    /**
     * Hardcore worlds: a death that would have qualified for a Death-conduit respawn is prevented
     * instead, at the cost of 100 levels. Runs first so that, when cancelled, no other listener
     * (including {@link #onPlayerDeath}) treats it as a death; LivingDeathEvent fires at the top of
     * ServerPlayer.die, before the death message, drops and XP. A totem of undying is checked by
     * vanilla before die() and so still takes precedence.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHardcorePlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel serverLevel)
                || !serverLevel.getServer().isHardcore()
                || player.isSpectator()
                // /kill is final, as it is for a totem.
                || event.getSource().is(DamageTypes.GENERIC_KILL)) {
            return;
        }

        DeathEligibility eligibility = computeDeathEligibility(player, serverLevel);
        if (DeathRespawnManager.tryHardcoreRescue(player, eligibility.conduitEligible())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // Hardcore has no respawn choice: the Death conduit acts before death instead
        // (onHardcorePlayerDeath), so neither option is offered there.
        DeathEligibility eligibility = serverLevel.getServer().isHardcore()
                ? new DeathEligibility(false, false)
                : computeDeathEligibility(player, serverLevel);

        DeathRespawnManager.captureDeath(player, eligibility.conduitEligible(), eligibility.amuletEligible());
        // Tell the client which death-screen buttons to show. Sent before vanilla's
        // ClientboundPlayerCombatKillPacket, which opens the DeathScreen.
        PacketDistributor.sendToPlayer(player,
                new DeathOptionsPacket(eligibility.conduitEligible(), eligibility.amuletEligible()));
    }

    @SubscribeEvent
    public static void onExperienceDrop(
            net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (DeathRespawnManager.isIntercepting(player.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRespawnPosition(
            net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent event
    ) {
        DeathRespawnManager.onRespawnPosition(event);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered()
                || !(event.getEntity() instanceof ServerPlayer newPlayer)) {
            return;
        }

        DeathRespawnManager.onRespawn(newPlayer);
    }

    @SubscribeEvent
    public static void onGetEnchantmentLevel(GetEnchantmentLevelEvent event) {
        // Only real stacks can be held by a player (26.x also queries item templates).
        if (!(event.getStack() instanceof ItemStack heldStack)) {
            return;
        }
        ServerPlayer player = findHoldingPlayer(heldStack);

        if (player == null) {
            return;
        }

        if (CelestialDebug.VERBOSE) {
            CelestialDebug.log(
                    "LUCK: enchant boost applied to {}'s held {} (amulet={} conduitInRange={})",
                    player.getName().getString(),
                    heldStack.getItem(),
                    player.getData(ModAttachments.LUCK_AMULET_ACTIVE.get()),
                    player.getData(ModAttachments.LUCK_CONDUIT_NEARBY.get())
            );
        }

        var enchantmentRegistry = player.level()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT);

        grantIfApplicable(event, enchantmentRegistry.getOrThrow(Enchantments.LOOTING));
        grantIfApplicable(event, enchantmentRegistry.getOrThrow(Enchantments.FORTUNE));
        grantIfApplicable(event, enchantmentRegistry.getOrThrow(Enchantments.LUCK_OF_THE_SEA));
    }

    @Nullable
    private static ServerPlayer findHoldingPlayer(ItemStack stack) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        if (server == null || !server.isSameThread() || stack.isEmpty()) {
            return null;
        }

        ServerPlayer equalMatch = null;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.getData(ModAttachments.LUCK_AMULET_ACTIVE.get())
                    && !player.getData(ModAttachments.LUCK_CONDUIT_NEARBY.get())) {
                continue;
            }

            if (player.getMainHandItem() == stack || player.getOffhandItem() == stack) {
                return player;
            }

            // Fortune can be queried using a copy of the main-hand stack.
            if (equalMatch == null && ItemStack.matches(player.getMainHandItem(), stack)) {
                equalMatch = player;
            }
        }

        return equalMatch;
    }

    private static void grantIfApplicable(
            GetEnchantmentLevelEvent event,
            Holder<Enchantment> enchantment
    ) {
        if (event.isTargetting(enchantment)) {
            int current = event.getEnchantments().getLevel(enchantment);
            event.getEnchantments().set(enchantment, Math.min(current + 1, 255));
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DeathRespawnManager.onLogout(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DeathRespawnManager.onLogin(player);
        }
    }

    @SubscribeEvent
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!event.getEffectInstance().is(MobEffects.LEVITATION)) {
            return;
        }

        if (player.getData(ModAttachments.END_AMULET_ACTIVE.get())
                || player.getData(ModAttachments.END_CONDUIT_NEARBY.get())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);

            if (CelestialDebug.VERBOSE) {
                CelestialDebug.log("END: blocked Levitation on {}", player.getName().getString());
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            EyeOreSense.cleanup(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        EyeOreSense.onEntityJoin(event);
    }
}