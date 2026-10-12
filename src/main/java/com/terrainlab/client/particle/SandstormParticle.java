package com.terrainlab.client.particle;

import com.terrainlab.weather.Sandstorm;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Sandstorm particle, spawned by SandstormClient already moving with the wind. GRAIN is
 * small and opaque, collides, and is removed on hitting a wall or landing; PUFF is a
 * large, faint dust cloud that passes through blocks and swells. Greyscale sprites are
 * tinted sand or badlands red from the spawn position's biome.
 */
public class SandstormParticle extends SingleQuadParticle {

    public enum Kind { GRAIN, PUFF }

    private static final float[] SAND = {0.86F, 0.76F, 0.55F};
    private static final float[] RED_SAND = {0.78F, 0.46F, 0.26F};

    private final Kind kind;
    private final float baseSize;
    private final float maxAlpha;

    protected SandstormParticle(
            ClientLevel level,
            double x, double y, double z,
            double xd, double yd, double zd,
            TextureAtlasSprite sprite,
            Kind kind) {

        super(level, x, y, z, sprite);
        this.kind = kind;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.friction = 0.99F;

        float[] colour = Sandstorm.isRedSand(level, BlockPos.containing(x, y, z)) ? RED_SAND : SAND;
        float shade = 0.9F + this.random.nextFloat() * 0.2F;
        this.setColor(
                Mth.clamp(colour[0] * shade, 0.0F, 1.0F),
                Mth.clamp(colour[1] * shade, 0.0F, 1.0F),
                Mth.clamp(colour[2] * shade, 0.0F, 1.0F));

        if (kind == Kind.GRAIN) {
            this.hasPhysics = true;
            this.gravity = 0.06F;
            this.lifetime = 30 + this.random.nextInt(30);
            this.baseSize = 0.035F + this.random.nextFloat() * 0.03F;
            this.maxAlpha = 0.9F;
        } else {
            this.hasPhysics = false;
            this.gravity = 0.0F;
            this.lifetime = 50 + this.random.nextInt(40);
            this.baseSize = 0.6F + this.random.nextFloat() * 0.7F;
            this.maxAlpha = 0.2F + this.random.nextFloat() * 0.15F;
        }

        this.quadSize = this.baseSize;
        this.setAlpha(0.0F);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.removed) {
            return;
        }

        // Remove grains that stop against a wall or land rather than leaving specks.
        if (this.kind == Kind.GRAIN && (this.onGround || (this.x == this.xo && this.z == this.zo))) {
            this.remove();
            return;
        }

        float life = this.age / (float) this.lifetime;
        float fadeIn = Mth.clamp(this.age / 6.0F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp((1.0F - life) / 0.3F, 0.0F, 1.0F);
        this.setAlpha(this.maxAlpha * Math.min(fadeIn, fadeOut));
    }

    @Override
    public float getQuadSize(float partialTick) {
        if (this.kind == Kind.PUFF) {
            float life = Mth.clamp((this.age + partialTick) / this.lifetime, 0.0F, 1.0F);
            return this.baseSize * (1.0F + life * 0.8F);
        }
        return this.baseSize;
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final Kind kind;

        public Provider(SpriteSet sprites, Kind kind) {
            this.sprites = sprites;
            this.kind = kind;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x, double y, double z,
                double xd, double yd, double zd,
                RandomSource random) {

            return new SandstormParticle(level, x, y, z, xd, yd, zd, this.sprites.get(random), this.kind);
        }
    }
}
