package com.terrainlab.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;

/**
 * Outlaw Raider neck bandana: a thin box around the collar, attached to the body part so
 * it stays at the neck when the head turns.
 */
public class OutlawBandanaModel extends EntityModel<IllagerRenderState> {

    private final ModelPart body;

    public OutlawBandanaModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-4.5F, -1.0F, -2.5F, 9.0F, 2.0F, 5.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void setupAnim(IllagerRenderState renderState) {
        // Pose is copied from the parent model by RaiderGearLayer.
    }
}
