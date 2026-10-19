package com.terrainlab.mixin;

import com.terrainlab.registry.ModAttachments;
import com.terrainlab.registry.ModSounds;
import com.terrainlab.village.VillagerGestureAccess;
import com.terrainlab.village.VillagerMoodService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces a villager's plain ambient sound with a mood voice when it is fearful, injured, tired,
 * hungry or trapped (first match wins). The tired and trapped voices also broadcast an entity event so clients
 * play the matching gesture ({@link VillagerGestureAccess}).
 */
@Mixin(Villager.class)
public abstract class VillagerMoodSoundMixin implements VillagerGestureAccess {

    @Unique
    private int terrainlab$gesture = NONE;
    @Unique
    private int terrainlab$gestureStart;

    @Inject(method = "getAmbientSound", at = @At("RETURN"), cancellable = true, require = 1)
    private void terrainlab$moodVoice(CallbackInfoReturnable<SoundEvent> cir) {
        if (cir.getReturnValue() != SoundEvents.VILLAGER_AMBIENT) {
            return;
        }
        Villager self = (Villager) (Object) this;
        int flags = self.getData(ModAttachments.VILLAGER_MOOD_FLAGS.get());
        if ((flags & VillagerMoodService.FEARFUL) != 0) {
            cir.setReturnValue(ModSounds.VILLAGER_MOOD_FEARFUL.get());
        } else if ((flags & VillagerMoodService.INJURED) != 0) {
            cir.setReturnValue(ModSounds.VILLAGER_MOOD_INJURED.get());
        } else if ((flags & VillagerMoodService.TIRED) != 0) {
            cir.setReturnValue(ModSounds.VILLAGER_MOOD_TIRED.get());
            terrainlab$sendGesture(self, EVENT_YAWN);
        } else if ((flags & VillagerMoodService.HUNGRY) != 0) {
            cir.setReturnValue(ModSounds.VILLAGER_MOOD_HUNGRY.get());
        } else if ((flags & VillagerMoodService.TRAPPED) != 0) {
            cir.setReturnValue(ModSounds.VILLAGER_MOOD_TRAPPED.get());
            terrainlab$sendGesture(self, EVENT_STAMP);
        }
    }

    /** Server only: ambient sounds are audible only when played server-side, so this matches the sound. */
    @Unique
    private static void terrainlab$sendGesture(Villager villager, byte event) {
        if (villager.level() instanceof ServerLevel level) {
            level.broadcastEntityEvent(villager, event);
        }
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$receiveGesture(byte id, CallbackInfo ci) {
        if (id == EVENT_YAWN || id == EVENT_STAMP) {
            this.terrainlab$gesture = id == EVENT_YAWN ? YAWN : STAMP;
            this.terrainlab$gestureStart = ((Villager) (Object) this).tickCount;
            ci.cancel();
        }
    }

    @Override
    public int terrainlab$gesture() {
        return this.terrainlab$gesture;
    }

    @Override
    public int terrainlab$gestureStart() {
        return this.terrainlab$gestureStart;
    }
}
