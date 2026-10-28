package com.terrainlab.mixin;

import com.terrainlab.client.WolfCarpetSleepClient;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.animal.wolf.BabyWolfModel;
import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lying-down pose for a wolf asleep on a carpet (WolfCarpetSleepHandler): body flat on the ground,
 * front legs stretched forward with the head resting on them, hind legs tucked along the flanks, tail
 * curled forward along one side, slow breathing. Applied after vanilla's setupAnim (which resets the
 * parts every frame), only when the render-state flag is set, so normal and sitting wolves are untouched.
 * Model space: y points down, the ground is at y = 24.
 */
@Mixin(WolfModel.class)
public abstract class WolfModelCarpetSleepMixin {

    @Shadow @Final protected ModelPart head;
    @Shadow @Final protected ModelPart body;
    @Shadow @Final protected ModelPart rightHindLeg;
    @Shadow @Final protected ModelPart leftHindLeg;
    @Shadow @Final protected ModelPart rightFrontLeg;
    @Shadow @Final protected ModelPart leftFrontLeg;
    @Shadow @Final protected ModelPart tail;

    @Inject(
            method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/WolfRenderState;)V",
            at = @At("TAIL"),
            require = 1
    )
    private void terrainlab$carpetSleepPose(WolfRenderState state, CallbackInfo ci) {
        if (!Boolean.TRUE.equals(state.getRenderData(WolfCarpetSleepClient.CARPET_ASLEEP)) || state.isSitting) {
            return;
        }
        float breath = Mth.sin(state.ageInTicks * 0.08F) * 0.15F;
        float flat = -Mth.HALF_PI; // leg/tail pointing forward, lying along the ground

        if ((Object) this instanceof BabyWolfModel) {
            this.body.y += 3.0F + breath;

            this.head.y += 2.0F;
            this.head.z -= 1.0F;
            this.head.xRot = 0.0F;
            this.head.yRot = 0.0F;

            terrainlab$foldLeg(this.rightFrontLeg, flat, 2.0F, 0.0F);
            terrainlab$foldLeg(this.leftFrontLeg, flat, 2.0F, 0.0F);
            terrainlab$foldLeg(this.rightHindLeg, flat, 2.0F, -2.0F);
            terrainlab$foldLeg(this.leftHindLeg, flat, 2.0F, 2.0F);

            // The baby tail cube hangs from a child rotated by -3.1 rad; this makes it point forward.
            this.tail.y += 4.0F;
            this.tail.z += 1.0F;
            this.tail.x -= 1.0F;
            this.tail.xRot = Mth.HALF_PI + 3.1F;
            this.tail.yRot = 0.9F;
            return;
        }

        ModelPart root = ((Model<?>) (Object) this).root();
        this.body.y += 7.0F + breath;
        if (root.hasChild("upper_body")) {
            root.getChild("upper_body").y += 7.0F + breath;
        }

        // Head lowered onto the outstretched front paws.
        this.head.y += 5.5F;
        this.head.z -= 1.0F;
        this.head.xRot = 0.0F;
        this.head.yRot = 0.0F;

        terrainlab$foldLeg(this.rightFrontLeg, flat, 7.0F, 0.0F);
        terrainlab$foldLeg(this.leftFrontLeg, flat, 7.0F, 0.0F);
        terrainlab$foldLeg(this.rightHindLeg, flat, 7.0F, -2.0F);
        terrainlab$foldLeg(this.leftHindLeg, flat, 7.0F, 2.0F);

        // Tail laid on the ground, curled forward along the right flank.
        this.tail.y += 11.0F;
        this.tail.z += 1.0F;
        this.tail.xRot = flat;
        this.tail.yRot = 0.7F;
    }

    /** Lays a leg flat on the ground: rotate it horizontal, drop the pivot, optionally push it out sideways. */
    @Unique
    private static void terrainlab$foldLeg(ModelPart leg, float xRot, float drop, float outward) {
        leg.xRot = xRot;
        leg.y += drop;
        leg.x += outward;
    }
}
