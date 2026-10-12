package com.terrainlab.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side record of recent passive shield auto-blocks, fed by ShieldAutoBlockPacket.
 * For {@link #FLASH_TICKS} after an auto-block, {@link #isFlashing} is true for that entity
 * and hand, which swaps the shield to its blocking model and raises the arm into the
 * BLOCK pose. Keyed by entity network id; each value holds the flash end time per hand
 * ([0] = main hand, [1] = off hand).
 */
public final class ClientShieldAutoBlockState {

    /** How long the raised pose is shown after a passive auto-block, in ticks. */
    public static final int FLASH_TICKS = 10;

    private static final Map<Integer, long[]> FLASH_UNTIL = new ConcurrentHashMap<>();

    private ClientShieldAutoBlockState() {
    }

    public static void flash(int entityId, boolean mainHand) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        long until = level.getGameTime() + FLASH_TICKS;
        long[] hands = FLASH_UNTIL.computeIfAbsent(entityId, k -> new long[]{Long.MIN_VALUE, Long.MIN_VALUE});
        hands[mainHand ? 0 : 1] = until;
    }

    public static boolean isFlashing(LivingEntity entity, InteractionHand hand) {
        long[] hands = FLASH_UNTIL.get(entity.getId());
        if (hands == null) {
            return false;
        }
        long now = entity.level().getGameTime();
        if (now >= hands[0] && now >= hands[1]) {
            FLASH_UNTIL.remove(entity.getId());
            return false;
        }
        return now < hands[hand == InteractionHand.MAIN_HAND ? 0 : 1];
    }
}
