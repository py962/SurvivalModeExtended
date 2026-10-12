package com.terrainlab.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.client.model.animal.parrot.ParrotModel;
import net.minecraft.util.Mth;

/**
 * Turkey vulture model based on ParrotModel's poses: a hunched body, long wings with
 * folding tips, a short neck with a feathered ruff and a small hooked beak. Leans
 * further forward in flight. Geometry is placeholder art pending a Blockbench pass.
 */
public class VultureModel extends EntityModel<ParrotRenderState> {

    /** Wing-tip rotation when folded (tips point back over the tail). */
    private static final float WING_TIP_FOLD = 2.0F;
    /** Extra outward wing angle in flight so the full span opens out. */
    private static final float WING_SPREAD = 0.95F;
    private ModelPart leftWingTip;
    private ModelPart rightWingTip;
    private static final float FLAP_DAMPING = 0.3F;
    // Extra forward pitch in flight, on top of the resting body/neck tilt.
    private static final float FLIGHT_FORWARD_LEAN = 0.35F;

    private final ModelPart body;
    private final ModelPart tail;
    private final ModelPart leftWing;
    private final ModelPart rightWing;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;

    public VultureModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.tail = root.getChild("tail");
        this.leftWing = root.getChild("left_wing");
        this.rightWing = root.getChild("right_wing");
        this.leftWingTip = this.leftWing.getChild("left_wing_tip");
        this.rightWingTip = this.rightWing.getChild("right_wing_tip");
        this.neck = root.getChild("neck");
        this.head = this.neck.getChild("head");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
    }

    /** Builds the 48x32 layer. */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 6).addBox(-2.5F, 0.0F, -3.0F, 5.0F, 7.0F, 6.0F),
                PartPose.offsetAndRotation(0.0F, 14.5F, -0.5F, 0.3491F, 0.0F, 0.0F));

        root.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(0, 19).addBox(-2.0F, -0.5F, 0.0F, 4.0F, 1.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, 20.0F, 2.5F, 0.5F, 0.0F, 0.0F));

        PartDefinition leftWingDef = root.addOrReplaceChild("left_wing",
                CubeListBuilder.create().texOffs(22, 6).mirror().addBox(-0.5F, 0.0F, -3.0F, 1.0F, 8.0F, 6.0F),
                PartPose.offsetAndRotation(3.0F, 14.8F, 0.0F, 0.35F, 0.0F, -0.1F));
        PartDefinition rightWingDef = root.addOrReplaceChild("right_wing",
                CubeListBuilder.create().texOffs(22, 6).addBox(-0.5F, 0.0F, -3.0F, 1.0F, 8.0F, 6.0F),
                PartPose.offsetAndRotation(-3.0F, 14.8F, 0.0F, 0.35F, 0.0F, 0.1F));

        PartDefinition neck = root.addOrReplaceChild("neck",
                CubeListBuilder.create().texOffs(18, 0).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 15.5F, -3.0F, 0.5236F, 0.0F, 0.0F));

        neck.addOrReplaceChild("ruff",
                CubeListBuilder.create().texOffs(26, 0).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 2.0F, 3.0F),
                PartPose.ZERO);

        PartDefinition vultureHead = neck.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -3.0F, -2.0F, 3.0F, 3.0F, 3.0F),
                PartPose.offsetAndRotation(0.0F, -3.0F, -0.5F, 0.2618F, 0.0F, 0.0F));

        vultureHead.addOrReplaceChild("beak",
                CubeListBuilder.create().texOffs(12, 0).addBox(-0.5F, -0.5F, -1.5F, 1.0F, 1.0F, 2.0F),
                PartPose.offset(0.0F, -1.8F, -2.0F));

        CubeListBuilder leg = CubeListBuilder.create().texOffs(16, 19).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F);
        root.addOrReplaceChild("left_leg", leg, PartPose.offset(1.2F, 21.0F, -0.5F));
        root.addOrReplaceChild("right_leg", leg, PartPose.offset(-1.2F, 21.0F, -0.5F));

        // Wing tips: folded back along the body when perched, straightened in flight.
        CubeListBuilder tip = CubeListBuilder.create().texOffs(36, 6).addBox(-0.5F, 0.0F, -2.0F, 1.0F, 6.0F, 4.0F);
        leftWingDef.addOrReplaceChild("left_wing_tip", tip, PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, WING_TIP_FOLD, 0.0F, 0.0F));
        rightWingDef.addOrReplaceChild("right_wing_tip", tip, PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, WING_TIP_FOLD, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 48, 32);
    }

    @Override
    public void setupAnim(ParrotRenderState state) {
        super.setupAnim(state);
        this.neck.xRot = 0.5236F + state.xRot * (float) (Math.PI / 180.0) * 0.5F;
        this.head.yRot = state.yRot * (float) (Math.PI / 180.0);

        switch (state.pose) {
            case STANDING:
                this.body.xRot = 0.3491F; // resting tilt; no extra lean while walking
                this.leftLeg.xRot = Mth.cos(state.walkAnimationPos * 0.6662F) * 1.0F * state.walkAnimationSpeed;
                this.rightLeg.xRot = Mth.cos(state.walkAnimationPos * 0.6662F + (float) Math.PI) * 1.0F * state.walkAnimationSpeed;
                break;
            case FLYING:
            case ON_SHOULDER:
            default:
                // ON_SHOULDER and unlisted poses share the flight lean.
                this.body.xRot = 0.3491F + FLIGHT_FORWARD_LEAN;
                this.neck.xRot += FLIGHT_FORWARD_LEAN;
                float dampedFlap = state.flapAngle * FLAP_DAMPING;
                this.leftWing.zRot = -0.4363F - dampedFlap;
                this.rightWing.zRot = 0.4363F + dampedFlap;
                break;
            case SITTING:
                this.body.xRot = 0.3491F;
                break;
            case PARTY:
                this.body.xRot = 0.3491F;
                float f = Mth.cos(state.ageInTicks);
                float f1 = Mth.sin(state.ageInTicks);
                this.head.x += f;
                this.head.y += f1;
                this.leftWing.zRot = -0.4363F - (state.flapAngle * FLAP_DAMPING);
                this.rightWing.zRot = 0.4363F + (state.flapAngle * FLAP_DAMPING);
                break;
        }

        // Wing tips straighten and the wings open out in flight.
        boolean flying = state.pose == ParrotModel.Pose.FLYING;
        this.leftWingTip.xRot = flying ? 0.0F : WING_TIP_FOLD;
        this.rightWingTip.xRot = flying ? 0.0F : WING_TIP_FOLD;
        if (flying) {
            this.leftWing.zRot -= WING_SPREAD;
            this.rightWing.zRot += WING_SPREAD;
            levelFlight(state);
        }
    }

    private static final float HALF_PI = (float) (Math.PI / 2.0);

    /**
     * Flight: body level with the ground, neck stretched forward with the head looking slightly
     * down, wings out from mid-body (keeping their spread and flap), tail flat behind, legs trailing.
     */
    private void levelFlight(ParrotRenderState state) {
        this.body.xRot = HALF_PI;

        this.neck.xRot = 1.2F + state.xRot * Mth.DEG_TO_RAD * 0.5F;
        this.head.xRot = 0.3F - 1.2F;

        this.leftWing.xRot = 0.0F;
        this.rightWing.xRot = 0.0F;
        this.leftWing.y = 13.0F;
        this.rightWing.y = 13.0F;
        this.leftWing.z = 2.5F;
        this.rightWing.z = 2.5F;

        this.tail.y = 14.5F;
        this.tail.z = 6.5F;
        this.tail.xRot = 0.05F;

        this.leftLeg.y = 17.0F;
        this.rightLeg.y = 17.0F;
        this.leftLeg.z = 4.5F;
        this.rightLeg.z = 4.5F;
        this.leftLeg.xRot = HALF_PI;
        this.rightLeg.xRot = HALF_PI;
    }
}
