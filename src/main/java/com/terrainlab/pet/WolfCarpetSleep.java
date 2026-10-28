package com.terrainlab.pet;

import com.terrainlab.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.MossyCarpetBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * State of a tamed wolf sent to sleep on a carpet while its owner sleeps (see WolfCarpetSleepHandler).
 * Both attachments are transient: {@code WOLF_SLEEP_CARPET} is the claimed carpet (walking there or lying
 * on it), {@code WOLF_CARPET_ASLEEP} turns true once the wolf lies down and is synced for the pose.
 */
public final class WolfCarpetSleep {

    private WolfCarpetSleep() {}

    /** The carpet this wolf has claimed, if it is walking to it or sleeping on it. */
    public static Optional<BlockPos> carpet(Wolf wolf) {
        if (!wolf.hasData(ModAttachments.WOLF_SLEEP_CARPET.get())) return Optional.empty();
        return wolf.getData(ModAttachments.WOLF_SLEEP_CARPET.get());
    }

    public static boolean hasCarpet(Wolf wolf) {
        return carpet(wolf).isPresent();
    }

    /** True while the wolf is lying on its carpet (both sides; synced). */
    public static boolean isAsleep(Object entity) {
        return entity instanceof Wolf wolf
                && wolf.hasData(ModAttachments.WOLF_CARPET_ASLEEP.get())
                && wolf.getData(ModAttachments.WOLF_CARPET_ASLEEP.get());
    }

    public static void assign(Wolf wolf, BlockPos carpet) {
        wolf.setData(ModAttachments.WOLF_SLEEP_CARPET.get(), Optional.of(carpet.immutable()));
    }

    public static void lieDown(Wolf wolf) {
        wolf.setData(ModAttachments.WOLF_CARPET_ASLEEP.get(), true);
    }

    /** Clears the claim and the sleeping flag; the wolf's normal goals take over again. */
    public static void wake(Wolf wolf) {
        if (hasCarpet(wolf)) {
            wolf.setData(ModAttachments.WOLF_SLEEP_CARPET.get(), Optional.empty());
        }
        if (isAsleep(wolf)) {
            wolf.setData(ModAttachments.WOLF_CARPET_ASLEEP.get(), false);
        }
    }

    /** Wool carpets, moss carpet, any other CarpetBlock, and a pale moss carpet's floor layer. */
    public static boolean isCarpet(BlockState state) {
        if (state.is(BlockTags.WOOL_CARPETS) || state.getBlock() instanceof CarpetBlock) return true;
        return state.is(Blocks.PALE_MOSS_CARPET) && state.getValue(MossyCarpetBlock.BASE);
    }

    /** A carpet with free air above it (room for a lying wolf). */
    public static boolean isFreeCarpet(BlockGetter level, BlockPos pos) {
        return isCarpet(level.getBlockState(pos)) && level.getBlockState(pos.above()).isAir();
    }
}
