package com.terrainlab.client.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/**
 * Pendant model shared by all Celestial amulets; only the texture
 * (textures/entity/&lt;variant&gt;_amulet_render.png) differs.
 * <p>
 * The pendant is a flat 4x4 plane showing the amulet's item icon, hung from two thin
 * chain strands. The texture is 64x64 at 4 pixels per model texel (layer size 16x16).
 * Extends HumanoidModel to follow the wearer's pose, so the unused standard parts are
 * empty. The pendant sits 0.6px in front of a chestplate (and 0.3px in front of the Fur
 * Cloak mantle) to avoid z-fighting.
 */
public class CelestialAmuletModel extends HumanoidModel<HumanoidRenderState> {

    public CelestialAmuletModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.0F, 1.2F, -3.6F, 4.0F, 4.0F, 0.0F),
                PartPose.ZERO);

        // Chain strands from each side of the neck to the bail; a 1x3x1 box shrunk to a thin cord.
        body.addOrReplaceChild("chain_left",
                CubeListBuilder.create()
                        .texOffs(0, 4).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(-0.32F)),
                PartPose.offsetAndRotation(2.2F, -0.3F, -3.55F, 0.0F, 0.0F, 0.87F));
        body.addOrReplaceChild("chain_right",
                CubeListBuilder.create()
                        .texOffs(0, 4).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(-0.32F)),
                PartPose.offsetAndRotation(-2.2F, -0.3F, -3.55F, 0.0F, 0.0F, -0.87F));

        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));

        return LayerDefinition.create(mesh, 16, 16);
    }
}
