package com.terrainlab.mixin;

import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Takes the coordinate lines (XYZ, block, chunk, facing; section-relative position) out of the default
 * F3 profile. Players can still turn them back on in the debug options screen (F3+F6).
 */
@Mixin(RegisterDebugEntriesEvent.class)
public abstract class DebugProfileCoordinatesMixin {

    @Shadow @Final private Map<Identifier, DebugScreenEntryStatus> defaultProfile;

    @Inject(method = "<init>", at = @At("TAIL"), require = 1)
    private void terrainlab$coordinatesOff(CallbackInfo ci) {
        this.defaultProfile.remove(DebugScreenEntries.PLAYER_POSITION);
        this.defaultProfile.remove(DebugScreenEntries.PLAYER_SECTION_POSITION);
    }
}
