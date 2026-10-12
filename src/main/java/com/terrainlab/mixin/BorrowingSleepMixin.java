package com.terrainlab.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.datafixers.util.Either;
import com.terrainlab.TerrainLab;
import com.terrainlab.api.BorrowingAccess;
import com.terrainlab.registry.ModMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * While a sleeping player has the Borrowing effect, points their client camera at a random nearby
 * living entity and restores it on wake. Uses a raw {@link ClientboundSetCameraPacket} rather than
 * {@code ServerPlayer#setCamera}, which would teleport the player and wake them. A short Nausea
 * effect marks each perspective switch.
 */
@Mixin(ServerPlayer.class)
public abstract class BorrowingSleepMixin implements BorrowingAccess {

    private static final double RADIUS = 24.0;

    @Unique
    private boolean terrainlab$isBorrowing = false;

    @Unique
    private LivingEntity terrainlab$borrowTarget;

    @Override
    public boolean terrainlab$isBorrowing() {
        return terrainlab$isBorrowing;
    }

    @Override
    public void terrainlab$setBorrowing(boolean borrowing) {
        terrainlab$isBorrowing = borrowing;
    }

    @ModifyReturnValue(method = "startSleepInBed", at = @At("RETURN"))
    private Either<Player.BedSleepingProblem, Unit> terrainlab$startBorrowing(
            Either<Player.BedSleepingProblem, Unit> original, BlockPos pos) {

        ServerPlayer self = (ServerPlayer) (Object) this;

        if (original.right().isEmpty()) {
            return original;
        }

        if (!self.hasEffect(ModMobEffects.BORROWING)) {
            return original;
        }

        LivingEntity target = terrainlab$pickTarget(self);
        if (target != null) {
            // TerrainLab.LOGGER.info("[BORROWING] {} borrowing the senses of {} (id={})",
            //         self.getName().getString(), target.getType(), target.getId());
            terrainlab$switchTo(self, target);
            terrainlab$isBorrowing = true;
            com.terrainlab.advancement.ModAdvancements.award(self, "borrowing_dream");
        }

        return original;
    }

    @Unique
    private static LivingEntity terrainlab$pickTarget(ServerPlayer self) {
        AABB searchBox = self.getBoundingBox().inflate(RADIUS);
        List<LivingEntity> nearby = self.level().getEntitiesOfClass(
                LivingEntity.class, searchBox,
                // Villagers excluded: they sleep at night too, which would tilt the view into a bed.
                entity -> entity != self && entity.isAlive() && !(entity instanceof Villager));
        return nearby.isEmpty() ? null : nearby.get(self.getRandom().nextInt(nearby.size()));
    }

    /** Client-only camera swap; leaves position and sleep state untouched. Nausea marks the switch. */
    @Unique
    private void terrainlab$switchTo(ServerPlayer self, LivingEntity target) {
        self.connection.send(new ClientboundSetCameraPacket(target));
        terrainlab$borrowTarget = target;
        self.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20, 0));
    }

    /** Moves to another nearby creature, or back to the player, when the borrowed one dies or strays. */
    @Inject(method = "tick", at = @At("TAIL"))
    private void terrainlab$checkBorrowTarget(CallbackInfo ci) {
        if (!terrainlab$isBorrowing) {
            return;
        }
        ServerPlayer self = (ServerPlayer) (Object) this;
        LivingEntity target = terrainlab$borrowTarget;
        if (target != null && target.isAlive() && !target.isRemoved()
                && target.level() == self.level() && target.distanceToSqr(self) <= RADIUS * RADIUS * 4) {
            return;
        }
        LivingEntity next = terrainlab$pickTarget(self);
        if (next != null) {
            terrainlab$switchTo(self, next);
        } else {
            self.connection.send(new ClientboundSetCameraPacket(self));
            terrainlab$borrowTarget = null;
            terrainlab$isBorrowing = false;
        }
    }

    @Inject(method = "stopSleepInBed", at = @At("HEAD"))
    private void terrainlab$stopBorrowingOnWake(boolean immediately, boolean updateSleepingPlayers, CallbackInfo ci) {
        if (!terrainlab$isBorrowing) {
            return;
        }

        ServerPlayer self = (ServerPlayer) (Object) this;
        // TerrainLab.LOGGER.info("[BORROWING] {} woke up - restoring own view", self.getName().getString());
        self.connection.send(new ClientboundSetCameraPacket(self));
        terrainlab$isBorrowing = false;
        terrainlab$borrowTarget = null;

        self.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20, 0));
    }
}
