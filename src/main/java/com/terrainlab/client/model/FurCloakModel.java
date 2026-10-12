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
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;

/**
 * Fur Cloak worn in the Curios back/body slot: a fur mantle and collar, shoulder caps,
 * and a cape to about knee height that swings back while walking and lifts when
 * crouching. Sized to sit over armour (mantle and caps 1.3px out, versus vanilla
 * armour's 1.0px). The cape hides while an elytra is worn.
 * <p>
 * Extends HumanoidModel so it follows the wearer's pose; unused standard parts are empty.
 * setupAnim must not call Curios' setupHumanoidAnimations, which calls back into it and
 * recurses.
 */
public class FurCloakModel extends HumanoidModel<HumanoidRenderState> {

    private final ModelPart cape;

    public FurCloakModel(ModelPart root) {
        super(root);
        this.cape = root.getChild("body").getChild("cape");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);

        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create()
                        // Mantle over the shoulders and upper torso.
                        .texOffs(0, 0).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 4.0F, 4.0F, new CubeDeformation(1.3F))
                        // Raised fur collar round the neck.
                        .texOffs(24, 0).addBox(-4.5F, -1.5F, -2.5F, 9.0F, 2.0F, 5.0F, new CubeDeformation(0.6F)),
                PartPose.ZERO);

        // Cape: pivots at the back of the shoulders, hangs to about the knees.
        body.addOrReplaceChild("cape",
                CubeListBuilder.create()
                        .texOffs(0, 8).addBox(-4.5F, 0.0F, 0.0F, 9.0F, 19.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, 1.0F, 2.6F, 0.06F, 0.0F, 0.0F));

        root.addOrReplaceChild("right_arm",
                CubeListBuilder.create()
                        .texOffs(40, 8).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(1.3F)),
                PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create()
                        .texOffs(40, 8).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(1.3F)),
                PartPose.offset(5.0F, 2.0F, 0.0F));

        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(HumanoidRenderState state) {
        super.setupAnim(state);

        // Hidden while an elytra is worn, as vanilla's cape is.
        this.cape.visible = !state.chestEquipment.has(DataComponents.GLIDER);

        // Swing back with walking speed so the cape clears the legs.
        float swing = Math.min(state.walkAnimationSpeed, 1.0F) * 0.45F;
        float flutter = Mth.sin(state.walkAnimationPos * 0.6662F) * 0.06F * state.walkAnimationSpeed;
        this.cape.xRot = 0.06F + swing + flutter;

        if (state.isCrouching) {
            this.cape.xRot += 0.35F;
        }
    }
}
