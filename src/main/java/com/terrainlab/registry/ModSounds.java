package com.terrainlab.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sound event registrations: mob sounds, distant wildlife calls, biome ambience and village sounds.
 */
public class ModSounds {

    public static final String MODID = "terrainlab";

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, MODID);

    // Owl
    public static final DeferredHolder<SoundEvent, SoundEvent> OWL_AMBIENT = register("owl.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> OWL_HURT = register("owl.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> OWL_DEATH = register("owl.death");
    // Distant call used as biome ambience, independent of any owl entity.
    public static final DeferredHolder<SoundEvent, SoundEvent> OWL_AMBIENT_DISTANT = register("owl.ambient_distant");

    // Goose
    public static final DeferredHolder<SoundEvent, SoundEvent> GOOSE_AMBIENT = register("goose.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOOSE_HURT = register("goose.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> GOOSE_DEATH = register("goose.death");
    // Distant call used as biome ambience.
    public static final DeferredHolder<SoundEvent, SoundEvent> GOOSE_AMBIENT_DISTANT = register("goose.ambient_distant");

    // Vulture
    public static final DeferredHolder<SoundEvent, SoundEvent> VULTURE_AMBIENT = register("vulture.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> VULTURE_HURT = register("vulture.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> VULTURE_DEATH = register("vulture.death");
    // Distant call used as biome ambience.
    public static final DeferredHolder<SoundEvent, SoundEvent> VULTURE_AMBIENT_DISTANT = register("vulture.ambient_distant");

    // Elephant
    public static final DeferredHolder<SoundEvent, SoundEvent> ELEPHANT_AMBIENT = register("elephant.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELEPHANT_HURT = register("elephant.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELEPHANT_DEATH = register("elephant.death");
    // Played when a ridden elephant starts its dash (charge).
    public static final DeferredHolder<SoundEvent, SoundEvent> ELEPHANT_TRUMPET = register("elephant.trumpet");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELEPHANT_BABY_AMBIENT = register("elephant.baby_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> ELEPHANT_BABY_HURT = register("elephant.baby_hurt");
    // TODO: ELEPHANT_STEP once a footstep sound exists.

    // Caribou
    public static final DeferredHolder<SoundEvent, SoundEvent> CARIBOU_AMBIENT = register("caribou.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> CARIBOU_HURT = register("caribou.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> CARIBOU_DEATH = register("caribou.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> CARIBOU_BABY_AMBIENT = register("caribou.baby_ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> CARIBOU_BABY_HURT = register("caribou.baby_hurt");
    // Distant herd ambience (bells, grunting), not an individual mob's idle sound.
    public static final DeferredHolder<SoundEvent, SoundEvent> CARIBOU_HERD_AMBIENCE = register("caribou.herd_ambience");
    // TODO: CARIBOU_STEP once a footstep sound exists.

    // Ambience beds and calls, played by AmbienceClient.
    public static final DeferredHolder<SoundEvent, SoundEvent> BIOME_FOREST_TEMPERATE = register("biome_ambience.forest_temperate");
    public static final DeferredHolder<SoundEvent, SoundEvent> BIOME_FOREST_FLORAL = register("biome_ambience.forest_floral");
    public static final DeferredHolder<SoundEvent, SoundEvent> BIOME_JUNGLE_NIGHT = register("biome_ambience.jungle_night");
    public static final DeferredHolder<SoundEvent, SoundEvent> BIOME_SWAMP_NIGHT = register("biome_ambience.swamp_night");
    public static final DeferredHolder<SoundEvent, SoundEvent> BIOME_WOLVES_DISTANT_NIGHT = register("biome_ambience.wolves_distant_night");
    public static final DeferredHolder<SoundEvent, SoundEvent> BIOME_OCEAN_WAVES_COLD = register("biome_ambience.ocean_waves_cold");
    public static final DeferredHolder<SoundEvent, SoundEvent> BIOME_BEACH_WAVES_WARM = register("biome_ambience.beach_waves_warm");
    public static final DeferredHolder<SoundEvent, SoundEvent> BIOME_MOUNTAIN_WIND = register("biome_ambience.mountain_wind");
    // Distant call used as biome ambience.
    public static final DeferredHolder<SoundEvent, SoundEvent> ELEPHANT_AMBIENT_DISTANT = register("elephant.ambient_distant");

    // Dusk song at a tier-4 village's gathering (VillageSongService); one hum clip played at note pitches.
    public static final DeferredHolder<SoundEvent, SoundEvent> VILLAGER_SONG = register("villager.song");

    // Mood sounds replacing a hungry/tired/trapped/fearful villager's ambient sound (VillagerMoodSoundMixin).
    public static final DeferredHolder<SoundEvent, SoundEvent> VILLAGER_MOOD_HUNGRY = register("villager.mood.hungry");
    public static final DeferredHolder<SoundEvent, SoundEvent> VILLAGER_MOOD_TIRED = register("villager.mood.tired");
    public static final DeferredHolder<SoundEvent, SoundEvent> VILLAGER_MOOD_TRAPPED = register("villager.mood.trapped");
    public static final DeferredHolder<SoundEvent, SoundEvent> VILLAGER_MOOD_FEARFUL = register("villager.mood.fearful");
    public static final DeferredHolder<SoundEvent, SoundEvent> VILLAGER_MOOD_INJURED = register("villager.mood.injured");

    // Celestial Forge: ritual synth loop (20 s) and the transform swell ending in a low boom.
    public static final DeferredHolder<SoundEvent, SoundEvent> CELESTIAL_FORGE_RITUAL = register("block.celestial_forge.ritual");
    public static final DeferredHolder<SoundEvent, SoundEvent> CELESTIAL_FORGE_TRANSFORM = register("block.celestial_forge.transform");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(MODID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(net.neoforged.bus.api.IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}
