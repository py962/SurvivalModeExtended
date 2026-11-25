package com.terrainlab.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Faint shimmer rising off hot ground or lava: wavers sideways and cycles its frames as it climbs. */
public class HeatHazeParticle extends SingleQuadParticle {

    private static final float MAX_ALPHA = 0.45F;

    private final SpriteSet sprites;
    private final double baseX;
    private final double baseZ;
    private final float phase;

    protected HeatHazeParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        this.baseX = x;
        this.baseZ = z;
        this.phase = this.random.nextFloat() * Mth.TWO_PI;
        this.xd = 0.0D;
        this.yd = 0.012D + this.random.nextDouble() * 0.008D;
        this.zd = 0.0D;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.lifetime = 40 + this.random.nextInt(25);
        this.quadSize = 0.35F + this.random.nextFloat() * 0.25F;
        this.setColor(1.0F, 0.97F, 0.9F);
        this.setAlpha(0.0F);
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) {
            return;
        }
        float sway = Mth.sin(this.phase + this.age * 0.2F) * 0.06F;
        this.setPos(this.baseX + sway, this.y, this.baseZ + sway * 0.5F);
        this.setSpriteFromAge(this.sprites);

        float life = this.age / (float) this.lifetime;
        float fadeIn = Mth.clamp(life / 0.3F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp((1.0F - life) / 0.4F, 0.0F, 1.0F);
        this.setAlpha(MAX_ALPHA * Math.min(fadeIn, fadeOut));
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd, RandomSource random) {
            return new HeatHazeParticle(level, x, y, z, this.sprites);
        }
    }
}
