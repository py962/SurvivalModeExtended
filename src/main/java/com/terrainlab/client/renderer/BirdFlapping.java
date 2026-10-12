package com.terrainlab.client.renderer;

import net.minecraft.client.model.animal.parrot.ParrotModel;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.phys.Vec3;

/**
 * Shared wing animation for the owl, vulture and goose renderers.
 * <ul>
 *   <li>One wingbeat per second in flight (vanilla parrots beat about six times a second). The
 *   beat is a sine on the render clock, scaled by the entity's flapSpeed so it eases in on
 *   take-off and out on landing.</li>
 *   <li>A bird that is off the ground but not actually moving (settled on leaves or a perch edge,
 *   which vanilla counts as flying) is shown standing with its wings folded.</li>
 *   <li>Sitting and standing birds never flap.</li>
 * </ul>
 */
final class BirdFlapping {

    /** Radians per tick: one full beat every 20 ticks. */
    private static final float FLAP_RATE = (float) (Math.PI * 2.0 / 20.0);
    /** Below this speed (blocks per tick, squared) an airborne bird counts as perched. */
    private static final double STILL_SPEED_SQR = 0.02 * 0.02;

    private BirdFlapping() {
    }

    /** Call after super.extractRenderState, which sets ageInTicks. */
    static void apply(Parrot bird, ParrotRenderState state, float partialTick) {
        ParrotModel.Pose pose = ParrotModel.getPose(bird);
        if (pose == ParrotModel.Pose.FLYING) {
            Vec3 motion = bird.getDeltaMovement();
            if (motion.lengthSqr() < STILL_SPEED_SQR) {
                pose = ParrotModel.Pose.STANDING;
            }
        }
        state.pose = pose;

        if (pose == ParrotModel.Pose.FLYING) {
            float speed = Mth.lerp(partialTick, bird.oFlapSpeed, bird.flapSpeed);
            state.flapAngle = (Mth.sin(state.ageInTicks * FLAP_RATE) + 1.0F) * speed;
        } else {
            state.flapAngle = 0.0F;
        }
    }
}
