package com.terrainlab.mixin;

import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhaseManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Below half health the dragon keeps charging the nearest survival player. */
@Mixin(EnderDragon.class)
public abstract class EnderDragonRelentlessMixin {

    private static final float ENRAGE_THRESHOLD = 0.5F; // fraction of max health
    private static final double TARGET_RANGE = 150.0; // blocks

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void terrainlab$relentlessPursuit(CallbackInfo ci) {
        EnderDragon dragon = (EnderDragon) (Object) this;

        if (dragon.level().isClientSide()) return;

        boolean enraged = dragon.getHealth() <= dragon.getMaxHealth() * ENRAGE_THRESHOLD;
        if (!enraged) return;

        EnderDragonPhaseManager phaseManager = dragon.getPhaseManager();
        DragonPhaseInstance current = phaseManager.getCurrentPhase();
        EnderDragonPhase<? extends DragonPhaseInstance> type = current.getPhase();

        if (type == EnderDragonPhase.CHARGING_PLAYER || type == EnderDragonPhase.DYING) return;

        // The charge phase aborts on its first tick unless it has a target.
        Player target = dragon.level().getNearestPlayer(dragon.getX(), dragon.getY(), dragon.getZ(),
                TARGET_RANGE, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
        if (target == null) return;

        phaseManager.setPhase(EnderDragonPhase.CHARGING_PLAYER);
        phaseManager.getPhase(EnderDragonPhase.CHARGING_PLAYER)
                .setTarget(new Vec3(target.getX(), target.getY(), target.getZ()));
    }
}
