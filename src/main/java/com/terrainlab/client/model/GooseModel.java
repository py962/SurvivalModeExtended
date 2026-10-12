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
 * Goose model with a two-segment neck (lower_neck + head) for an upright posture, folding
 * wing tips, and a forward lean in flight. Geometry is placeholder art pending a
 * Blockbench pass.
 */
public class GooseModel extends EntityModel<ParrotRenderState> {

    /** Wing-tip rotation when folded (tips point back over the tail). */
    private static final float WING_TIP_FOLD = 2.0F;
    /** Extra outward wing angle in flight so the full span opens out. */
    private static final float WING_SPREAD = 1.1F;
    private ModelPart leftWingTip;
    private ModelPart rightWingTip;
    private static final float FLAP_DAMPING = 0.8F;
    // Smaller than Vulture/Owl's 0.35F; geese fly fairly level.
    private static final float FLIGHT_FORWARD_LEAN = 0.3F;

    private final ModelPart body;
    private final ModelPart tail;
    private final ModelPart leftWing;
    private final ModelPart rightWing;
    private final ModelPart lowerNeck;
    private final ModelPart head;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;

    public GooseModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.tail = root.getChild("tail");
        this.leftWing = root.getChild("left_wing");
        this.rightWing = root.getChild("right_wing");
        this.leftWingTip = this.leftWing.getChild("left_wing_tip");
        this.rightWingTip = this.rightWing.getChild("right_wing_tip");
        this.lowerNeck = root.getChild("lower_neck");
        this.head = this.lowerNeck.getChild("head");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
    }

    /** Builds the 48x32 layer: a long, level body with whole-pixel-sized parts. */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 9).addBox(-2.5F, 0.0F, -4.5F, 5.0F, 5.0F, 9.0F),
                PartPose.offset(0.0F, 15.0F, 0.5F));

        root.addOrReplaceChild("tail",
                CubeListBuilder.create().texOffs(30, 0).addBox(-2.0F, -0.5F, 0.0F, 4.0F, 1.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 15.8F, 4.8F, 0.35F, 0.0F, 0.0F));

        PartDefinition leftWingDef = root.addOrReplaceChild("left_wing",
                CubeListBuilder.create().texOffs(28, 9).mirror().addBox(-0.5F, 0.0F, -3.5F, 1.0F, 4.0F, 7.0F),
                PartPose.offsetAndRotation(2.8F, 15.2F, 1.0F, 0.0F, 0.0F, -0.08F));
        PartDefinition rightWingDef = root.addOrReplaceChild("right_wing",
                CubeListBuilder.create().texOffs(28, 9).addBox(-0.5F, 0.0F, -3.5F, 1.0F, 4.0F, 7.0F),
                PartPose.offsetAndRotation(-2.8F, 15.2F, 1.0F, 0.0F, 0.0F, 0.08F));

        PartDefinition lowerNeck = root.addOrReplaceChild("lower_neck",
                CubeListBuilder.create().texOffs(22, 0).addBox(-1.0F, -7.0F, -1.0F, 2.0F, 7.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 16.5F, -3.2F, -0.3491F, 0.0F, 0.0F));

        PartDefinition gooseHead = lowerNeck.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -3.0F, -2.5F, 3.0F, 3.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, -6.5F, 0.0F, 0.3491F, 0.0F, 0.0F));

        gooseHead.addOrReplaceChild("beak",
                CubeListBuilder.create().texOffs(14, 0).addBox(-1.0F, -0.5F, -2.0F, 2.0F, 1.0F, 2.0F),
                PartPose.offset(0.0F, -1.2F, -2.5F));

        CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 23)
                .addBox(-0.5F, 0.0F, -1.0F, 1.0F, 4.0F, 2.0F);
        root.addOrReplaceChild("left_leg", leg, PartPose.offset(1.2F, 20.0F, 1.0F));
        root.addOrReplaceChild("right_leg", leg, PartPose.offset(-1.2F, 20.0F, 1.0F));

        // Wing tips: folded back along the body when perched, straightened in flight.
        CubeListBuilder tip = CubeListBuilder.create().texOffs(6, 23).addBox(-0.5F, 0.0F, -1.5F, 1.0F, 6.0F, 3.0F);
        leftWingDef.addOrReplaceChild("left_wing_tip", tip, PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, WING_TIP_FOLD, 0.0F, 0.0F));
        rightWingDef.addOrReplaceChild("right_wing_tip", tip, PartPose.offsetAndRotation(0.0F, 4.0F, 0.0F, WING_TIP_FOLD, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, 48, 32);
    }

    @Override
    public void setupAnim(ParrotRenderState state) {
        super.setupAnim(state);
        this.lowerNeck.xRot = -0.3491F + state.xRot * (float) (Math.PI / 180.0) * 0.5F;
        this.head.yRot = state.yRot * (float) (Math.PI / 180.0);

        switch (state.pose) {
            case STANDING:
                this.body.xRot = 0.0F;
                this.leftLeg.xRot = Mth.cos(state.walkAnimationPos * 0.6662F) * 1.4F * state.walkAnimationSpeed;
                this.rightLeg.xRot = Mth.cos(state.walkAnimationPos * 0.6662F + (float) Math.PI) * 1.4F * state.walkAnimationSpeed;
                break;
            case FLYING:
            case ON_SHOULDER:
            default:
                this.body.xRot = FLIGHT_FORWARD_LEAN;
                this.lowerNeck.xRot += FLIGHT_FORWARD_LEAN;
                float dampedFlap = state.flapAngle * FLAP_DAMPING;
                this.leftWing.zRot = -0.2618F - dampedFlap;
                this.rightWing.zRot = 0.2618F + dampedFlap;
                break;
            case SITTING:
                this.body.xRot = 0.0F;
                break;
            case PARTY:
                this.body.xRot = 0.0F;
                float f = Mth.cos(state.ageInTicks);
                float f1 = Mth.sin(state.ageInTicks);
                this.head.x += f;
                this.head.y += f1;
                this.leftWing.zRot = -0.2618F - (state.flapAngle * FLAP_DAMPING);
                this.rightWing.zRot = 0.2618F + (state.flapAngle * FLAP_DAMPING);
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
     * Flight: body level (no forward lean), neck stretched out in front with the head level,
     * legs trailing behind. The wings keep their spread and flap.
     */
    private void levelFlight(ParrotRenderState state) {
        this.body.xRot = 0.0F;

        this.lowerNeck.xRot = 1.25F + state.xRot * Mth.DEG_TO_RAD * 0.5F;
        this.head.xRot = 0.1F - 1.25F;

        this.leftLeg.y = 19.0F;
        this.rightLeg.y = 19.0F;
        this.leftLeg.z = 3.0F;
        this.rightLeg.z = 3.0F;
        this.leftLeg.xRot = HALF_PI;
        this.rightLeg.xRot = HALF_PI;
    }
}
