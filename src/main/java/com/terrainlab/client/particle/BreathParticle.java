package com.terrainlab.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** A puff of visible breath in cold air: drifts forward, swells and fades. */
public class BreathParticle extends SingleQuadParticle {

    private static final float MAX_ALPHA = 0.55F;

    protected BreathParticle(ClientLevel level, double x, double y, double z,
                             double xd, double yd, double zd, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.hasPhysics = false;
        this.gravity = -0.004F;
        this.friction = 0.9F;
        this.lifetime = 20 + this.random.nextInt(8);
        this.quadSize = 0.07F;
        this.roll = this.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        this.setAlpha(0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) {
            return;
        }
        float life = this.age / (float) this.lifetime;
        float fadeIn = Mth.clamp(this.age / 3.0F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp((1.0F - life) / 0.5F, 0.0F, 1.0F);
        this.setAlpha(MAX_ALPHA * Math.min(fadeIn, fadeOut));
    }

    @Override
    public float getQuadSize(float partialTick) {
        float life = Mth.clamp((this.age + partialTick) / this.lifetime, 0.0F, 1.0F);
        return this.quadSize * (1.0F + life * 1.4F);
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
            return new BreathParticle(level, x, y, z, xd, yd, zd, this.sprites.get(random));
        }
    }
}
