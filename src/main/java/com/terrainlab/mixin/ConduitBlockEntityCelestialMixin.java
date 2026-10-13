package com.terrainlab.mixin;

import com.terrainlab.celestial.BreathConduitHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Links the vanilla conduit (the Breath conduit) to the Breath amulet; all logic lives in
 * {@link BreathConduitHooks}. The tick hook runs on vanilla's 40-tick frame-check cadence, and
 * {@code preRemoveSideEffects} fires only on real server-side block removal, not chunk unload.
 */
@Mixin(ConduitBlockEntity.class)
public abstract class ConduitBlockEntityCelestialMixin extends BlockEntity {

    @Shadow
    @Final
    private List<BlockPos> effectBlocks;

    private ConduitBlockEntityCelestialMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "serverTick", at = @At("TAIL"), require = 1)
    private static void terrainlab$breathConduitCheck(Level level, BlockPos pos, BlockState state,
                                                       ConduitBlockEntity be, CallbackInfo ci) {
        if (level.getGameTime() % 40L != 0L) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        int frameCount = ((ConduitBlockEntityCelestialMixin) (Object) be).effectBlocks.size();
        BreathConduitHooks.onServerCheck(serverLevel, pos, be.isActive(), frameCount, be.components());
        if (be.isActive()) {
            com.terrainlab.advancement.ModAdvancements.awardNearby(serverLevel, net.minecraft.world.phys.Vec3.atCenterOf(pos), 16.0, "conduit_breath");
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel serverLevel) {
            BreathConduitHooks.onRemoved(serverLevel, pos, this.components());
        }
    }
}
