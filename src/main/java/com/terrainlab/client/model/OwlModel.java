
package com.terrainlab.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.animal.parrot.ParrotModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.util.Mth;

public class OwlModel extends EntityModel<ParrotRenderState> {

    private static final float WING_TIP_FOLD = 1.9F;
    private static final float WING_SPREAD = 1.1F;
    private static final float FLAP_DAMPING = 0.5F;
    private static final float BODY_BASE_XROT = 0.2F;
    private static final float FLIGHT_FORWARD_LEAN = 0.35F;

    private final ModelPart body;
    private final ModelPart tail;
    private final ModelPart leftWing;
    private final ModelPart rightWing;
    private final ModelPart leftWingTip;
    private final ModelPart rightWingTip;
    private final ModelPart head;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;

    public OwlModel(ModelPart root) {
        super(root);

        this.body = root.getChild("body");
        this.tail = root.getChild("tail");

        this.leftWing = root.getChild("left_wing");
        this.rightWing = root.getChild("right_wing");

        this.leftWingTip = this.leftWing.getChild("left_wing_tip");
        this.rightWingTip = this.rightWing.getChild("right_wing_tip");

        this.head = root.getChild("head");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(0, 10)
                        .addBox(-2.5F, 0.0F, -2.0F, 5.0F, 7.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, 15.0F, 0.0F, 0.12F, 0.0F, 0.0F)
        );

        root.addOrReplaceChild(
                "tail",
                CubeListBuilder.create()
                        .texOffs(32, 10)
                        .addBox(-1.5F, 0.0F, 0.0F, 3.0F, 3.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, 20.0F, 1.8F, 0.5F, 0.0F, 0.0F)
        );

        PartDefinition leftWingDef = root.addOrReplaceChild(
                "left_wing",
                CubeListBuilder.create()
                        .texOffs(22, 10)
                        .mirror()
                        .addBox(-0.5F, 0.0F, -2.0F, 1.0F, 6.0F, 4.0F),
                PartPose.offset(3.0F, 15.5F, 0.2F)
        );

        PartDefinition rightWingDef = root.addOrReplaceChild(
                "right_wing",
                CubeListBuilder.create()
                        .texOffs(22, 10)
                        .addBox(-0.5F, 0.0F, -2.0F, 1.0F, 6.0F, 4.0F),
                PartPose.offset(-3.0F, 15.5F, 0.2F)
        );

