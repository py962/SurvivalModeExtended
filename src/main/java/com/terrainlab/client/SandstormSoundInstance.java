package com.terrainlab.client;

import com.terrainlab.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * Looping, non-positional sandstorm wind whose volume follows
 * {@link SandstormClient#soundStrength()} and whose pitch drops with severity.
 * Stops itself once the storm has faded out.
 */
public class SandstormSoundInstance extends AbstractTickableSoundInstance {

    private static final float MAX_VOLUME = 1.0F;
    private int silentTicks;

    public SandstormSoundInstance() {
        super(ModSounds.BIOME_MOUNTAIN_WIND.get(), SoundSource.WEATHER, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
        this.volume = 0.01F;
        this.pitch = 1.0F;
    }

    @Override
    public void tick() {
        float strength = SandstormClient.soundStrength();
        float severity = SandstormClient.severity();

        this.volume = Math.max(0.01F, Mth.clamp(strength * 1.4F, 0.0F, 1.0F) * MAX_VOLUME);
        this.pitch = Mth.lerp(severity, 1.0F, 0.8F);

        if (strength < 0.01F) {
            if (++this.silentTicks > 40) {
                this.stop();
            }
        } else {
            this.silentTicks = 0;
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
