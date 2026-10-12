package com.terrainlab.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

/**
 * Outlaws Hideout garrison: a Pillager with a cosmetic outfit whose mounted
 * patrol rides a loop 10-30 blocks outside its hideout when not fighting.
 * <p>
 * Some riders are given the hideout's front tunnel instead: they ride up and down it, from the
 * cavern outside the doors to the open ground beyond the mouth, pausing at each end.
 * <p>
 * The goals live on the rider because a mounted Mob steers its vehicle
 * ({@code Mob#getNavigation} returns the vehicle's navigation).
 */
public class OutlawRaiderEntity extends Pillager {

    public static final int CIRCLE_MIN_DISTANCE = 10;
    public static final int CIRCLE_MAX_DISTANCE = 30;
    private static final float CIRCLE_SPEED = 0.55F;
    /** Approximate spacing between loop waypoints, in blocks. */
    private static final float WAYPOINT_SPACING = 7.0F;

    private static final String TAG_HIDEOUT_X = "OutlawHideoutX";
    private static final String TAG_HIDEOUT_Z = "OutlawHideoutZ";
    private static final String TAG_HALF_X = "OutlawHideoutHalfX";
    private static final String TAG_HALF_Z = "OutlawHideoutHalfZ";
    private static final String TAG_DISTANCE = "OutlawCircleDistance";
    private static final String TAG_ANGLE = "OutlawCircleAngle";
    private static final String TAG_ROUTE = "OutlawTunnelRoute";
    private static final float TUNNEL_SPEED = 0.6F;

    @Nullable
    private BlockPos hideoutCentre;
    private int hideoutHalfX;
    private int hideoutHalfZ;
    private int circleDistance = 20;
    private float circleAngle;
    /** Tunnel patrol route (cavern first, outside last); empty for loop riders. */
    private List<BlockPos> tunnelRoute = List.of();

    public OutlawRaiderEntity(EntityType<? extends Pillager> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * Sets the hideout to circle. Half-sizes make the distance measured from the
     * hideout's edge; distance is clamped to 10-30 and startAngle is in radians.
     */
    public void setHideout(BlockPos centre, int halfX, int halfZ, int distance, float startAngle) {
        this.hideoutCentre = centre.immutable();
        this.hideoutHalfX = halfX;
        this.hideoutHalfZ = halfZ;
        this.circleDistance = Mth.clamp(distance, CIRCLE_MIN_DISTANCE, CIRCLE_MAX_DISTANCE);
        this.circleAngle = startAngle;
    }

    /** Makes this rider patrol the front tunnel instead of the loop. */
    public void setTunnelRoute(List<BlockPos> route) {
        this.tunnelRoute = List.copyOf(route);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // Below the attack goals (2-3), above vanilla's random stroll (8).
        this.goalSelector.addGoal(6, new CircleHideoutGoal(this));
        this.goalSelector.addGoal(6, new TunnelPatrolGoal(this));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);

        if (this.hideoutCentre != null) {
            output.putInt(TAG_HIDEOUT_X, this.hideoutCentre.getX());
            output.putInt(TAG_HIDEOUT_Z, this.hideoutCentre.getZ());
            output.putInt(TAG_HALF_X, this.hideoutHalfX);
            output.putInt(TAG_HALF_Z, this.hideoutHalfZ);
            output.putInt(TAG_DISTANCE, this.circleDistance);
            output.putFloat(TAG_ANGLE, this.circleAngle);
        }
        if (!this.tunnelRoute.isEmpty()) {
            output.store(TAG_ROUTE, BlockPos.CODEC.listOf(), this.tunnelRoute);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);

        int halfX = input.getIntOr(TAG_HALF_X, -1);

        if (halfX >= 0) {
            this.hideoutCentre = new BlockPos(
                    input.getIntOr(TAG_HIDEOUT_X, 0),
                    0,
                    input.getIntOr(TAG_HIDEOUT_Z, 0));
            this.hideoutHalfX = halfX;
            this.hideoutHalfZ = input.getIntOr(TAG_HALF_Z, 0);
            this.circleDistance = input.getIntOr(TAG_DISTANCE, 20);
            this.circleAngle = input.getFloatOr(TAG_ANGLE, 0.0F);
        }
        this.tunnelRoute = input.read(TAG_ROUTE, BlockPos.CODEC.listOf()).map(List::copyOf).orElse(List.of());
    }

    /**
     * Rides between waypoints on an ellipse circleDistance blocks outside the
     * hideout footprint; unreachable waypoints are skipped so the loop never stalls.
     */
    static final class CircleHideoutGoal extends Goal {

