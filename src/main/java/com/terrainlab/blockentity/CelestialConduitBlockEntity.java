package com.terrainlab.blockentity;

import com.terrainlab.block.CelestialConduitBlock;
import com.terrainlab.celestial.CelestialConduitRegistry;
import com.terrainlab.celestial.CelestialDebug;
import com.terrainlab.celestial.CelestialEffects;
import com.terrainlab.celestial.CelestialFrame;
import com.terrainlab.celestial.CelestialVariant;
import com.terrainlab.registry.ModBlockEntities;
import com.terrainlab.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;
import java.util.UUID;

/**
 * Shared block entity for the seven custom Celestial Conduits (Breath uses
 * vanilla's ConduitBlockEntity). Reproduces vanilla's frame activation on a
 * 40-tick cadence, minus the underwater requirement; see {@link CelestialFrame}.
 * The client side mirrors vanilla's rotation and frame-particle animation, using
 * dust tinted with the variant's colour in place of the nautilus sprite.
 */
public class CelestialConduitBlockEntity extends BlockEntity {

    private static final int CHECK_INTERVAL = 40; // ticks, as vanilla
    private static final float ROTATION_SPEED = -0.0375F; // vanilla ConduitBlockEntity value

    private UUID pairId;
    private boolean active = false;
    private boolean fullPower = false;
    private int frameCount = 0;
    private int tickCounter = 0;

    /** Client tick counter driving the renderer's wind/eye animation; mirrors vanilla's public {@code tickCount}. */
    public int tickCount;
    private float activeRotation;
    /** Client-only frame positions for particles, refreshed every CHECK_INTERVAL ticks. */
    private List<BlockPos> cachedFramePositions;

