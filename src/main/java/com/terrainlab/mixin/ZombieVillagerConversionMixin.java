package com.terrainlab.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Villagers killed by a zombie always become zombie villagers on Normal (and so on Isolation), as on
 * vanilla Hard, instead of half the time. Easy and Peaceful keep vanilla's no-conversion rule.
 */
@Mixin(Zombie.class)
public abstract class ZombieVillagerConversionMixin {

    @Redirect(
            method = "killedEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getDifficulty()Lnet/minecraft/world/Difficulty;"
            ),
            require = 1
    )
    private Difficulty terrainlab$alwaysConvert(ServerLevel level) {
        Difficulty difficulty = level.getDifficulty();
        return difficulty == Difficulty.NORMAL ? Difficulty.HARD : difficulty;
    }
}
