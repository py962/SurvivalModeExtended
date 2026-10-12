package com.terrainlab.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.object.boat.AbstractBoatModel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class HouseboatModel extends AbstractBoatModel {

    public HouseboatModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createHouseboatModel() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Hull bottom: 64 long, 48 wide.
        root.addOrReplaceChild("bottom",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-32.0F, -24.0F, -3.0F, 64.0F, 48.0F, 3.0F),
                PartPose.offsetAndRotation(0.0F, 3.0F, 1.0F,
                        (float)(Math.PI / 2), 0.0F, 0.0F));

        root.addOrReplaceChild("back",
                CubeListBuilder.create().texOffs(0, 52)
                        .addBox(-24.0F, -7.0F, -1.0F, 48.0F, 6.0F, 2.0F),
                PartPose.offsetAndRotation(-32.0F, 4.0F, 0.0F,
                        0.0F, (float)(Math.PI * 3.0 / 2.0), 0.0F));

        root.addOrReplaceChild("front",
                CubeListBuilder.create().texOffs(0, 60)
                        .addBox(-24.0F, -7.0F, -1.0F, 48.0F, 6.0F, 2.0F),
                PartPose.offsetAndRotation(32.0F, 4.0F, 0.0F,
                        0.0F, (float)(Math.PI / 2), 0.0F));

        root.addOrReplaceChild("right",
                CubeListBuilder.create().texOffs(0, 68)
                        .addBox(-32.0F, -7.0F, -1.0F, 64.0F, 6.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 4.0F, -24.0F,
                        0.0F, (float)Math.PI, 0.0F));

        root.addOrReplaceChild("left",
                CubeListBuilder.create().texOffs(0, 76)
                        .addBox(-32.0F, -7.0F, -1.0F, 64.0F, 6.0F, 2.0F),
                PartPose.offset(0.0F, 4.0F, 24.0F));

        root.addOrReplaceChild("left_paddle",
                CubeListBuilder.create().texOffs(0, 84)
                        .addBox(-1.0F, 0.0F, -5.0F, 2.0F, 2.0F, 18.0F)
                        .addBox(-1.001F, -3.0F, 8.0F, 1.0F, 6.0F, 7.0F),
                PartPose.offsetAndRotation(3.0F, -5.0F, 24.0F,
                        0.0F, 0.0F, (float)(Math.PI / 16)));

        root.addOrReplaceChild("right_paddle",
                CubeListBuilder.create().texOffs(0, 104)
                        .addBox(-1.0F, 0.0F, -5.0F, 2.0F, 2.0F, 18.0F)
                        .addBox(0.001F, -3.0F, 8.0F, 1.0F, 6.0F, 7.0F),
                PartPose.offsetAndRotation(3.0F, -5.0F, -24.0F,
                        0.0F, (float)Math.PI, (float)(Math.PI / 16)));

        // Mast, behind the seating position.
        root.addOrReplaceChild("mast",
                CubeListBuilder.create().texOffs(0, 124)
                        .addBox(-1.0F, -48.0F, -1.0F, 2.0F, 48.0F, 2.0F),
                PartPose.offset(0.0F, 4.0F, -8.0F));

        root.addOrReplaceChild("lantern_chain",
                CubeListBuilder.create().texOffs(0, 174)
                        .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F),
                PartPose.offset(0.0F, -44.0F, -8.0F));

        root.addOrReplaceChild("lantern_body",
                CubeListBuilder.create().texOffs(0, 184)
                        .addBox(-3.0F, 0.0F, -3.0F, 6.0F, 7.0F, 6.0F),
                PartPose.offset(0.0F, -36.0F, -8.0F));

        root.addOrReplaceChild("lantern_cap",
                CubeListBuilder.create().texOffs(0, 197)
                        .addBox(-2.0F, 0.0F, -2.0F, 4.0F, 2.0F, 4.0F),
                PartPose.offset(0.0F, -29.0F, -8.0F));

        root.addOrReplaceChild("bed_headboard",
                CubeListBuilder.create().texOffs(256, 0)
                        .addBox(-24.0F, -9.0F, -1.0F, 48.0F, 10.0F, 2.0F),
                PartPose.offsetAndRotation(-30.0F, 3.0F, 0.0F,
                        0.0F, (float)(Math.PI * 3.0 / 2.0), 0.0F));

        root.addOrReplaceChild("bed_footboard",
                CubeListBuilder.create().texOffs(256, 12)
                        .addBox(-24.0F, -6.0F, -1.0F, 48.0F, 6.0F, 2.0F),
                PartPose.offsetAndRotation(-8.0F, 3.0F, 0.0F,
                        0.0F, (float)(Math.PI * 3.0 / 2.0), 0.0F));

        // Mattress spans the full width of the boat at the back.
        root.addOrReplaceChild("bed_mattress",
                CubeListBuilder.create().texOffs(256, 20)
                        .addBox(-22.0F, -4.0F, -1.0F, 44.0F, 8.0F, 3.0F),
                PartPose.offsetAndRotation(-18.0F, 1.0F, 0.0F,
                        (float)(Math.PI / 2), (float)(Math.PI / 2), 0.0F));

        root.addOrReplaceChild("bed_pillow",
                CubeListBuilder.create().texOffs(256, 31)
                        .addBox(-8.0F, -4.0F, -1.0F, 16.0F, 8.0F, 2.0F),
                PartPose.offsetAndRotation(-27.0F, 1.0F, 0.0F,
                        (float)(Math.PI / 2), (float)(Math.PI / 2), 0.0F));
        return LayerDefinition.create(mesh, 512, 256);
    }

    public static LayerDefinition createWaterPatch() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("water_patch",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-32.0F, -24.0F, -3.0F, 64.0F, 48.0F, 3.0F),
                PartPose.offsetAndRotation(0.0F, -3.0F, 1.0F,
                        (float)(Math.PI / 2), 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 0, 0);
    }
}
