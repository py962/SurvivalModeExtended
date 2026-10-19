package com.terrainlab.client.model;

import com.terrainlab.client.renderer.CaribouRenderState;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.MeshTransformer;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Caribou model built on QuadrupedModel (cow-like proportions and walk cycle) with ears,
 * branching antlers and a neck mane. Separate, slightly larger head and body fluff parts
 * act as the winter coat and are hidden when the caribou is sheared.
 */
public class CaribouModel extends QuadrupedModel<CaribouRenderState> {

    public CaribouModel(ModelPart root) {
        super(root);
    }

    // Calves render at half size, matching their half-size hitbox.
    public static final MeshTransformer BABY_TRANSFORMER = MeshTransformer.scaling(0.5F);

    public static LayerDefinition createBabyLayer() {
        return createBodyLayer().apply(BABY_TRANSFORMER);
    }

    public static LayerDefinition createBodyLayer() {
        // 128x64 texture; every part has its own UV area. Part names follow QuadrupedModel.
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-4.0F, -4.0F, -6.0F, 8.0F, 8.0F, 6.0F)
                        .texOffs(0, 14)
                        .addBox("muzzle", -3.0F, 1.0F, -8.0F, 6.0F, 3.0F, 2.0F),
                PartPose.offset(0.0F, 4.0F, -8.0F));

        CubeListBuilder ear = CubeListBuilder.create()
                .texOffs(16, 14)
                .addBox(0.0F, -3.0F, -1.0F, 1.0F, 3.0F, 2.0F);
        head.addOrReplaceChild("left_ear", ear,
                PartPose.offsetAndRotation(4.0F, -2.0F, -2.0F, 0.0F, 0.4F, 0.9F));
        head.addOrReplaceChild("right_ear", ear,
                PartPose.offsetAndRotation(-5.0F, -2.0F, -2.0F, 0.0F, -0.4F, -0.9F));

        // Antlers: beams rise from the top of the skull, sweep back and out,
        // and curve forward at the top; the brow tine ("shovel") points forward.
        CubeListBuilder beam = CubeListBuilder.create()
                .texOffs(32, 0)
                .addBox(-0.5F, -14.0F, -0.5F, 1.0F, 14.0F, 1.0F);
        PartDefinition leftBeam = head.addOrReplaceChild("left_antler_beam", beam,
                PartPose.offsetAndRotation(2.5F, -4.0F, -2.5F, -0.35F, 0.0F, 0.5F));
        PartDefinition rightBeam = head.addOrReplaceChild("right_antler_beam", beam,
                PartPose.offsetAndRotation(-2.5F, -4.0F, -2.5F, -0.35F, 0.0F, -0.5F));

        CubeListBuilder browTine = CubeListBuilder.create()
                .texOffs(36, 0)
                .addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F);
        leftBeam.addOrReplaceChild("left_brow_tine", browTine,
                PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, 1.2F, 0.0F, -0.4F));
        rightBeam.addOrReplaceChild("right_brow_tine", browTine,
                PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, 1.2F, 0.0F, 0.4F));

        CubeListBuilder midTine = CubeListBuilder.create()
                .texOffs(40, 0)
                .addBox(-0.5F, -6.0F, -0.5F, 1.0F, 6.0F, 1.0F);
        leftBeam.addOrReplaceChild("left_mid_tine", midTine,
                PartPose.offsetAndRotation(0.0F, -8.0F, 0.0F, -0.5F, 0.0F, 0.35F));
        rightBeam.addOrReplaceChild("right_mid_tine", midTine,
                PartPose.offsetAndRotation(0.0F, -8.0F, 0.0F, -0.5F, 0.0F, -0.35F));

        CubeListBuilder topTine = CubeListBuilder.create()
                .texOffs(44, 0)
                .addBox(-0.5F, -5.0F, -0.5F, 1.0F, 5.0F, 1.0F);
        leftBeam.addOrReplaceChild("left_top_tine", topTine,
                PartPose.offsetAndRotation(0.0F, -13.5F, 0.0F, 0.7F, 0.0F, -0.3F));
        rightBeam.addOrReplaceChild("right_top_tine", topTine,
                PartPose.offsetAndRotation(0.0F, -13.5F, 0.0F, 0.7F, 0.0F, 0.3F));

        // Winter coat over the head (hidden when sheared).
        head.addOrReplaceChild("head_fluff",
                CubeListBuilder.create()
                        .texOffs(64, 0)
                        .addBox(-4.5F, -4.5F, -6.5F, 9.0F, 9.0F, 7.0F),
                PartPose.ZERO);

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        .texOffs(0, 22)
                        .addBox(-6.0F, -10.0F, -7.0F, 12.0F, 18.0F, 10.0F)
                        // Cream neck mane hanging from the throat down the chest.
                        .texOffs(0, 50)
                        .addBox("mane", -4.0F, -12.0F, -5.0F, 8.0F, 3.0F, 8.0F),
                PartPose.offsetAndRotation(0.0F, 5.0F, 2.0F, (float) (Math.PI / 2), 0.0F, 0.0F));

        body.addOrReplaceChild("body_fluff",
                CubeListBuilder.create()
                        .texOffs(64, 16)
                        .addBox(-6.5F, -10.5F, -7.5F, 13.0F, 19.0F, 11.0F),
                PartPose.ZERO);

        CubeListBuilder leftLegBuilder = CubeListBuilder.create().mirror().texOffs(96, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F);
        CubeListBuilder rightLegBuilder = CubeListBuilder.create().texOffs(96, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F);
        root.addOrReplaceChild("right_hind_leg", rightLegBuilder, PartPose.offset(-4.0F, 12.0F, 7.0F));
        root.addOrReplaceChild("left_hind_leg", leftLegBuilder, PartPose.offset(4.0F, 12.0F, 7.0F));
        root.addOrReplaceChild("right_front_leg", rightLegBuilder, PartPose.offset(-4.0F, 12.0F, -5.0F));
        root.addOrReplaceChild("left_front_leg", leftLegBuilder, PartPose.offset(4.0F, 12.0F, -5.0F));

        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void setupAnim(CaribouRenderState state) {
        super.setupAnim(state);
        boolean fluffVisible = !state.sheared;
        this.body.getChild("body_fluff").visible = fluffVisible;
        this.head.getChild("head_fluff").visible = fluffVisible;
    }
}
