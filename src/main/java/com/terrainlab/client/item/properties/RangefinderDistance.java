package com.terrainlab.client.item.properties;

import com.mojang.serialization.MapCodec;
import com.terrainlab.item.rangefinder.BandedDistance;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Distance to the target carried in this stack's LodestoneTracker
 * component (copied from the input Compass at craft time - see
 * RangefinderCompassRecipe), or world spawn if the tracker is absent/
 * untracked - matching vanilla's own plain-compass default behavior.
 *
 * Horizontal (X/Z) distance only. If the target is in a different
 * dimension than the holder, routes to the top/off-scale band.
 */
public class RangefinderDistance implements RangeSelectItemModelProperty {

    public static final MapCodec<RangefinderDistance> MAP_CODEC =
            MapCodec.unit(RangefinderDistance::new);

    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        if (level == null || owner == null) {
            return 0.0F;
        }

        BlockPos targetPos;
        LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
        Optional<GlobalPos> target = tracker != null ? tracker.target() : Optional.empty();

        if (target.isPresent()) {
            if (target.get().dimension() != level.dimension()) {
                return BandedDistance.getBandFraction(Double.MAX_VALUE);
            }
            targetPos = target.get().pos();
        } else {
            targetPos = level.getRespawnData().pos();
        }

        Vec3 ownerPos = owner.position();
        double dx = targetPos.getX() - ownerPos.x();
        double dz = targetPos.getZ() - ownerPos.z();
        double distance = Math.sqrt(dx * dx + dz * dz);

        return BandedDistance.getBandFraction(distance);
    }

    @Override
    public MapCodec<? extends RangeSelectItemModelProperty> type() {
        return MAP_CODEC;
    }
}
