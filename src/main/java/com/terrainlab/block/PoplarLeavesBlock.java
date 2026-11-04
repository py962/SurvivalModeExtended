package com.terrainlab.block;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;

/**
 * Poplar leaves (backported from 26.3): untinted leaves that drop the mod's own falling-leaf particle.
 * The particle type is registered after blocks, so it is looked up when a particle spawns rather than
 * passed in at construction like vanilla's UntintedParticleLeavesBlock does.
 */
public class PoplarLeavesBlock extends UntintedParticleLeavesBlock {

    private final Supplier<? extends ParticleOptions> particle;

    public PoplarLeavesBlock(float leafParticleChance, Supplier<? extends ParticleOptions> particle, Properties properties) {
        super(leafParticleChance, ParticleTypes.CHERRY_LEAVES, properties); // placeholder; see spawnFallingLeavesParticle
        this.particle = particle;
    }

    @Override
    protected void spawnFallingLeavesParticle(Level level, BlockPos pos, RandomSource random) {
        ParticleUtils.spawnParticleBelow(level, pos, random, this.particle.get());
    }
}