        PartDefinition head = root.addOrReplaceChild(
                "head",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-3.0F, -5.0F, -2.5F, 6.0F, 5.0F, 5.0F),
                PartPose.offset(0.0F, 15.0F, -0.3F)
        );

        head.addOrReplaceChild(
                "beak",
                CubeListBuilder.create()
                        .texOffs(28, 0)
                        .addBox(-0.5F, 0.0F, -1.0F, 1.0F, 1.0F, 1.0F),
                PartPose.offset(0.0F, -2.0F, -2.5F)
        );

        head.addOrReplaceChild(
                "left_tuft",
                CubeListBuilder.create()
                        .texOffs(24, 0)
                        .mirror()
                        .addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F),
                PartPose.offsetAndRotation(2.2F, -5.0F, -1.2F, -0.2F, 0.0F, 0.35F)
        );

        head.addOrReplaceChild(
                "right_tuft",
                CubeListBuilder.create()
                        .texOffs(24, 0)
                        .addBox(-0.5F, -2.0F, -0.5F, 1.0F, 2.0F, 1.0F),
                PartPose.offsetAndRotation(-2.2F, -5.0F, -1.2F, -0.2F, 0.0F, -0.35F)
        );

        CubeListBuilder leg = CubeListBuilder.create()
                .texOffs(0, 22)
                .addBox(-0.5F, 0.0F, -1.0F, 1.0F, 2.0F, 2.0F);

        root.addOrReplaceChild(
                "left_leg",
                leg,
                PartPose.offset(1.2F, 22.0F, -0.3F)
        );

        root.addOrReplaceChild(
                "right_leg",
                leg,
                PartPose.offset(-1.2F, 22.0F, -0.3F)
        );

        CubeListBuilder tip = CubeListBuilder.create()
                .texOffs(40, 10)
                .addBox(-0.5F, 0.0F, -1.5F, 1.0F, 4.0F, 3.0F);

        leftWingDef.addOrReplaceChild(
                "left_wing_tip",
                tip,
                PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, WING_TIP_FOLD, 0.0F, 0.0F)
        );

        rightWingDef.addOrReplaceChild(
                "right_wing_tip",
                tip,
                PartPose.offsetAndRotation(0.0F, 6.0F, 0.0F, WING_TIP_FOLD, 0.0F, 0.0F)
        );

        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(ParrotRenderState state) {
        super.setupAnim(state);

        /*
         * Reset all animated parts to their original geometry positions.
         * This prevents animation offsets from carrying over between frames
         * when the owl changes pose.
         */

        this.body.x = 0.0F;
        this.body.y = 15.0F;
        this.body.z = 0.0F;
        this.body.xRot = BODY_BASE_XROT;
        this.body.yRot = 0.0F;
        this.body.zRot = 0.0F;

        this.tail.x = 0.0F;
        this.tail.y = 20.0F;
        this.tail.z = 1.8F;
        this.tail.xRot = 0.5F;
        this.tail.yRot = 0.0F;
        this.tail.zRot = 0.0F;

        this.leftWing.x = 3.0F;
        this.leftWing.y = 15.5F;
        this.leftWing.z = 0.2F;
        this.leftWing.xRot = 0.0F;
        this.leftWing.yRot = 0.0F;
        this.leftWing.zRot = 0.0F;

        this.rightWing.x = -3.0F;
        this.rightWing.y = 15.5F;
        this.rightWing.z = 0.2F;
        this.rightWing.xRot = 0.0F;
        this.rightWing.yRot = 0.0F;
        this.rightWing.zRot = 0.0F;

        this.leftWingTip.xRot = WING_TIP_FOLD;
        this.leftWingTip.yRot = 0.0F;
        this.leftWingTip.zRot = 0.0F;

        this.rightWingTip.xRot = WING_TIP_FOLD;
        this.rightWingTip.yRot = 0.0F;
        this.rightWingTip.zRot = 0.0F;

        this.head.x = 0.0F;
        this.head.y = 15.0F;
        this.head.z = -0.3F;
        this.head.xRot = 0.0F;
        this.head.yRot = 0.0F;
        this.head.zRot = 0.0F;

        this.leftLeg.x = 1.2F;
        this.leftLeg.y = 22.0F;
        this.leftLeg.z = -0.3F;
        this.leftLeg.xRot = 0.0F;
        this.leftLeg.yRot = 0.0F;
        this.leftLeg.zRot = 0.0F;

        this.rightLeg.x = -1.2F;
        this.rightLeg.y = 22.0F;
        this.rightLeg.z = -0.3F;
        this.rightLeg.xRot = 0.0F;
        this.rightLeg.yRot = 0.0F;
        this.rightLeg.zRot = 0.0F;

        this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
        this.head.yRot = state.yRot * Mth.DEG_TO_RAD;

        float dampedFlap = state.flapAngle * FLAP_DAMPING;

        switch (state.pose) {
            case STANDING:
                this.leftLeg.xRot =
                        Mth.cos(state.walkAnimationPos * 0.6662F)
                                * 1.4F * state.walkAnimationSpeed;

                this.rightLeg.xRot =
                        Mth.cos(state.walkAnimationPos * 0.6662F + (float) Math.PI)
                                * 1.4F * state.walkAnimationSpeed;
                break;

            case FLYING:
                this.body.xRot = BODY_BASE_XROT + FLIGHT_FORWARD_LEAN;
                this.head.xRot += FLIGHT_FORWARD_LEAN;

                this.leftLeg.xRot = (float) (Math.PI * 2.0 / 9.0);
                this.rightLeg.xRot = (float) (Math.PI * 2.0 / 9.0);

                this.leftWing.zRot = -0.0873F - dampedFlap;
                this.rightWing.zRot = 0.0873F + dampedFlap;

                this.leftWing.zRot -= WING_SPREAD;
                this.rightWing.zRot += WING_SPREAD;

                this.leftWingTip.xRot = 0.0F;
                this.rightWingTip.xRot = 0.0F;
                break;

            case SITTING:
                this.body.y += 1.0F;
                this.head.y += 1.0F;

                this.tail.xRot = (float) (Math.PI / 6.0);
                this.tail.y += 1.0F;

                this.leftWing.zRot = -0.0873F;
                this.rightWing.zRot = 0.0873F;

                this.leftWing.y += 1.0F;
                this.rightWing.y += 1.0F;

                this.leftLeg.y += 1.0F;
                this.rightLeg.y += 1.0F;

                this.leftLeg.xRot = 0.5F;
                this.rightLeg.xRot = 0.5F;
                break;

            case PARTY:
                float f = Mth.cos(state.ageInTicks);
                float f1 = Mth.sin(state.ageInTicks);

                this.head.x += f;
                this.head.y += f1;
                this.head.xRot = 0.0F;
                this.head.yRot = 0.0F;
                this.head.zRot = Mth.sin(state.ageInTicks) * 0.4F;

                this.body.x += f;
                this.body.y += f1;

                this.leftWing.zRot = -0.0873F - dampedFlap;
                this.rightWing.zRot = 0.0873F + dampedFlap;

                this.leftWing.x += f;
                this.leftWing.y += f1;
                this.rightWing.x += f;
                this.rightWing.y += f1;

                this.tail.x += f;
                this.tail.y += f1;
                break;

            case ON_SHOULDER:
            default:
                this.body.xRot = BODY_BASE_XROT + FLIGHT_FORWARD_LEAN;
                this.head.xRot += FLIGHT_FORWARD_LEAN;

                this.leftWing.zRot = -0.0873F - dampedFlap;
                this.rightWing.zRot = 0.0873F + dampedFlap;
                break;
        }

        if (state.pose == ParrotModel.Pose.FLYING) {
            levelFlight(state);
        }
    }

    /**
     * Flight: body level with the ground, hanging back from the shoulders, head forward on top of
     * it looking where the owl looks, wings out from mid-body, tail at the back and legs trailing.
     * The wings keep their spread and flap from the switch above.
     */
    private void levelFlight(ParrotRenderState state) {
        this.body.xRot = HALF_PI;

        this.head.z = -1.0F;
        this.head.xRot = state.xRot * Mth.DEG_TO_RAD;

        this.leftWing.y = 13.5F;
        this.rightWing.y = 13.5F;
        this.leftWing.z = 2.0F;
        this.rightWing.z = 2.0F;

        this.tail.y = 15.0F;
        this.tail.z = 7.0F;
        this.tail.xRot = HALF_PI - 0.2F;

        this.leftLeg.y = 17.0F;
        this.rightLeg.y = 17.0F;
        this.leftLeg.z = 5.0F;
        this.rightLeg.z = 5.0F;
        this.leftLeg.xRot = HALF_PI;
        this.rightLeg.xRot = HALF_PI;
    }

    private static final float HALF_PI = (float) (Math.PI / 2.0);
}
