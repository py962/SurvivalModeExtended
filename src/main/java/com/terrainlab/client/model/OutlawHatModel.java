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
 * Outlaw Raider hat brim. The crown is part of the pillager texture; only the brim needs
 * geometry. "head" is an empty part that RaiderGearLayer copies the illager's head pose
 * onto, with the wide flat brim as its child.
 */
public class OutlawHatModel extends EntityModel<IllagerRenderState> {

    private final ModelPart head;

    public OutlawHatModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition head = mesh.getRoot().addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F),
                PartPose.ZERO);

        // Lowered to brow height so it reads as a brim rather than floating above the crown.
        head.addOrReplaceChild("brim",
                CubeListBuilder.create()
                        .texOffs(0, 32)
                        .addBox(-8.0F, -1.0F, -8.0F, 16.0F, 1.0F, 16.0F),
                PartPose.offset(0.0F, -6.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(IllagerRenderState renderState) {
        // Pose is copied from the parent model by RaiderGearLayer.
    }
}
