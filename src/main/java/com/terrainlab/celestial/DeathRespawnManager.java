package com.terrainlab.celestial;

import com.terrainlab.network.DeathPickingStatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side state for the Death conduit/amulet respawn flow. Every player death is
 * intercepted and its XP held back, then returned on respawn according to the choice made:
 * <ul>
 *   <li>Respawn at the nearest active Death conduit: all XP returned.</li>
 *   <li>Plain respawn (bed, anchor or world spawn): 50% returned.</li>
 *   <li>Pick a spot (Death amulet active, or died within an active Death conduit's range):
 *   a time-limited spectator window starting at the death location, confirmed by
 *   left-click; 50% returned.</li>
 * </ul>
 * A choice that was not available at the moment of death falls back to the plain 50% outcome.
 */
public final class DeathRespawnManager {

    /** Spectator picking window (ticks, ~30 s). */
    private static final int PICK_WINDOW_TICKS = 600;

    private enum Choice { NONE, CONDUIT, PICK }

    private record DeathLoc(ResourceKey<Level> dimension, BlockPos pos) {
    }

    /** Captured at death: which better-than-baseline options were available, so onRespawn can
     * reject a button clicked with nothing behind it. */
    private record DeathCapture(DeathLoc loc, boolean conduitEligible, boolean amuletEligible) {
    }

    private static final class PickingSession {
        final DeathLoc normalRespawn;
        final GameType originalGameType;
        final long deadlineGameTime;
        final int xpAmount;
        volatile boolean confirmRequested;

        PickingSession(DeathLoc normalRespawn, GameType originalGameType, long deadlineGameTime, int xpAmount) {
            this.normalRespawn = normalRespawn;
            this.originalGameType = originalGameType;
            this.deadlineGameTime = deadlineGameTime;
            this.xpAmount = xpAmount;
        }
    }

    private static final Map<UUID, Integer> pendingXp = new HashMap<>();
    private static final Map<UUID, DeathCapture> pendingCaptures = new HashMap<>();
    private static final Map<UUID, Choice> pendingChoice = new HashMap<>();
    private static final Map<UUID, PickingSession> pickingSessions = new HashMap<>();
    /** Set when onRespawnPosition redirected the respawn to a Death conduit; consumed by onRespawn. */
    private static final Map<UUID, Boolean> conduitRespawnApplied = new HashMap<>();

    private DeathRespawnManager() {
    }

    private static net.minecraft.server.MinecraftServer serverOf(ServerPlayer player) {
        return ((ServerLevel) player.level()).getServer();
    }

    /** Called from LivingDeathEvent (before XP drops) for every player death: stashes the player's
     * XP, death location and option eligibility. */
    public static void captureDeath(ServerPlayer player, boolean conduitEligible, boolean amuletEligible) {
        UUID id = player.getUUID();
        // Spendable XP rather than totalExperience, which vanilla never lowers when levels are spent.
        pendingXp.put(id, com.terrainlab.util.XpMath.realTotalXp(player));
        pendingCaptures.put(id, new DeathCapture(
                new DeathLoc(player.level().dimension(), player.blockPosition().immutable()),
                conduitEligible, amuletEligible));
        pendingChoice.remove(id);

        // Also kept on the player, so a server stop on the death screen does not lose the XP.
        CompoundTag saved = new CompoundTag();
        saved.putInt("Xp", pendingXp.get(id));
        saved.putString("Dimension", player.level().dimension().identifier().toString());
        saved.putLong("Pos", player.blockPosition().asLong());
        persistedData(player).put(CAPTURE_MARKER, saved);
    }

    private static final String CAPTURE_MARKER = "terrainlab_death_capture";

    /** The part of the player's data that is copied to the respawned player. */
    private static CompoundTag persistedData(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG)
                .orElseGet(CompoundTag::new);
        root.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, persisted);
        return persisted;
    }

    public static boolean hasCapturedDeath(UUID playerId) {
        return pendingCaptures.containsKey(playerId);
    }

    public static void chooseConduitRespawn(UUID playerId) {
        DeathCapture capture = pendingCaptures.get(playerId);
        if (capture != null && !capture.conduitEligible()) {
            if (CelestialDebug.VERBOSE) CelestialDebug.log("DEATH: {} clicked 'Respawn at Death Conduit' but it was not available - ignored", playerId);
        } else if (capture != null) {
            pendingChoice.put(playerId, Choice.CONDUIT);
            if (CelestialDebug.VERBOSE) CelestialDebug.log("DEATH: {} chose 'Respawn at Death Conduit'", playerId);
        } else if (CelestialDebug.VERBOSE) {
            CelestialDebug.log("DEATH: {} clicked 'Respawn at Death Conduit' but no captured death - ignored", playerId);
        }
    }

    public static void choosePickLocation(UUID playerId) {
        DeathCapture capture = pendingCaptures.get(playerId);
        if (capture != null && !capture.amuletEligible()) {
            if (CelestialDebug.VERBOSE) CelestialDebug.log("DEATH: {} chose spectator picking but it was not available - ignored", playerId);
        } else if (capture != null) {
            pendingChoice.put(playerId, Choice.PICK);
            if (CelestialDebug.VERBOSE) CelestialDebug.log("DEATH: {} chose spectator picking", playerId);
        } else if (CelestialDebug.VERBOSE) {
            CelestialDebug.log("DEATH: {} chose spectator picking but no captured death - ignored", playerId);
        }
    }

    /**
     * PlayerRespawnPositionEvent handler: redirects the respawn transition to the nearest active
     * Death conduit when that option was chosen. Must run here rather than in PlayerEvent.Clone,
     * which fires before vanilla's own placement and would be overwritten.
     */
    public static void onRespawnPosition(net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        conduitRespawnApplied.remove(id);
        if (event.isFromEndFight()) return;

        DeathCapture capture = pendingCaptures.get(id);
        Choice choice = pendingChoice.get(id);
        if (capture == null) {
            if (CelestialDebug.VERBOSE) CelestialDebug.log("DEATH: respawn position for {} - no captured death, vanilla placement", player.getName().getString());
            return;
        }
        if (choice != Choice.CONDUIT) {
            if (CelestialDebug.VERBOSE) CelestialDebug.log("DEATH: respawn position for {} - choice {}, vanilla placement", player.getName().getString(), choice);
            return;
        }
        if (!capture.conduitEligible()) {
            if (CelestialDebug.VERBOSE) CelestialDebug.log("DEATH: respawn position for {} - chose conduit but none was active at death, vanilla placement", player.getName().getString());
            return;
        }

        Optional<ConduitSpot> spot = findConduitRespawnSpot(serverOf(player), capture.loc().dimension(), capture.loc().pos());
        if (spot.isEmpty()) {
            if (CelestialDebug.VERBOSE) CelestialDebug.log("DEATH: respawn position for {} - no usable active Death conduit any more, vanilla placement", player.getName().getString());
            return;
        }
        event.setTeleportTransition(new TeleportTransition(spot.get().level(), spot.get().pos(), Vec3.ZERO,
                player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
        conduitRespawnApplied.put(id, true);
        if (CelestialDebug.VERBOSE) CelestialDebug.log("DEATH: respawn position for {} set to Death conduit {} {} -> {}",
                player.getName().getString(), spot.get().level().dimension().identifier(), spot.get().conduitPos(), spot.get().pos());
    }

    /** Where a Death-conduit respawn (or hardcore rescue) puts the player. */
    public record ConduitSpot(ServerLevel level, Vec3 pos, BlockPos conduitPos) {
    }

    /**
     * The respawn spot above the active Death conduit nearest to {@code fromPos}, preferring
     * {@code fromDim} and falling back to other dimensions. Empty if none is active or its
     * dimension is not loaded.
     */
    public static Optional<ConduitSpot> findConduitRespawnSpot(MinecraftServer server, ResourceKey<Level> fromDim, BlockPos fromPos) {
        ServerLevel lookupLevel = server.getLevel(fromDim);
        if (lookupLevel == null) lookupLevel = server.overworld();
        var record = CelestialConduitRegistry.get(lookupLevel)
                .findNearestActive(CelestialVariant.DEATH, fromDim, fromPos);
        if (record.isEmpty()) {
            return Optional.empty();
        }
        ServerLevel destLevel = server.getLevel(record.get().dimension());
        if (destLevel == null) {
            if (CelestialDebug.VERBOSE) CelestialDebug.log("DEATH: Death conduit dimension {} not loaded",
                    record.get().dimension().identifier());
            return Optional.empty();
        }
        return Optional.of(new ConduitSpot(destLevel, safeSpotAbove(destLevel, record.get().pos()), record.get().pos()));
    }

    /** Experience levels a hardcore rescue costs. */
    public static final int HARDCORE_RESCUE_LEVEL_COST = 100;

    /**
     * Hardcore worlds only, called at the start of LivingDeathEvent: if the death would have
     * qualified for a Death-conduit respawn and the player has enough levels, prevents it, charges
     * {@link #HARDCORE_RESCUE_LEVEL_COST} levels and teleports the player to the conduit's respawn
     * spot. Returns true when the death was prevented (the caller cancels the event).
     */
    public static boolean tryHardcoreRescue(ServerPlayer player, boolean conduitEligible) {
        if (!conduitEligible || player.experienceLevel < HARDCORE_RESCUE_LEVEL_COST) {
            if (CelestialDebug.VERBOSE) CelestialDebug.log("DEATH: hardcore rescue for {} not possible - eligible={} levels={}",
                    player.getName().getString(), conduitEligible, player.experienceLevel);
            return false;
        }
        ServerLevel fromLevel = (ServerLevel) player.level();
        Optional<ConduitSpot> spot = findConduitRespawnSpot(serverOf(player), fromLevel.dimension(), player.blockPosition());
        if (spot.isEmpty()) {
            return false;
        }

        int levelsBefore = player.experienceLevel;
        player.giveExperienceLevels(-HARDCORE_RESCUE_LEVEL_COST);
        if (player.experienceLevel != levelsBefore - HARDCORE_RESCUE_LEVEL_COST) {
            // Another mod changed or cancelled the level change: put it back and let the death happen.
            player.setExperienceLevels(levelsBefore);
            return false;
        }

        Vec3 from = player.position();
        playRescueEffects(fromLevel, from, net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT);

        player.stopRiding();
        player.setHealth(player.getMaxHealth() / 2.0F);
        player.clearFire();
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);
        for (var effect : java.util.List.copyOf(player.getActiveEffects())) {
            if (effect.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
                player.removeEffect(effect.getEffect());
            }
        }

        ConduitSpot dest = spot.get();
        teleportPlayer(player, dest.level(), dest.pos(), player.getYRot(), player.getXRot());
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);
        // A few seconds of full invulnerability on arrival (set after the teleport, which may reset it).
        player.setInvulnerableTime(60);
        playRescueEffects(dest.level(), dest.pos(), net.minecraft.sounds.SoundEvents.RESPAWN_ANCHOR_SET_SPAWN);

        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.terrainlab.death.conduit_rescue")
                .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE), true);
        if (CelestialDebug.VERBOSE) CelestialDebug.log("DEATH: hardcore rescue - {} saved at {} {}, moved to Death conduit {} {} ({} -> {} levels)",
                player.getName().getString(), fromLevel.dimension().identifier(), BlockPos.containing(from),
                dest.level().dimension().identifier(), dest.conduitPos(), levelsBefore, player.experienceLevel);
        return true;
    }

    private static void playRescueEffects(ServerLevel level, Vec3 pos, net.minecraft.sounds.SoundEvent sound) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                pos.x, pos.y + 1.0, pos.z, 60, 0.4, 0.9, 0.4, 0.05);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                pos.x, pos.y + 1.0, pos.z, 80, 0.5, 1.0, 0.5, 0.6);
    }

    /** First spot from the frame's top (conduit y+3) upward with two non-colliding blocks;
     * falls back to y+3 if the next 24 blocks are all solid. */
    private static Vec3 safeSpotAbove(ServerLevel level, BlockPos conduit) {
        for (int dy = 3; dy < 27; dy++) {
            BlockPos feet = conduit.above(dy);
            if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                return Vec3.atBottomCenterOf(feet);
            }
        }
        return Vec3.atBottomCenterOf(conduit.above(3));
    }

    /** Left-click while picking; sent by CelestialDeathPickingClientHandler via DeathConfirmPickingPacket. */
    public static void requestConfirm(UUID playerId) {
        PickingSession session = pickingSessions.get(playerId);
        if (session != null) {
            session.confirmRequested = true;
        }
    }

    /** True for every captured death; LivingExperienceDropEvent cancels the natural XP drop when set. */
    public static boolean isIntercepting(UUID playerId) {
        return pendingCaptures.containsKey(playerId);
    }

    /** PlayerRespawnEvent handler (after the new player is placed): returns XP for the chosen
     * outcome and starts spectator picking if chosen. Conduit placement already happened in
     * {@link #onRespawnPosition}. */
    public static void onRespawn(ServerPlayer newPlayer) {
        UUID id = newPlayer.getUUID();
        DeathCapture capture = pendingCaptures.remove(id);
        Integer xp = pendingXp.remove(id);
        Choice choice = pendingChoice.remove(id);
        boolean conduitApplied = Boolean.TRUE.equals(conduitRespawnApplied.remove(id));

        CompoundTag saved = persistedData(newPlayer).getCompound(CAPTURE_MARKER).orElse(null);
        persistedData(newPlayer).remove(CAPTURE_MARKER);
        if (capture == null && saved != null) {
            // The server restarted while this player was dead: fall back to the plain outcome.
            Identifier dimId = Identifier.tryParse(saved.getStringOr("Dimension", ""));
            ResourceKey<Level> dim = dimId == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, dimId);
            capture = new DeathCapture(new DeathLoc(dim, BlockPos.of(saved.getLongOr("Pos", 0L))), false, false);
            xp = saved.getIntOr("Xp", 0);
            choice = Choice.NONE;
        }
        if (capture == null) {
            if (CelestialDebug.VERBOSE) CelestialDebug.log("DEATH: {} respawned - no captured death, nothing to do", newPlayer.getName().getString());
            return;
        }
        // Under keepInventory vanilla copies the old XP to the new player; reset it so the
        // hand-back below is not added on top.
        clearCarriedXp(newPlayer);

        DeathLoc deathLoc = capture.loc();
        int xpAmount = xp == null ? 0 : xp;
        if (choice == null) {
            choice = Choice.NONE;
        }
        // A choice only counts if it was available at the moment of death.
        if (choice == Choice.CONDUIT && !capture.conduitEligible()) {
            choice = Choice.NONE;
        } else if (choice == Choice.PICK && !capture.amuletEligible()) {
            choice = Choice.NONE;
        }

        if (CelestialDebug.VERBOSE) CelestialDebug.log("DEATH: {} respawned at {} {} - choice={} conduitEligible={} amuletEligible={} conduitApplied={} xp={}",
                newPlayer.getName().getString(), newPlayer.level().dimension().identifier(), newPlayer.blockPosition(),
                choice, capture.conduitEligible(), capture.amuletEligible(), conduitApplied, xpAmount);

        switch (choice) {
            case CONDUIT -> {
                if (conduitApplied) {
                    // Placement already done in onRespawnPosition: full XP back.
                    if (xpAmount > 0) {
                        newPlayer.giveExperiencePoints(xpAmount);
                    }
                    if (CelestialDebug.VERBOSE) CelestialDebug.log("DEATH: conduit respawn - returned all {} XP", xpAmount);
                } else {
                    // Conduit went inactive since death: baseline 50%.
                    grantBaselineHalf(newPlayer, xpAmount);
                    if (CelestialDebug.VERBOSE) CelestialDebug.log("DEATH: conduit respawn NOT applied - normal spawn, 50% XP");
                }
            }
            case PICK -> {
                ServerLevel deathLevel = serverOf(newPlayer).getLevel(deathLoc.dimension());
                if (deathLevel == null) {
                    grantBaselineHalf(newPlayer, xpAmount);
                    return;
                }
                DeathLoc normalRespawn = new DeathLoc(newPlayer.level().dimension(), newPlayer.blockPosition().immutable());
                GameType original = newPlayer.gameMode.getGameModeForPlayer();
                long deadline = deathLevel.getGameTime() + PICK_WINDOW_TICKS;
                pickingSessions.put(id, new PickingSession(normalRespawn, original, deadline, xpAmount));
                saveMarker(newPlayer, normalRespawn, original, xpAmount);

                teleportPlayer(newPlayer, deathLevel, Vec3.atBottomCenterOf(deathLoc.pos()).add(0, 1, 0),
                        newPlayer.getYRot(), newPlayer.getXRot());
                newPlayer.setGameMode(GameType.SPECTATOR);
                PacketDistributor.sendToPlayer(newPlayer, new DeathPickingStatePacket(true, PICK_WINDOW_TICKS));
                if (CelestialDebug.VERBOSE) CelestialDebug.log("DEATH: spectator picking started at {} {} (normal respawn was {})",
                        deathLoc.dimension().identifier(), deathLoc.pos(), normalRespawn.pos());
            }
            // Plain respawn, or an ineligible choice: 50% XP.
            case NONE -> {
                grantBaselineHalf(newPlayer, xpAmount);
                if (CelestialDebug.VERBOSE) CelestialDebug.log("DEATH: plain respawn - 50% XP");
            }
        }
    }

    private static void clearCarriedXp(ServerPlayer player) {
        player.setExperienceLevels(0);
        player.setExperiencePoints(0);
        player.totalExperience = 0;
    }

    /** Returns half the XP (rounded down) directly; the rest is lost. */
    private static void grantBaselineHalf(ServerPlayer player, int xpAmount) {
        int half = xpAmount / 2;
        if (half > 0) {
            player.giveExperiencePoints(half);
        }
    }

    /** Called every stagger tick: finalises an in-progress pick on confirm or timeout. */
    public static void tick(ServerPlayer player) {
        PickingSession session = pickingSessions.get(player.getUUID());
        if (session == null) {
            return;
        }
        long now = player.level().getGameTime();
        if (session.confirmRequested) {
            finalizePick(player, session, true);
        } else if (now >= session.deadlineGameTime) {
            finalizePick(player, session, false);
        } else {
            int remaining = (int) Math.max(0, session.deadlineGameTime - now);
            PacketDistributor.sendToPlayer(player, new DeathPickingStatePacket(true, remaining));
        }
    }

    private static void finalizePick(ServerPlayer player, PickingSession session, boolean confirmed) {
        pickingSessions.remove(player.getUUID());
        player.getPersistentData().remove(PICK_MARKER);

        if (confirmed) {
            // Exact confirmed spot, no clamping.
            player.setGameMode(session.originalGameType);
        } else {
            ServerLevel fallbackLevel = serverOf(player).getLevel(session.normalRespawn.dimension());
            if (fallbackLevel != null) {
                teleportPlayer(player, fallbackLevel, Vec3.atBottomCenterOf(session.normalRespawn.pos()),
                        player.getYRot(), player.getXRot());
            }
            player.setGameMode(session.originalGameType);
        }

        // Confirmed and timed-out picks both get the 50% rate; only the location differs.
        grantBaselineHalf(player, session.xpAmount);
        PacketDistributor.sendToPlayer(player, new DeathPickingStatePacket(false, 0));
    }

    /** Same-dimension teleportTo, otherwise a cross-dimension TeleportTransition. */
    private static void teleportPlayer(ServerPlayer player, ServerLevel destLevel, Vec3 pos, float yaw, float pitch) {
        if (player.level() == destLevel) {
            player.teleportTo(pos.x, pos.y, pos.z);
        } else {
            player.teleport(new TeleportTransition(destLevel, pos, Vec3.ZERO, yaw, pitch, TeleportTransition.DO_NOTHING));
        }
    }

    /** Persistent-data marker held while picking, so a crash mid-pick can be recovered at login. */
    private static final String PICK_MARKER = "terrainlab_death_picking";

    private static void saveMarker(ServerPlayer player, DeathLoc normalRespawn, GameType original, int xpAmount) {
        CompoundTag tag = new CompoundTag();
        tag.putString("GameMode", original.getName());
        tag.putString("Dimension", normalRespawn.dimension().identifier().toString());
        tag.putLong("Pos", normalRespawn.pos().asLong());
        tag.putInt("Xp", xpAmount);
        player.getPersistentData().put(PICK_MARKER, tag);
    }

    /** Logging out mid-picking counts as letting the timer run out: back to
     * the normal respawn point, original game mode, 50% XP. */
    public static void onLogout(ServerPlayer player) {
        PickingSession session = pickingSessions.get(player.getUUID());
        if (session != null) {
            if (CelestialDebug.VERBOSE) CelestialDebug.log("DEATH: {} logged out while picking - ending it", player.getName().getString());
            finalizePick(player, session, false);
        }
        // Not clear(): a player who logs out on the death screen still needs the captured death.
    }

    /** A marker still present at login means the server stopped without a clean logout;
     * finishes the pick as a timeout. */
    public static void onLogin(ServerPlayer player) {
        if (pickingSessions.containsKey(player.getUUID())) {
            return;
        }
        CompoundTag tag = player.getPersistentData().getCompound(PICK_MARKER).orElse(null);
        if (tag == null) {
            return;
        }
        player.getPersistentData().remove(PICK_MARKER);
        GameType original = GameType.byName(tag.getStringOr("GameMode", ""), GameType.SURVIVAL);
        Identifier dimId = Identifier.tryParse(tag.getStringOr("Dimension", ""));
        ServerLevel level = dimId == null ? null
                : serverOf(player).getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
        if (level == null) {
            level = serverOf(player).overworld();
        }
        BlockPos pos = BlockPos.of(tag.getLongOr("Pos", level.getRespawnData().pos().asLong()));
        teleportPlayer(player, level, Vec3.atBottomCenterOf(pos), player.getYRot(), player.getXRot());
        player.setGameMode(original);
        grantBaselineHalf(player, tag.getIntOr("Xp", 0));
        if (CelestialDebug.VERBOSE) CelestialDebug.log("DEATH: {} logged in mid-pick (crash?) - restored {} at {}",
                player.getName().getString(), original, pos);
    }

    public static void clear(UUID playerId) {
        pendingXp.remove(playerId);
        pendingCaptures.remove(playerId);
        pendingChoice.remove(playerId);
        pickingSessions.remove(playerId);
        conduitRespawnApplied.remove(playerId);
    }
}
