package com.terrainlab.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;

/**
 * Pirate Raider head bandana: a box matching vanilla's head cube, inflated so it sits
 * just outside the pillager head. "head" is a direct child of root so the layer's
 * pose copy lines up.
 */
public class PirateGearModel extends EntityModel<IllagerRenderState> {

    private final ModelPart head;

    public PirateGearModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.35F)),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(IllagerRenderState renderState) {
        // Pose is copied from the parent model each frame by RaiderGearLayer.
    }

    public ModelPart head() {
        return this.head;
    }
}
