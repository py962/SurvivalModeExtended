package com.terrainlab.client.model;

import com.terrainlab.client.renderer.GraniteGolemRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class GraniteGolemModel extends EntityModel<GraniteGolemRenderState> {

    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public GraniteGolemModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Body box is centred on the entity origin; its underside meets the leg tops.
        root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 40)
                        .addBox(-9.0F, -2.0F, -5.5F, 18.0F, 14.0F, 11.0F)
                        .texOffs(0, 0)
                        .addBox(-4.0F, -1.0F, -6.5F, 3.0F, 3.0F, 1.0F)
                        .texOffs(8, 0)
                        .addBox(1.0F, -1.0F, -6.5F, 3.0F, 3.0F, 1.0F)
                        .texOffs(0, 4)
                        .addBox(-3.0F, 3.0F, -6.5F, 6.0F, 2.0F, 1.0F),
                PartPose.offset(0.0F, -4.0F, 0.0F));

        root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(60, 21)
                        .addBox(-13.0F, -2.5F, -3.0F, 4.0F, 20.0F, 6.0F),
                PartPose.offset(0.0F, -4.0F, 0.0F));

        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(60, 58)
                        .addBox(9.0F, -2.5F, -3.0F, 4.0F, 20.0F, 6.0F),
                PartPose.offset(0.0F, -4.0F, 0.0F));

        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(37, 0)
                        .addBox(-3.5F, -3.0F, -3.0F, 6.0F, 16.0F, 5.0F),
                PartPose.offset(-4.0F, 11.0F, 0.0F));

        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(60, 0).mirror()
                        .addBox(-3.5F, -3.0F, -3.0F, 6.0F, 16.0F, 5.0F),
                PartPose.offset(5.0F, 11.0F, 0.0F));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(GraniteGolemRenderState state) {
        super.setupAnim(state);
        float speed = state.walkAnimationSpeed;
        float pos = state.walkAnimationPos;

        if (state.isTunnelling) {
            this.rightArm.xRot = -2.0F + 1.5F * Mth.triangleWave(state.animTick * 0.1F, 10.0F);
            this.leftArm.xRot = -2.0F + 1.5F * Mth.triangleWave(state.animTick * 0.1F + 5.0F, 10.0F);
        } else {
            this.rightArm.xRot = (-0.2F + 1.5F * Mth.triangleWave(pos, 13.0F)) * speed;
            this.leftArm.xRot = (-0.2F - 1.5F * Mth.triangleWave(pos, 13.0F)) * speed;
        }

        this.rightLeg.xRot = -1.5F * Mth.triangleWave(pos, 13.0F) * speed;
        this.leftLeg.xRot = 1.5F * Mth.triangleWave(pos, 13.0F) * speed;
    }
}
