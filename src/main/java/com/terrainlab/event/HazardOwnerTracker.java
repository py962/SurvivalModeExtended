package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.data.HazardOwnerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Records who placed lava sources (from a bucket) and campfires, and works out which player is
 * responsible when a penalised mob is hurt or killed by them. Lava placed by a dispenser belongs to
 * whoever placed the dispenser ({@code DispenserLavaOwnerMixin}). Lava, campfires and dispensers placed
 * before this was added have no owner. Flowing lava is traced to the nearest owned lava source within
 * {@link #LAVA_SEARCH_RADIUS} blocks.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class HazardOwnerTracker {

    private static final int LAVA_SEARCH_RADIUS = 8;
    /** How long a mob that left the lava or campfire still counts as burned by it (burning to death). */
    private static final long ATTRIBUTION_TICKS = 200L;
    private static final int MAX_REMEMBERED = 2048;

    private record Attribution(UUID owner, long gameTime) {
    }

    /** Mob UUID -> player whose lava/campfire last hurt it. Server thread only. */
    private static final Map<UUID, Attribution> LAST_HAZARD_OWNER = new HashMap<>();

    private HazardOwnerTracker() {
    }

    /** Damage from lava or a campfire (the blocks this tracks). */
    public static boolean isHazardDamage(DamageSource source) {
        return source.is(DamageTypes.LAVA) || source.is(DamageTypes.CAMPFIRE) || source.is(DamageTypes.IN_FIRE);
    }

    /** Hazard damage, or burning after leaving it. */
    public static boolean isHazardDeath(DamageSource source) {
        return isHazardDamage(source) || source.is(DamageTypes.ON_FIRE);
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getPlacedBlock().getBlock() instanceof CampfireBlock) {
            HazardOwnerData.get(level).setOwner(event.getPos(), player.getUUID());
        } else if (event.getPlacedBlock().getBlock() instanceof DispenserBlock) {
            HazardOwnerData.get(level).setDispenserOwner(event.getPos(), player.getUUID());
        }
    }

    /** Called by DispenserLavaOwnerMixin when a dispenser has just placed a lava source at {@code target}. */
    public static void onDispenserPlacedLava(ServerLevel level, BlockPos dispenser, BlockPos target) {
        HazardOwnerData data = HazardOwnerData.get(level);
        UUID owner = data.getDispenserOwner(dispenser);
        if (owner != null) {
            data.setOwner(target, owner);
        }
    }

    public static boolean isLavaSourceAt(ServerLevel level, BlockPos pos) {
        return isLavaSource(level, pos);
    }

    /** Lava from a bucket: records the positions that became lava sources this tick. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)
                || !event.getItemStack().is(Items.LAVA_BUCKET)) {
            return;
        }
        BlockPos clicked = event.getPos().immutable();
        Map<BlockPos, Boolean> wasSource = new HashMap<>();
        wasSource.put(clicked, isLavaSource(level, clicked));
        for (Direction direction : Direction.values()) {
            BlockPos pos = clicked.relative(direction);
            wasSource.put(pos, isLavaSource(level, pos));
        }
        UUID owner = player.getUUID();
        MinecraftServer server = level.getServer();
        server.schedule(new TickTask(server.getTickCount(), () -> {
            HazardOwnerData data = HazardOwnerData.get(level);
            wasSource.forEach((pos, before) -> {
                if (!before && isLavaSource(level, pos)) {
                    data.setOwner(pos, owner);
                }
            });
        }));
    }

    /** Remembers whose lava or campfire is hurting a penalised mob, for when it burns to death later. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)
                || !isHazardDamage(event.getSource())
                || !MobExperienceHandler.isPenalised(victim)) {
            return;
        }
        Attribution known = LAST_HAZARD_OWNER.get(victim.getUUID());
        if (known != null && level.getGameTime() - known.gameTime() < 20L) {
            return; // looked up within the last second
        }
        UUID owner = findOwnerAt(level, victim);
        if (owner != null) {
            if (LAST_HAZARD_OWNER.size() >= MAX_REMEMBERED) {
                prune(level.getGameTime());
            }
            LAST_HAZARD_OWNER.put(victim.getUUID(), new Attribution(owner, level.getGameTime()));
        }
    }

    /** The player whose lava or campfire killed this mob, if any. Forgets the mob afterwards. */
    public static UUID responsibleForDeath(ServerLevel level, LivingEntity victim) {
        Attribution remembered = LAST_HAZARD_OWNER.remove(victim.getUUID());
        if (remembered != null && level.getGameTime() - remembered.gameTime() <= ATTRIBUTION_TICKS) {
            return remembered.owner();
        }
        return findOwnerAt(level, victim);
    }

    private static UUID findOwnerAt(ServerLevel level, LivingEntity victim) {
        HazardOwnerData data = HazardOwnerData.get(level);
        BlockPos feet = victim.blockPosition();

        for (BlockPos pos : new BlockPos[]{feet, feet.below()}) {
            UUID owner = data.getOwner(pos);
            if (owner != null) {
                if (level.getBlockState(pos).getBlock() instanceof CampfireBlock || isLavaSource(level, pos)) {
                    return owner;
                }
                data.removeOwner(pos);
            }
        }

        // Lava: nearest owned source within range (covers lava flowing from it).
        UUID best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int r = LAVA_SEARCH_RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    pos.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
                    UUID owner = data.getOwner(pos);
                    if (owner == null) {
                        continue;
                    }
                    if (!isLavaSource(level, pos)) {
                        if (!(level.getBlockState(pos).getBlock() instanceof CampfireBlock)) {
                            data.removeOwner(pos.immutable());
                        }
                        continue;
                    }
                    double distance = dx * dx + dy * dy + dz * dz;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = owner;
                    }
                }
            }
        }
        return best;
    }

    private static boolean isLavaSource(ServerLevel level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        return fluid.is(FluidTags.LAVA) && fluid.isSource();
    }

    private static void prune(long now) {
        Iterator<Attribution> it = LAST_HAZARD_OWNER.values().iterator();
        while (it.hasNext()) {
            if (now - it.next().gameTime() > ATTRIBUTION_TICKS) {
                it.remove();
            }
        }
    }
}
