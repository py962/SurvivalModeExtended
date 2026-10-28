package com.terrainlab.mixin;

import net.minecraft.world.DifficultyInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DifficultyInstance.class)
public interface DifficultyInstanceAccessor {
    @Mutable
    @Accessor("effectiveDifficulty")
    void terrainlab$setEffectiveDifficulty(float value);
}
