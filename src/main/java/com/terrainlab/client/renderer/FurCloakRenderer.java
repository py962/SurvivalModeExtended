package com.terrainlab.client.renderer;

import com.terrainlab.TerrainLab;
import com.terrainlab.client.model.FurCloakModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import com.terrainlab.registry.ModModelLayers;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/** Curios renderer for the Fur Cloak; the mesh is {@link FurCloakModel}, baked lazily once. */
public class FurCloakRenderer implements ICurioRenderer.HumanoidRender {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("terrainlab", "textures/entity/fur_cloak_render.png");

    private EntityModel<HumanoidRenderState> model;

    @Override
    public EntityModel<HumanoidRenderState> getModel(ItemStack stack, SlotContext slotContext) {
        if (model == null) {
            model = new FurCloakModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModModelLayers.FUR_CLOAK));
        }
        return model;
    }

    @Override
    public Identifier getModelTexture(ItemStack stack, SlotContext slotContext) {
        return TEXTURE;
    }
}
