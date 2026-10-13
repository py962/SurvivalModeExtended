package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.entity.ElephantEntity;
import com.terrainlab.entity.HouseboatEntity;
import com.terrainlab.registry.ModAttachments;
import com.terrainlab.util.SafeSpotFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.InstrumentComponent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Goat horns summon the player's nearest owned animal of a horn-specific type:
 * <pre>
 *   ponder -> Wolf        seek -> Donkey      feel -> Horse
 *   sing   -> Camel       admire -> Elephant  call -> Nautilus
 *   dream  -> Strider     yearn -> Happy Ghast
 * </pre>
 * Camels, elephants, striders and happy ghasts are owned via the {@code MOUNT_OWNER} attachment
 * (see {@link MountOwnershipHandler}). Busy, leashed or ridden animals are summoned anyway. A happy
 * ghast towing a houseboat brings the boat along, with the ghast parked at hover height above it.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class GoatHornSummonHandler {

    private static final boolean DEBUG_LOGGING = false;

    /** Must match HappyGhastMixin.HOVER_HEIGHT. */
    private static final double GHAST_HOVER_HEIGHT = 6.0;

    private static final Map<String, Class<? extends Mob>> HORN_TO_ANIMAL = Map.of(
            "ponder_goat_horn", Wolf.class,
            "sing_goat_horn", Camel.class,
            "seek_goat_horn", Donkey.class,
            "feel_goat_horn", Horse.class,
            "admire_goat_horn", ElephantEntity.class,
            "call_goat_horn", net.minecraft.world.entity.animal.nautilus.Nautilus.class,
            "yearn_goat_horn", HappyGhast.class,
            "dream_goat_horn", Strider.class
    );

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.GOAT_HORN)) return;

        InstrumentComponent component = stack.get(DataComponents.INSTRUMENT);
        if (component == null) return;
        Holder<Instrument> instrument = component.instrument();

        String key = instrument.unwrapKey().map(k -> k.identifier().getPath()).orElse(null);
        Class<? extends Mob> targetType = key != null ? HORN_TO_ANIMAL.get(key) : null;
        if (targetType == null) return;

        Player player = event.getEntity();
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        Mob nearest = findNearestOwnedAnimal(level, player, targetType);
        if (nearest == null) {
            log("horn={} player={} -> no owned {} found in loaded chunks",
                    key, player.getScoreboardName(), targetType.getSimpleName());
            return;
        }

        log("horn={} player={} -> summoning {} at ({},{},{}) [riders={} leashed={}]",
                key, player.getScoreboardName(), nearest.getType().toShortString(),
                round(nearest.getX()), round(nearest.getY()), round(nearest.getZ()),
                nearest.getPassengers().size(), nearest.isLeashed());

        if (nearest instanceof HappyGhast ghast) {
            summonGhast(level, player, ghast);
        } else {
            BlockPos safe = SafeSpotFinder.findNearestSafeSpot(level, player.blockPosition(), 4);
            if (safe != null) {
                nearest.teleportTo(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
                log("  moved to safe spot {}", safe);
            } else {
                nearest.teleportTo(player.getX(), player.getY(), player.getZ());
                log("  no safe spot - moved onto player position");
            }
        }

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            com.terrainlab.advancement.ModAdvancements.award(serverPlayer, "goat_horn_summon");
        }

        // Summoning resets bed-anchor - re-claims on the owner's next sleep.
        if (nearest instanceof Wolf || nearest instanceof Cat) {
            nearest.setData(ModAttachments.ANCHOR_BED_POS.get(), Optional.empty());
            log("  cleared ANCHOR_BED_POS");
        }
    }

    private static void summonGhast(ServerLevel level, Player player, HappyGhast ghast) {
        HouseboatEntity boat = getLeashedBoat(ghast);

        // Player is already riding this ghast / its boat - nothing to bring.
        Entity playerRoot = player.getRootVehicle();
        if (playerRoot == ghast || (boat != null && playerRoot == boat)) {
            log("  player already aboard ghast/boat - nothing to move");
            return;
        }

        if (boat == null) {
            Vec3 spot = findClearSpot(level, ghast, player.position(), 3, 8, 3, 0.0);
            if (spot == null) {
                spot = player.position().add(0.0, 3.0, 0.0);
                log("  no clear hover spot beside player - hovering 3 above player");
            } else {
                log("  hover spot beside player at ({},{},{})",
                        round(spot.x), round(spot.y), round(spot.z));
            }
            ghast.teleportTo(spot.x, spot.y, spot.z);
            return;
        }

        // Towing: find a spot where both the boat and the ghast above it are clear. The ghast is
        // never placed independently or HappyGhastMixin would drag the boat.
        Vec3 boatSpot = findClearBoatSpot(level, ghast, boat, player.position());
        if (boatSpot == null) {
            boatSpot = player.position();
            log("  no clear boat+ghast spot beside player - boat placed on player position");
        } else {
            log("  boat spot beside player at ({},{},{})",
                    round(boatSpot.x), round(boatSpot.y), round(boatSpot.z));
        }

        // Boat first (its passengers ride along), then the ghast above it.
        boat.teleportTo(boatSpot.x, boatSpot.y, boatSpot.z);
        ghast.teleportTo(boatSpot.x, boatSpot.y + GHAST_HOVER_HEIGHT, boatSpot.z);
        log("  boat passengers={} ghast riders={}", boat.getPassengers().size(),
                ghast.getPassengers().size());
    }

    private static HouseboatEntity getLeashedBoat(HappyGhast ghast) {
        for (Leashable l : Leashable.leashableLeashedTo(ghast)) {
            if (l instanceof HouseboatEntity houseboat) return houseboat;
        }
        return null;
    }

    private static Vec3 findClearBoatSpot(ServerLevel level, HappyGhast ghast,
                                          HouseboatEntity boat, Vec3 origin) {
        AABB boatBox = boat.getBoundingBox();
        AABB ghastBox = ghast.getBoundingBox();
        Vec3 boatCur = boat.position();
        Vec3 ghastCur = ghast.position();

        for (int r = 4; r <= 10; r++) {
            for (int k = 0; k < 8; k++) {
                double ang = k * (Math.PI / 4.0);
                double px = origin.x + Math.round(Math.cos(ang) * r);
                double pz = origin.z + Math.round(Math.sin(ang) * r);
                for (int dy = 0; dy <= 2; dy++) {
                    double py = origin.y + dy;
                    AABB b = boatBox.move(px - boatCur.x, py - boatCur.y, pz - boatCur.z);
                    if (!level.noCollision(boat, b)) continue;
                    AABB g = ghastBox.move(px - ghastCur.x,
                            py + GHAST_HOVER_HEIGHT - ghastCur.y, pz - ghastCur.z);
                    if (!level.noCollision(ghast, g)) continue;
                    return new Vec3(px, py, pz);
                }
            }
        }
        return null;
    }

    /** Ring search for a collision-free position for a single entity. */
    private static Vec3 findClearSpot(ServerLevel level, Entity entity, Vec3 origin,
                                      int minR, int maxR, int maxUp, double yBase) {
        AABB box = entity.getBoundingBox();
        Vec3 cur = entity.position();
        for (int r = minR; r <= maxR; r++) {
            for (int k = 0; k < 8; k++) {
                double ang = k * (Math.PI / 4.0);
                double px = origin.x + Math.round(Math.cos(ang) * r);
                double pz = origin.z + Math.round(Math.sin(ang) * r);
                for (int dy = 0; dy <= maxUp; dy++) {
                    double py = origin.y + yBase + dy;
                    AABB moved = box.move(px - cur.x, py - cur.y, pz - cur.z);
                    if (level.noCollision(entity, moved)) return new Vec3(px, py, pz);
                }
            }
        }
        return null;
    }

    private static Mob findNearestOwnedAnimal(ServerLevel level, Player player, Class<? extends Mob> type) {
        List<? extends Mob> candidates = level.getEntitiesOfClass(
                type, player.getBoundingBox().inflate(20000.0),
                // Camel.class also matches elephants; the camel horn must not summon those.
                mob -> isOwnedByPlayer(mob, player)
                        && (type != Camel.class || !(mob instanceof ElephantEntity)));

        Mob nearest = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Mob candidate : candidates) {
            double distSq = candidate.distanceToSqr(player);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private static boolean isOwnedByPlayer(Mob mob, Player player) {
        if (mob instanceof TamableAnimal tamable) {
            return tamable.isTame() && tamable.isOwnedBy(player);
        }
        // Camels and elephants have no vanilla owner; camel-vs-elephant is split in findNearestOwnedAnimal.
        if (mob instanceof Camel) {
            return isMountOwnedBy(mob, player);
        }
        if (mob instanceof Strider || mob instanceof HappyGhast) {
            return isMountOwnedBy(mob, player);
        }
        if (mob instanceof AbstractHorse horse) {
            return horse.isTamed()
                    && horse.getOwnerReference() != null
                    && horse.getOwnerReference().getUUID().equals(player.getUUID());
        }
        if (mob instanceof net.minecraft.world.entity.animal.nautilus.Nautilus nautilus) {
            return nautilus.isTame()
                    && nautilus.getOwnerReference() != null
                    && nautilus.getOwnerReference().getUUID().equals(player.getUUID());
        }
        return false;
    }

    private static boolean isMountOwnedBy(Mob mob, Player player) {
        Optional<UUID> owner = mob.getData(ModAttachments.MOUNT_OWNER.get());
        return owner.isPresent() && owner.get().equals(player.getUUID());
    }

    private static void log(String msg, Object... args) {
        // if (DEBUG_LOGGING) TerrainLab.LOGGER.info("[HORN_SUMMON] " + msg, args);
    }

    private static double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
