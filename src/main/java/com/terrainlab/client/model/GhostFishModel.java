package com.terrainlab.client.model;

import com.terrainlab.client.renderer.GhostFishRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Ghost Fish model: a cod-proportioned body with a snout and flat dorsal, side and tail
 * fins. The tail wags (faster and wider when out of water) and the side fins scull.
 * GhostFishRenderer draws it translucent and emissive.
 */
public class GhostFishModel extends EntityModel<GhostFishRenderState> {

    private final ModelPart body;
    private final ModelPart tailFin;
    private final ModelPart leftFin;
    private final ModelPart rightFin;

    public GhostFishModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
        this.tailFin = root.getChild("tail_fin");
        this.leftFin = this.body.getChild("left_fin");
        this.rightFin = this.body.getChild("right_fin");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-1.5F, -2.0F, -3.0F, 3.0F, 4.0F, 7.0F)
                        .texOffs(0, 11)
                        .addBox("snout", -1.0F, -1.5F, -5.0F, 2.0F, 3.0F, 2.0F),
                PartPose.offset(0.0F, 22.0F, 0.0F));

        body.addOrReplaceChild("dorsal_fin",
                CubeListBuilder.create()
                        .texOffs(20, 0)
                        .addBox(0.0F, -2.0F, -1.0F, 0.0F, 2.0F, 4.0F),
                PartPose.offset(0.0F, -2.0F, 0.0F));

        body.addOrReplaceChild("left_fin",
                CubeListBuilder.create()
                        .texOffs(10, 11)
                        .addBox(0.0F, 0.0F, 0.0F, 2.0F, 0.0F, 2.0F),
                PartPose.offsetAndRotation(1.5F, 1.0F, -2.0F, 0.0F, 0.0F, 0.6F));
        body.addOrReplaceChild("right_fin",
                CubeListBuilder.create()
                        .texOffs(10, 11)
                        .mirror()
                        .addBox(-2.0F, 0.0F, 0.0F, 2.0F, 0.0F, 2.0F),
                PartPose.offsetAndRotation(-1.5F, 1.0F, -2.0F, 0.0F, 0.0F, -0.6F));

        root.addOrReplaceChild("tail_fin",
                CubeListBuilder.create()
                        .texOffs(20, 6)
                        .addBox(0.0F, -3.0F, 0.0F, 0.0F, 6.0F, 4.0F),
                PartPose.offset(0.0F, 22.0F, 4.0F));

        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void setupAnim(GhostFishRenderState state) {
        super.setupAnim(state);
        float speed = state.isInWater ? 1.0F : 1.7F;
        float amount = state.isInWater ? 0.35F : 0.6F;
        this.tailFin.yRot = -amount * Mth.sin(0.6F * speed * state.ageInTicks);
        float scull = 0.25F * Mth.sin(0.4F * state.ageInTicks);
        this.leftFin.zRot = 0.6F + scull;
        this.rightFin.zRot = -0.6F - scull;
    }
}
