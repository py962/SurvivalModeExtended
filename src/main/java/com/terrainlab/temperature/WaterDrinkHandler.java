package com.terrainlab.temperature;

import com.terrainlab.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Empty-hand drinking from small bodies of water: holding use on water for 1s
 * applies a cooling push, then a continuous 1s release is required before the
 * next drink. Oceans and other large bodies are excluded by size, not biome.
 */
public final class WaterDrinkHandler {

    private static final double REACH_DISTANCE = 5.0;
    private static final long HOLD_TICKS_REQUIRED = 20;
    private static final long RELEASE_TICKS_REQUIRED = 20;

    // Connected water blocks explored before a body counts as large/ocean-like.
    private static final int WATER_BODY_CAP = 200;

    private WaterDrinkHandler() {
    }

    public static void tick(ServerPlayer player) {
        long currentTick = player.level().getGameTime();
        boolean keyDown = player.getData(ModAttachments.PLAYER_WANTS_WATER_DRINK.get());
        WaterDrinkState state = player.getData(ModAttachments.WATER_DRINK_STATE.get());

        switch (state.phase) {
            case IDLE -> {
                if (keyDown && meetsConditions(player)) {
                    state.phase = WaterDrinkState.Phase.HOLDING;
                    state.holdStartTick = currentTick;
                }
            }
            case HOLDING -> {
                if (!keyDown || !meetsConditions(player)) {
                    state.phase = WaterDrinkState.Phase.IDLE;
                } else if (currentTick - state.holdStartTick >= HOLD_TICKS_REQUIRED) {
                    PlayerInternalTemperature.applyInstantPush(player, PlayerTemperatureConstants.COOLING_FOOD_PUSH);
                    state.phase = WaterDrinkState.Phase.COOLDOWN;
                    state.releaseStartTick = keyDown ? -1 : currentTick;
                }
            }
            case COOLDOWN -> {
                if (keyDown) {
                    state.releaseStartTick = -1;
                } else {
                    if (state.releaseStartTick == -1) {
                        state.releaseStartTick = currentTick;
                    } else if (currentTick - state.releaseStartTick >= RELEASE_TICKS_REQUIRED) {
                        state.phase = WaterDrinkState.Phase.IDLE;
                    }
                }
            }
        }
    }

    private static boolean meetsConditions(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty()) return false;

        HitResult hit = player.pick(REACH_DISTANCE, 1.0F, true);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() == HitResult.Type.MISS) {
            return false;
        }

        BlockPos pos = blockHit.getBlockPos();
        if (!player.level().getFluidState(pos).is(FluidTags.WATER)) {
            return false;
        }

        return !isLargeBodyOfWater(player.level(), pos);
    }

    /**
     * True if a 6-directional flood fill from origin reaches WATER_BODY_CAP
     * connected water blocks. Shared with the Leather Flask refill.
     */
    public static boolean isLargeBodyOfWater(Level level, BlockPos origin) {
        java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> frontier = new java.util.ArrayDeque<>();
        frontier.add(origin);
        visited.add(origin);

        while (!frontier.isEmpty()) {
            if (visited.size() >= WATER_BODY_CAP) {
                return true;
            }

            BlockPos current = frontier.poll();
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos next = current.relative(dir);
                if (visited.contains(next)) continue;
                if (!level.getFluidState(next).is(FluidTags.WATER)) continue;

                visited.add(next);
                frontier.add(next);

                if (visited.size() >= WATER_BODY_CAP) {
                    return true;
                }
            }
        }

        return false;
    }
}