    public CelestialConduitBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CELESTIAL_CONDUIT_BE.get(), pos, state);
    }

    public void setPairId(UUID pairId) {
        this.pairId = pairId;
        setChanged();
    }

    public UUID pairId() {
        return pairId;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isFullPower() {
        return fullPower;
    }

    public int frameCount() {
        return frameCount;
    }

    /** Effect radius: 32..96 in steps of 16 per 7 frame blocks; see {@link CelestialFrame#rangeFor}. */
    public int effectRadius() {
        return CelestialFrame.rangeFor(frameCount);
    }

    public CelestialVariant variant() {
        return getBlockState().getBlock() instanceof CelestialConduitBlock conduitBlock
                ? conduitBlock.variant()
                : null;
    }

    /** Accumulated cage/shell rotation, as vanilla's ConduitBlockEntity#getActiveRotation. */
    public float getActiveRotation(float partialTick) {
        return (this.activeRotation + partialTick) * ROTATION_SPEED;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CelestialConduitBlockEntity be) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        be.tickCounter++;
        if (be.tickCounter < CHECK_INTERVAL) {
            return;
        }
        be.tickCounter = 0;

        CelestialVariant variant = be.variant();
        if (variant == null) {
            if (CelestialDebug.VERBOSE) {
                CelestialDebug.log("conduit check at {} skipped - block {} is not a CelestialConduitBlock",
                        pos, state.getBlock());
            }
            return;
        }

        boolean wasActive = be.active;
        be.frameCount = CelestialFrame.countFrameBlocks(level, pos, variant);
        be.active = be.frameCount >= CelestialFrame.MIN_ACTIVE;
        be.fullPower = CelestialFrame.isFullPower(be.frameCount);

        // Every conduit is registered, paired or not; unpaired ones use a positional key.
        java.util.UUID registryKey = CelestialConduitRegistry.keyFor(be.pairId, serverLevel.dimension(), pos);
        CelestialConduitRegistry.get(serverLevel)
                .setConduit(registryKey, serverLevel.dimension(), pos.immutable(), be.active, variant, be.effectRadius());

        if (CelestialDebug.VERBOSE) {
            CelestialDebug.log("conduit check {} at {} {}: frame={}/{} active={} fullPower={} radius={} key={} ({})",
                    variant, serverLevel.dimension().identifier(), pos, be.frameCount, CelestialFrame.FULL_FRAME,
                    be.active, be.fullPower, be.effectRadius(), registryKey, be.pairId != null ? "paired" : "unpaired");
        }

        if (be.active) {
            CelestialEffects.tickConduit(serverLevel, pos, variant, be.effectRadius(), be.fullPower);
            com.terrainlab.advancement.ModAdvancements.awardNearby(serverLevel, net.minecraft.world.phys.Vec3.atCenterOf(pos), 16.0,
                    "conduit_" + variant.name().toLowerCase(java.util.Locale.ROOT));
        }

        if (wasActive != be.active) {
            be.setChanged();

            BlockState syncState = state;
            if (wasActive != be.active) {
                // Push the ACTIVE property through setBlock so the light level re-evaluates;
                // the block entity survives because the block and BE type are unchanged.
                syncState = state.setValue(CelestialConduitBlock.ACTIVE, be.active);
                level.setBlock(pos, syncState, 3);
            }
            level.sendBlockUpdated(pos, syncState, syncState, 3);
        }
    }

    /** Client ticker: rotation and frame-particle stream, mirroring ConduitBlockEntity#clientTick. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, CelestialConduitBlockEntity be) {
        be.tickCount++;
        if (be.active) {
            be.activeRotation++;
        }

        CelestialVariant variant = be.variant();
        if (variant == null || !be.active) {
            return;
        }

        if (be.tickCount % CHECK_INTERVAL == 0 || be.cachedFramePositions == null) {
            be.cachedFramePositions = CelestialFrame.matchingFramePositions(level, pos, variant);
        }

        // DustParticleOptions takes a packed 0xRRGGBB colour, which tintColor() already is.
        int tint = variant.tintColor();
        DustParticleOptions dust = new DustParticleOptions(tint, 1.0F);
        spawnFrameParticles(level, pos, be.cachedFramePositions, dust, be.tickCount, level.getRandom());
    }

    /** Frame-block half of vanilla's animationTick: particles flowing from each frame block
     * into the conduit. The hunt-target half is not reproduced (the target is not synced). */
    private static void spawnFrameParticles(Level level, BlockPos pos, List<BlockPos> frameBlocks,
                                             DustParticleOptions dust, int tickCount, RandomSource random) {
        double bob = Mth.sin((tickCount + 35) * 0.1F) / 2.0F + 0.5F;
        bob = (bob * bob + bob) * 0.3F;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 1.5 + bob;
        double cz = pos.getZ() + 0.5;

        for (BlockPos framePos : frameBlocks) {
            if (random.nextInt(50) != 0) {
                continue;
            }
            BlockPos rel = framePos.subtract(pos);
            float vx = -0.5F + random.nextFloat() + rel.getX();
            float vy = -2.0F + random.nextFloat() + rel.getY();
            float vz = -0.5F + random.nextFloat() + rel.getZ();
            level.addParticle(dust, cx, cy, cz, vx, vy, vz);
        }
    }

    /** Runs before the block entity is discarded when the conduit block is broken or replaced. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel serverLevel) {
            onRemoved(serverLevel);
        }
    }

    public void onRemoved(ServerLevel level) {
        // Same key tick() registered under: pair id, or positional key when unpaired. Only removed if
        // the record still points here, so another conduit sharing the id keeps its record.
        java.util.UUID registryKey = CelestialConduitRegistry.keyFor(pairId, level.dimension(), getBlockPos());
        CelestialConduitRegistry registry = CelestialConduitRegistry.get(level);
        boolean ownsRecord = registry.get(registryKey)
                .map(record -> record.dimension().equals(level.dimension()) && record.pos().equals(getBlockPos()))
                .orElse(false);
        if (ownsRecord) {
            registry.removeConduit(registryKey);
        }
        if (CelestialDebug.VERBOSE) {
            CelestialDebug.log("conduit removed at {} {} - unregistered key {} ({})",
                    level.dimension().identifier(), getBlockPos(), registryKey, pairId != null ? "paired" : "unpaired");
        }
    }

    // Sync saveAdditional's data (active, pairId) to clients for the renderer, as vanilla's conduit does.
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    // The pair id travels as the celestial_pair_id component: read from the item when placed, and
    // written to the drop by the block's loot table (copy_components), so a moved conduit keeps
    // its amulets.
    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        UUID fromItem = components.get(ModDataComponents.CELESTIAL_PAIR_ID.get());
        if (fromItem != null) {
            pairId = fromItem;
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (pairId != null) {
            components.set(ModDataComponents.CELESTIAL_PAIR_ID.get(), pairId);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        pairId = input.read("PairId", UUIDUtil.CODEC).orElse(null);
        active = input.getBooleanOr("Active", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (pairId != null) {
            output.store("PairId", UUIDUtil.CODEC, pairId);
        }
        output.putBoolean("Active", active);
    }
}
