package com.terrainlab.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.datafixers.util.Either;
import com.terrainlab.TerrainLab;
import com.terrainlab.api.BorrowingAccess;
import com.terrainlab.registry.ModMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * While a sleeping player has the Borrowing effect, points their client camera at a random nearby
 * living entity and restores it on wake. Uses a raw {@link ClientboundSetCameraPacket} rather than
 * {@code ServerPlayer#setCamera}, which would teleport the player and wake them. A short Nausea
 * effect marks each perspective switch.
 * <p>
 * Pressing sneak while borrowing drifts to a different nearby creature (unvisited ones preferred).
 * Every creature viewed counts toward a limit of {@link #MAX_HOPS}; sneaking on the last one, or when
 * no other creature is available, ends the borrow: the view returns to the player, the Borrowing
 * effect is removed and the player keeps sleeping normally (counting toward the night skip again).
 */
@Mixin(ServerPlayer.class)
public abstract class BorrowingSleepMixin implements BorrowingAccess {

    private static final double RADIUS = 24.0;
    private static final int MAX_HOPS = 5;

    @Unique
    private boolean terrainlab$isBorrowing = false;

    @Unique
    private LivingEntity terrainlab$borrowTarget;

    /** Creatures viewed during the current borrow session. */
    @Unique
    private final Set<UUID> terrainlab$visited = new HashSet<>();

    /** Number of creatures moved through this session; the first one counts as 1. */
    @Unique
    private int terrainlab$hopCount = 0;

    /** Last sneak state received from the client, for rising-edge detection. */
    @Unique
    private boolean terrainlab$lastShift = false;

    @Override
    public boolean terrainlab$isBorrowing() {
        return terrainlab$isBorrowing;
    }

    @Override
    public void terrainlab$setBorrowing(boolean borrowing) {
        terrainlab$isBorrowing = borrowing;
        if (!borrowing) {
            terrainlab$resetSession();
        }
    }

    @Override
    public void terrainlab$onClientShiftInput(boolean shift) {
        boolean pressed = shift && !terrainlab$lastShift;
        terrainlab$lastShift = shift;
        if (!pressed || !terrainlab$isBorrowing) {
            return;
        }
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (terrainlab$hopCount >= MAX_HOPS) {
            terrainlab$returnToSleep(self);
            return;
        }
        LivingEntity next = terrainlab$pickTarget(self, terrainlab$borrowTarget, terrainlab$visited);
        if (next == null) {
            terrainlab$returnToSleep(self);
        } else {
            terrainlab$switchTo(self, next);
        }
    }

    @ModifyReturnValue(method = "startSleepInBed", at = @At("RETURN"))
    private Either<Player.BedSleepingProblem, Unit> terrainlab$startBorrowing(
            Either<Player.BedSleepingProblem, Unit> original,
            net.minecraft.world.level.block.AbstractBedBlock bedBlock,
            net.minecraft.world.level.block.state.BlockState bedBlockState,
            net.minecraft.world.attribute.BedRule rule,
            BlockPos pos) {

        ServerPlayer self = (ServerPlayer) (Object) this;

        if (original.right().isEmpty()) {
            return original;
        }

        if (!self.hasEffect(ModMobEffects.BORROWING)) {
            return original;
        }

        LivingEntity target = terrainlab$pickTarget(self, null, null);
        if (target != null) {
            terrainlab$resetSession();
            // A sneak key already held when lying down must be released and pressed again.
            terrainlab$lastShift = self.getLastClientInput().shift();
            // TerrainLab.LOGGER.info("[BORROWING] {} borrowing the senses of {} (id={})",
            //         self.getName().getString(), target.getType(), target.getId());
            terrainlab$switchTo(self, target);
            terrainlab$isBorrowing = true;
            com.terrainlab.advancement.ModAdvancements.award(self, "borrowing_dream");
        }

        return original;
    }

    /**
     * Picks a random eligible creature near the player, never {@code exclude}. When {@code visited} is
     * given, creatures not yet viewed this session are preferred over ones already seen.
     */
    @Unique
    private static LivingEntity terrainlab$pickTarget(ServerPlayer self, LivingEntity exclude, Set<UUID> visited) {
        AABB searchBox = self.getBoundingBox().inflate(RADIUS);
        List<LivingEntity> nearby = self.level().getEntitiesOfClass(
                LivingEntity.class, searchBox,
                // Villagers excluded: they sleep at night too, which would tilt the view into a bed.
                entity -> entity != self && entity != exclude && entity.isAlive() && !(entity instanceof Villager));
        if (nearby.isEmpty()) {
            return null;
        }
        if (visited != null && !visited.isEmpty()) {
            List<LivingEntity> fresh = new ArrayList<>();
            for (LivingEntity entity : nearby) {
                if (!visited.contains(entity.getUUID())) {
                    fresh.add(entity);
                }
            }
            if (!fresh.isEmpty()) {
                nearby = fresh;
            }
        }
        return nearby.get(self.getRandom().nextInt(nearby.size()));
    }

    /**
     * Client-only camera swap; leaves position and sleep state untouched. Nausea marks the switch.
     * Every switch counts as one creature moved through and refreshes the action-bar hint.
     */
    @Unique
    private void terrainlab$switchTo(ServerPlayer self, LivingEntity target) {
        self.connection.send(new ClientboundSetCameraPacket(target));
        terrainlab$borrowTarget = target;
        terrainlab$visited.add(target.getUUID());
        terrainlab$hopCount++;
        self.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20, 0));
        self.sendSystemMessage(terrainlab$hopCount >= MAX_HOPS
                ? Component.translatable("message.terrainlab.borrowing.return")
                : Component.translatable("message.terrainlab.borrowing.drift", terrainlab$hopCount, MAX_HOPS), true);
    }

    /**
     * Ends the borrow without waking: the view returns to the player, the Borrowing effect is
     * removed and the player keeps sleeping, counting toward the night skip again.
     */
    @Unique
    private void terrainlab$returnToSleep(ServerPlayer self) {
        self.connection.send(new ClientboundSetCameraPacket(self));
        terrainlab$isBorrowing = false;
        terrainlab$borrowTarget = null;
        terrainlab$resetSession();
        self.removeEffect(ModMobEffects.BORROWING);
        self.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20, 0));
    }

    @Unique
    private void terrainlab$resetSession() {
        terrainlab$visited.clear();
        terrainlab$hopCount = 0;
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
        LivingEntity next = terrainlab$pickTarget(self, target, terrainlab$visited);
        if (next != null) {
            terrainlab$switchTo(self, next);
        } else {
            self.connection.send(new ClientboundSetCameraPacket(self));
            terrainlab$borrowTarget = null;
            terrainlab$isBorrowing = false;
            terrainlab$resetSession();
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
        terrainlab$resetSession();

        self.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20, 0));
    }
}