        private final OutlawRaiderEntity outlaw;
        private int recalcTimer;

        CircleHideoutGoal(OutlawRaiderEntity outlaw) {
            this.outlaw = outlaw;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return this.outlaw.hideoutCentre != null
                    && this.outlaw.tunnelRoute.isEmpty()
                    && this.outlaw.isPassenger()
                    && this.outlaw.getTarget() == null
                    && this.outlaw.level() instanceof ServerLevel;
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void start() {
            this.recalcTimer = 0;
        }

        @Override
        public void stop() {
            this.outlaw.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (--this.recalcTimer > 0 && !this.outlaw.getNavigation().isDone()) {
                return;
            }

            this.recalcTimer = 100; // ticks; re-aim periodically in case the path stalls

            for (int attempt = 0; attempt < 4; attempt++) {
                advanceAlongLoop();

                if (headForCurrentWaypoint()) {
                    return;
                }
            }
        }

        private void advanceAlongLoop() {
            OutlawRaiderEntity o = this.outlaw;
            float radius = Math.max(o.hideoutHalfX, o.hideoutHalfZ) + o.circleDistance;
            o.circleAngle = (o.circleAngle + WAYPOINT_SPACING / radius) % Mth.TWO_PI;

            o.circleDistance = Mth.clamp(
                    o.circleDistance + o.getRandom().nextInt(3) - 1,
                    CIRCLE_MIN_DISTANCE,
                    CIRCLE_MAX_DISTANCE);
        }

        private boolean headForCurrentWaypoint() {
            OutlawRaiderEntity o = this.outlaw;
            ServerLevel level = (ServerLevel) o.level();
            BlockPos centre = o.hideoutCentre;

            int x = centre.getX() + Math.round(Mth.cos(o.circleAngle) * (o.hideoutHalfX + o.circleDistance));
            int z = centre.getZ() + Math.round(Mth.sin(o.circleAngle) * (o.hideoutHalfZ + o.circleDistance));

            if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
                return false;
            }

            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

            return o.getNavigation().moveTo(x + 0.5D, y, z + 0.5D, CIRCLE_SPEED);
        }
    }

    /**
     * Rides the tunnel route waypoint by waypoint, turning round at each end after a pause;
     * a waypoint that can't be reached is skipped so the patrol never stalls.
     */
    static final class TunnelPatrolGoal extends Goal {

        private final OutlawRaiderEntity outlaw;
        private int index;
        private int step = 1;
        private int pause;
        private int recalcTimer;

        TunnelPatrolGoal(OutlawRaiderEntity outlaw) {
            this.outlaw = outlaw;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return this.outlaw.tunnelRoute.size() >= 2
                    && this.outlaw.isPassenger()
                    && this.outlaw.getTarget() == null
                    && this.outlaw.level() instanceof ServerLevel;
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void start() {
            // Resume from the nearest waypoint.
            List<BlockPos> route = this.outlaw.tunnelRoute;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < route.size(); i++) {
                double d = route.get(i).distToCenterSqr(this.outlaw.position());
                if (d < best) {
                    best = d;
                    this.index = i;
                }
            }
            this.recalcTimer = 0;
        }

        @Override
        public void stop() {
            this.outlaw.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (this.pause > 0) {
                this.pause--;
                return;
            }
            List<BlockPos> route = this.outlaw.tunnelRoute;
            BlockPos target = route.get(this.index);
            boolean arrived = target.distToCenterSqr(this.outlaw.position()) < 4.0D * 4.0D;

            if (arrived || --this.recalcTimer <= 0 || this.outlaw.getNavigation().isDone()) {
                if (arrived) {
                    advance(route.size());
                }
                this.recalcTimer = 80;
                for (int attempt = 0; attempt < 3; attempt++) {
                    BlockPos next = route.get(this.index);
                    if (this.outlaw.getNavigation().moveTo(next.getX() + 0.5D, next.getY(), next.getZ() + 0.5D,
                            TUNNEL_SPEED)) {
                        return;
                    }
                    advance(route.size());
                }
            }
        }

        /** Next waypoint; at either end, turn round and wait a while. */
        private void advance(int size) {
            int next = this.index + this.step;
            if (next < 0 || next >= size) {
                this.step = -this.step;
                this.pause = 100 + this.outlaw.getRandom().nextInt(200);
                next = this.index + this.step;
            }
            this.index = Mth.clamp(next, 0, size - 1);
        }
    }
}
