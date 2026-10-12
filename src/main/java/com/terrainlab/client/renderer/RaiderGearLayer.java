package com.terrainlab.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Render layer that draws a small gear model (hat, bandana, eyepatch) over an illager.
 * Each frame it copies the pose of one named parent part ("head" or "body") onto the
 * gear model's matching part, then submits it with its own texture.
 */
public class RaiderGearLayer extends RenderLayer<IllagerRenderState, IllagerModel<IllagerRenderState>> {

    private final EntityModel<IllagerRenderState> gearModel;
    private final Identifier texture;
    private final String trackedPart;

    public RaiderGearLayer(RenderLayerParent<IllagerRenderState, IllagerModel<IllagerRenderState>> renderer,
                            EntityModel<IllagerRenderState> gearModel, Identifier texture, String trackedPart) {
        super(renderer);
        this.gearModel = gearModel;
        this.texture = texture;
        this.trackedPart = trackedPart;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                        IllagerRenderState renderState, float yRot, float xRot) {
        ModelPart parentPart = this.getParentModel().root().getChild(this.trackedPart);
        ModelPart gearPart = this.gearModel.root().getChild(this.trackedPart);
        gearPart.loadPose(parentPart.storePose());

        collector.order(1).submitModel(this.gearModel, renderState, poseStack,
                RenderTypes.entityCutoutNoCull(this.texture),
                packedLight, OverlayTexture.NO_OVERLAY, -1, null);
    }
}
