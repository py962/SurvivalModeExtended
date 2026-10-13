package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.data.HazardOwnerData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.EntityTypes;

/**
 * Mob-kill XP rebalance: flat boss rewards, an XP penalty for killing passive/neutral mobs and baby
 * animals, and no XP from PvP kills of low-level players.
 * <p>
 * The penalty is charged when the mob dies, to the player who hit it within vanilla's kill-credit
 * window or, failing that, to whoever placed the lava or campfire that killed it (including burning
 * to death after leaving it; see {@link HazardOwnerTracker}). Self-defence kills are penalised too.
 * Players who are offline when charged lose the XP when they next log in.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class MobExperienceHandler {

    private MobExperienceHandler() {
    }

    private static final int PASSIVE_KILL_XP_PENALTY = 3;
    private static final int PVP_XP_LEVEL_THRESHOLD = 30;

    /**
     * Flat XP for bosses, replacing vanilla's values. The dragon's value is a total spread across the
     * several drop events of its death animation.
     */
    private static final Map<EntityType<?>, Integer> STRAIGHT_OVERRIDE = Map.of(
            EntityTypes.ENDER_DRAGON, 500,
            EntityTypes.WITHER, 500
    );

    /**
     * Passive and neutral mobs whose kill costs XP. Baby animals are handled separately via
     * {@code instanceof Animal && isBaby()}, which excludes baby hostile mobs.
     */
    private static final Set<EntityType<?>> PENALTY_BUCKET = Set.of(
            // Passive
            EntityTypes.AXOLOTL, EntityTypes.CAMEL, EntityTypes.CAT, EntityTypes.CHICKEN,
            EntityTypes.COD, EntityTypes.COW, EntityTypes.DONKEY, EntityTypes.FOX,
            EntityTypes.FROG, EntityTypes.GLOW_SQUID, EntityTypes.HORSE, EntityTypes.MOOSHROOM,
            EntityTypes.OCELOT, EntityTypes.PARROT, EntityTypes.PIG, EntityTypes.PUFFERFISH,
            EntityTypes.RABBIT, EntityTypes.SALMON, EntityTypes.SHEEP, EntityTypes.SKELETON_HORSE,
            EntityTypes.SNIFFER, EntityTypes.SQUID, EntityTypes.STRIDER, EntityTypes.TROPICAL_FISH,
            EntityTypes.TURTLE, EntityTypes.ZOMBIE_HORSE, EntityTypes.ALLAY, EntityTypes.BAT,
            EntityTypes.SNOW_GOLEM, EntityTypes.TADPOLE, EntityTypes.VILLAGER, EntityTypes.WANDERING_TRADER,
            EntityTypes.COPPER_GOLEM,
            // Neutral
            EntityTypes.IRON_GOLEM, EntityTypes.BEE, EntityTypes.DOLPHIN, EntityTypes.GOAT,
            EntityTypes.LLAMA, EntityTypes.PANDA, EntityTypes.POLAR_BEAR, EntityTypes.TRADER_LLAMA,
            EntityTypes.WOLF
    );

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        LivingEntity victim = event.getEntity();
        EntityType<?> type = victim.getType();

        Integer straightOverride = STRAIGHT_OVERRIDE.get(type);
        if (straightOverride != null) {
            if (victim instanceof EnderDragon dragon) {
                event.setDroppedExperience(Math.round(
                        event.getOriginalExperience() * (float) straightOverride / vanillaDragonTotal(dragon)));
            } else {
                event.setDroppedExperience(straightOverride);
            }
            return;
        }

        if (isPenalised(victim)) {
            // Orb drops cannot be negative; the penalty itself is charged on death (onLivingDeath).
            event.setDroppedExperience(0);
        }
    }

    /** Passive or neutral mob on the penalty list, or any baby animal. */
    public static boolean isPenalised(LivingEntity entity) {
        return PENALTY_BUCKET.contains(entity.getType()) || (entity instanceof Animal && entity.isBaby());
    }

    /**
     * Charges the kill penalty. Runs for every death, unlike the XP drop event, which vanilla skips
     * for babies and when the mobDrops game rule is off.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || !isPenalised(victim)) {
            return;
        }

        UUID responsible = null;
        if (victim.getLastHurtByPlayerMemoryTime() > 0 && victim.getLastHurtByPlayer() instanceof ServerPlayer killer) {
            responsible = killer.getUUID();
        } else if (HazardOwnerTracker.isHazardDeath(event.getSource())) {
            responsible = HazardOwnerTracker.responsibleForDeath(level, victim);
        }
        if (responsible == null) {
            return;
        }

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(responsible);
        if (player != null) {
            applyPenalty(player, PASSIVE_KILL_XP_PENALTY);
        } else {
            HazardOwnerData.get(level.getServer().overworld()).addPendingPenalty(responsible, PASSIVE_KILL_XP_PENALTY);
        }
    }

    /** Penalties incurred while offline. */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            int owed = HazardOwnerData.get(player.level().getServer().overworld()).takePendingPenalty(player.getUUID());
            if (owed > 0) {
                applyPenalty(player, owed);
            }
        }
    }

    /** Deducts XP with a low, dull pickup sound (heard only by this player) and a puff of smoke. */
    private static void applyPenalty(ServerPlayer player, int amount) {
        player.giveExperiencePoints(-amount);
        player.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EXPERIENCE_ORB_PICKUP),
                SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(),
                0.35F, 0.5F, player.getRandom().nextLong()));
        ((ServerLevel) player.level()).sendParticles(ParticleTypes.SMOKE,
                player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.25, 0.4, 0.25, 0.01);
    }

    /** Total XP vanilla awards across all of the dragon's death drops (matches EnderDragon.tickDeath). */
    private static int vanillaDragonTotal(EnderDragon dragon) {
        EnderDragonFight fight = dragon.getDragonFight();
        return fight != null && !fight.hasPreviouslyKilledDragon() ? 12000 : 500;
    }

    /** PvP kills of players at or below the level threshold drop no XP; above it the vanilla drop stands. */
    @SubscribeEvent
    public static void onPvpExperienceDrop(LivingExperienceDropEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        if (!(event.getAttackingPlayer() instanceof ServerPlayer)) {
            return; // not a PvP kill

        }
        if (victim.experienceLevel <= PVP_XP_LEVEL_THRESHOLD) {
            event.setDroppedExperience(0);
        }
    }
}
