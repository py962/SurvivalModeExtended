package com.terrainlab.client.model;

import net.minecraft.client.model.animal.camel.CamelSaddleModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.CamelRenderState;

/** The vanilla camel saddle raised onto the elephant's back, without the camel bridle and reins. */
public class ElephantSaddleModel extends CamelSaddleModel {

    // Elephant back top (y -36) above the camel's (y -8).
    private static final float BACK_RAISE = 28.0F;

    private final ModelPart body;

    public ElephantSaddleModel(ModelPart root) {
        super(root);
        this.body = root.getChild("body");
    }

    @Override
    public void setupAnim(CamelRenderState state) {
        super.setupAnim(state);
        this.body.y -= BACK_RAISE;
        this.head.visible = false;
    }
}
