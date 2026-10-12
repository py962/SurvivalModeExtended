package com.terrainlab.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * Looping, non-positional ambience bed whose volume and pitch are driven each tick by an
 * {@link AmbienceClient.Layer}. It stops itself once the layer has been silent for two seconds.
 */
public class AmbienceLoopSound extends AbstractTickableSoundInstance {

    private static final float MIN_VOLUME = 0.001F;
    private static final int STOP_AFTER_SILENT_TICKS = 40;

    private final AmbienceClient.Layer layer;
    private int silentTicks;

    public AmbienceLoopSound(SoundEvent event, AmbienceClient.Layer layer) {
        super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.layer = layer;
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
        this.volume = Math.max(MIN_VOLUME, layer.volume());
        this.pitch = layer.pitch();
    }

    @Override
    public void tick() {
        float v = this.layer.volume();
        this.volume = Math.max(MIN_VOLUME, v);
        this.pitch = this.layer.pitch();

        if (v <= MIN_VOLUME && this.layer.target() <= MIN_VOLUME) {
            if (++this.silentTicks > STOP_AFTER_SILENT_TICKS) {
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
