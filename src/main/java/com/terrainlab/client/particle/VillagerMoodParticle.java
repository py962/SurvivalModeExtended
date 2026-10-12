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

/**
 * Villager comfort indicator particles. Each mood sprite gets its own motion: FLAKE
 * (cold) drifts down, PUFF (breath) swells and fades, DROP (sweat) arcs and falls, POP
 * (fearful) pops in and trembles, RISE (tired) floats up and shrinks, BOB (hungry)
 * bobs above the head. All are translucent so they can fade.
 */
public class VillagerMoodParticle extends SingleQuadParticle {

    public enum Motion { FLAKE, PUFF, DROP, POP, RISE, BOB }

    private final Motion motion;
    private final float baseSize;
    private final double baseX;
    private final double baseY;
    private final float phase;

    protected VillagerMoodParticle(
            ClientLevel level,
            double x, double y, double z,
            double xd, double yd, double zd,
            TextureAtlasSprite sprite,
            Motion motion) {

        super(level, x, y, z, sprite);
        this.motion = motion;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.hasPhysics = false;
        this.baseX = x;
        this.baseY = y;
        this.phase = this.random.nextFloat() * Mth.TWO_PI;

        switch (motion) {
            case FLAKE -> {
                this.gravity = 0.05F;
                this.friction = 0.92F;
                this.lifetime = 40;
                this.baseSize = 0.07F;
            }
            case PUFF -> {
                this.gravity = -0.01F;
                this.friction = 0.88F;
                this.lifetime = 18;
                this.baseSize = 0.06F;
            }
            case DROP -> {
                this.gravity = 0.9F;
                this.friction = 0.98F;
                this.lifetime = 22;
                this.hasPhysics = true;
                this.baseSize = 0.06F;
            }
            case POP -> {
                this.gravity = 0.0F;
                this.friction = 1.0F;
                this.lifetime = 26;
                this.baseSize = 0.13F;
            }
            case RISE -> {
                this.gravity = 0.0F;
                this.friction = 1.0F;
                this.lifetime = 44;
                this.baseSize = 0.12F;
            }
            default -> { // BOB
                this.gravity = 0.0F;
                this.friction = 1.0F;
                this.lifetime = 34;
                this.baseSize = 0.12F;
            }
        }

        this.quadSize = this.baseSize;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.removed) {
            return;
        }

        float life = this.age / (float) this.lifetime;

        switch (this.motion) {
            case POP -> {
                this.setPos(this.baseX + (this.random.nextFloat() - 0.5F) * 0.03F, this.y, this.z);
            }
            case RISE -> {
                this.setPos(
                        this.baseX + Mth.sin(this.phase + this.age * 0.18F) * 0.08F,
                        this.baseY + life * 0.55,
                        this.z);
            }
            case BOB -> {
                this.setPos(this.x, this.baseY + Mth.sin(this.phase + this.age * 0.25F) * 0.04F, this.z);
            }
            default -> {
            }
        }

        // Fade in over the first 3 ticks, out over the last quarter.
        float fadeIn = Mth.clamp(this.age / 3.0F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp((1.0F - life) / 0.25F, 0.0F, 1.0F);
        this.setAlpha(Math.min(fadeIn, fadeOut));
    }

    @Override
    public float getQuadSize(float partialTick) {
        float life = Mth.clamp((this.age + partialTick) / this.lifetime, 0.0F, 1.0F);

        return switch (this.motion) {
            case PUFF -> this.baseSize * (1.0F + life * 1.2F);               // swells
            case RISE -> this.baseSize * (1.0F - life * 0.5F);               // shrinks as it rises
            case POP -> this.baseSize * Mth.clamp((this.age + partialTick) / 4.0F, 0.0F, 1.0F); // pops in
            default -> this.baseSize;
        };
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final Motion motion;

        public Provider(SpriteSet sprites, Motion motion) {
            this.sprites = sprites;
            this.motion = motion;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x, double y, double z,
                double xd, double yd, double zd,
                RandomSource random) {

            return new VillagerMoodParticle(level, x, y, z, xd, yd, zd, this.sprites.first(), this.motion);
        }
    }
}
