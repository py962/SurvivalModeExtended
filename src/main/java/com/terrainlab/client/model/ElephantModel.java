package com.terrainlab.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.CamelRenderState;
import net.minecraft.util.Mth;

/**
 * Elephant model (256x128 texture, about four blocks tall) with flapping ears, a
 * three-segment curling trunk, tusks and a tail. setupAnim adds a diagonal-pair walk,
 * trunk sway, ear fanning and tail swish.
 */
public class ElephantModel extends EntityModel<CamelRenderState> {

    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart leftEar;
    private final ModelPart rightEar;
    private final ModelPart trunkUpper;
    private final ModelPart trunkMid;
    private final ModelPart trunkTip;
    private final ModelPart tail;
    private final ModelPart frontLeftLeg;
    private final ModelPart frontRightLeg;
    private final ModelPart backLeftLeg;
    private final ModelPart backRightLeg;

    public ElephantModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        this.leftEar = this.head.getChild("left_ear");
        this.rightEar = this.head.getChild("right_ear");
        this.trunkUpper = this.head.getChild("trunk_upper");
        this.trunkMid = this.trunkUpper.getChild("trunk_mid");
        this.trunkTip = this.trunkMid.getChild("trunk_tip");
        this.tail = this.body.getChild("tail");
        this.frontLeftLeg = this.body.getChild("front_left_leg");
        this.frontRightLeg = this.body.getChild("front_right_leg");
        this.backLeftLeg = this.body.getChild("back_left_leg");
        this.backRightLeg = this.body.getChild("back_right_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-16.0F, -14.0F, -24.0F, 32.0F, 28.0F, 48.0F),
                PartPose.offset(0.0F, -22.0F, -2.0F));

        body.addOrReplaceChild("tail",
                CubeListBuilder.create()
                        .texOffs(210, 76)
                        .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 16.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, -8.0F, 23.0F, 0.25F, 0.0F, 0.0F));

        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(160, 0)
                        .addBox(-12.0F, -12.0F, -12.0F, 24.0F, 24.0F, 24.0F),
                PartPose.offset(0.0F, -30.0F, -30.0F));

        CubeListBuilder ear = CubeListBuilder.create()
                .texOffs(0, 76)
                .addBox(0.0F, -12.0F, -2.0F, 2.0F, 26.0F, 24.0F);
        head.addOrReplaceChild("left_ear", ear,
                PartPose.offsetAndRotation(11.0F, -2.0F, -4.0F, 0.0F, 0.9F, -0.08F));
        CubeListBuilder rightEar = CubeListBuilder.create()
                .texOffs(0, 76)
                .mirror()
                .addBox(0.0F, -12.0F, -2.0F, 2.0F, 26.0F, 24.0F);
        // mirrored so the pink inner side faces the head on both ears
        head.addOrReplaceChild("right_ear", rightEar,
                PartPose.offsetAndRotation(-13.0F, -2.0F, -4.0F, 0.0F, -0.9F, 0.08F));

        CubeListBuilder tusk = CubeListBuilder.create()
                .texOffs(104, 76)
                .addBox(-1.5F, -1.5F, -14.0F, 3.0F, 3.0F, 14.0F);
        head.addOrReplaceChild("left_tusk", tusk,
                PartPose.offsetAndRotation(6.0F, 8.0F, -10.0F, -0.55F, -0.15F, 0.0F));
        head.addOrReplaceChild("right_tusk", tusk,
                PartPose.offsetAndRotation(-6.0F, 8.0F, -10.0F, -0.55F, 0.15F, 0.0F));

        PartDefinition trunkUpper = head.addOrReplaceChild("trunk_upper",
                CubeListBuilder.create()
                        .texOffs(138, 76)
                        .addBox(-4.0F, 0.0F, -4.0F, 8.0F, 12.0F, 8.0F),
                PartPose.offsetAndRotation(0.0F, 6.0F, -10.0F, 0.1F, 0.0F, 0.0F));
        PartDefinition trunkMid = trunkUpper.addOrReplaceChild("trunk_mid",
                CubeListBuilder.create()
                        .texOffs(170, 76)
                        .addBox(-3.0F, 0.0F, -3.0F, 6.0F, 10.0F, 6.0F),
                PartPose.offsetAndRotation(0.0F, 11.5F, 0.0F, 0.1F, 0.0F, 0.0F));
        trunkMid.addOrReplaceChild("trunk_tip",
                CubeListBuilder.create()
                        .texOffs(194, 76)
                        .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 9.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, 9.5F, 0.0F, -0.45F, 0.0F, 0.0F));

        CubeListBuilder leg = CubeListBuilder.create()
                .texOffs(52, 76)
                .addBox(-6.5F, 0.0F, -6.5F, 13.0F, 32.0F, 13.0F);
        body.addOrReplaceChild("front_left_leg", leg, PartPose.offset(10.0F, 14.0F, -16.0F));
        body.addOrReplaceChild("front_right_leg", leg, PartPose.offset(-10.0F, 14.0F, -16.0F));
        body.addOrReplaceChild("back_left_leg", leg, PartPose.offset(10.0F, 14.0F, 16.0F));
        body.addOrReplaceChild("back_right_leg", leg, PartPose.offset(-10.0F, 14.0F, 16.0F));

        return LayerDefinition.create(mesh, 256, 128);
    }

    @Override
    public void setupAnim(CamelRenderState state) {
        super.setupAnim(state);
        float pos = state.walkAnimationPos;
        float speed = state.walkAnimationSpeed;
        float t = state.ageInTicks;

        // Head follows where the elephant is looking, gently.
        this.head.yRot = state.yRot * Mth.DEG_TO_RAD * 0.5F;
        this.head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.5F;

        // Diagonal-pair walk, slow and heavy.
        float swing = Mth.cos(pos * 0.45F) * 0.5F * speed;
        this.frontLeftLeg.xRot = swing;
        this.backRightLeg.xRot = swing;
        this.frontRightLeg.xRot = -swing;
        this.backLeftLeg.xRot = -swing;

        // Trunk: idle sway plus a swing while walking; the tip keeps its curl.
        float sway = Mth.sin(t * 0.05F) * 0.08F + Mth.sin(pos * 0.45F) * 0.12F * speed;
        this.trunkUpper.xRot = 0.1F + sway * 0.5F;
        this.trunkUpper.zRot = Mth.sin(t * 0.037F) * 0.06F;
        this.trunkMid.xRot = 0.1F + sway;
        this.trunkTip.xRot = -0.45F + sway * 1.5F;

        // Slow ear fanning and tail swish.
        float fan = Mth.sin(t * 0.08F) * 0.12F;
        this.leftEar.yRot = 0.9F + fan;
        this.rightEar.yRot = -0.9F - fan;
        this.tail.zRot = Mth.sin(t * 0.11F) * 0.2F;
    }
}
