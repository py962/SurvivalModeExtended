package com.terrainlab.client.model;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.CamelRenderState;

/**
 * Bamboo howdah carried on the elephant: floor platform, four full-height corner posts,
 * low rail walls along the bottom of each side (so a seated rider can see out), and an
 * overhanging pitched roof. Positioning is baked into the root-level part offsets,
 * relative to the elephant body's top surface. Requires the 128x136 howdah.png.
 * <p>
 * Part offsets give the box top (more negative is higher); a box extends downward by
 * its height.
 */
public class HowdahModel extends EntityModel<CamelRenderState> {

    private final ModelPart floor;
    private final ModelPart postFrontLeft;
    private final ModelPart postFrontRight;
    private final ModelPart postBackLeft;
    private final ModelPart postBackRight;
    private final ModelPart railFront;
    private final ModelPart railBack;
    private final ModelPart railLeft;
    private final ModelPart railRight;
    private final ModelPart roof;

    public HowdahModel(ModelPart root) {
        super(root);
        this.floor = root.getChild("floor");
        this.postFrontLeft = root.getChild("post_front_left");
        this.postFrontRight = root.getChild("post_front_right");
        this.postBackLeft = root.getChild("post_back_left");
        this.postBackRight = root.getChild("post_back_right");
        this.railFront = root.getChild("rail_front");
        this.railBack = root.getChild("rail_back");
        this.railLeft = root.getChild("rail_left");
        this.railRight = root.getChild("rail_right");
        this.roof = root.getChild("roof");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Elephant-back offset applied to every top-level part; dy is the elephant body's top surface.
        final float dx = 0.0F;
        final float dy = -36.0F;
        final float dz = -2.0F;

        // 24x24 footprint (1.5x1.5 blocks); top surface at dy.
        root.addOrReplaceChild("floor",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-12.0F, 0.0F, -12.0F, 24.0F, 2.0F, 24.0F),
                PartPose.offset(dx + 0.0F, dy + 0.0F, dz + 0.0F));

        // Corner posts, 26 tall, from the floor top up to the roof base.
        CubeListBuilder postBox = CubeListBuilder.create().texOffs(0, 26)
                .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 26.0F, 2.0F);
        root.addOrReplaceChild("post_front_left", postBox,
                PartPose.offset(dx - 11.0F, dy - 26.0F, dz - 11.0F));
        root.addOrReplaceChild("post_front_right", postBox,
                PartPose.offset(dx + 11.0F, dy - 26.0F, dz - 11.0F));
        root.addOrReplaceChild("post_back_left", postBox,
                PartPose.offset(dx - 11.0F, dy - 26.0F, dz + 11.0F));
        root.addOrReplaceChild("post_back_right", postBox,
                PartPose.offset(dx + 11.0F, dy - 26.0F, dz + 11.0F));

        // Rail walls cover only the bottom 5 units, leaving the view above open.
        CubeListBuilder railFrontBack = CubeListBuilder.create().texOffs(0, 54)
                .addBox(-12.0F, 0.0F, -1.0F, 24.0F, 5.0F, 2.0F);
        CubeListBuilder railSide = CubeListBuilder.create().texOffs(0, 61)
                .addBox(-1.0F, 0.0F, -12.0F, 2.0F, 5.0F, 24.0F);

        root.addOrReplaceChild("rail_front", railFrontBack,
                PartPose.offset(dx + 0.0F, dy - 5.0F, dz - 11.0F));
        root.addOrReplaceChild("rail_back", railFrontBack,
                PartPose.offset(dx + 0.0F, dy - 5.0F, dz + 11.0F));
        root.addOrReplaceChild("rail_left", railSide,
                PartPose.offset(dx - 11.0F, dy - 5.0F, dz + 0.0F));
        root.addOrReplaceChild("rail_right", railSide,
                PartPose.offset(dx + 11.0F, dy - 5.0F, dz + 0.0F));

        // Overhanging pitched roof resting on the post tops.
        root.addOrReplaceChild("roof",
                CubeListBuilder.create().texOffs(0, 90)
                        .addBox(-14.0F, 0.0F, -14.0F, 28.0F, 2.0F, 28.0F)
                        .texOffs(0, 120)
                        .addBox(-6.0F, -4.0F, -6.0F, 12.0F, 4.0F, 12.0F),
                PartPose.offset(dx + 0.0F, dy - 28.0F, dz + 0.0F));

        return LayerDefinition.create(mesh, 128, 136);
    }

    @Override
    public void setupAnim(CamelRenderState state) {
        // Static; follows the elephant via the render layer's transform.
    }
}
