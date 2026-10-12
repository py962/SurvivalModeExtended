package com.terrainlab.village;

/**
 * Mood gestures (yawn, stamp) synced to a villager's mood voice. The server sends an entity event
 * when the voice plays so clients start the gesture in step. Implemented on Villager by
 * VillagerMoodSoundMixin; read by VillagerMoodClient and VillagerModelMoodMixin.
 */
public interface VillagerGestureAccess {

    int NONE = 0;
    int YAWN = 1;
    int STAMP = 2;

    /** Entity event ids - unused by vanilla's villager and its parents. */
    byte EVENT_YAWN = 120;
    byte EVENT_STAMP = 121;

    int terrainlab$gesture();

    /** The villager's tickCount when the gesture started. */
    int terrainlab$gestureStart();
}
