package com.terrainlab.client.renderer;

import com.terrainlab.TerrainLab;
import com.terrainlab.celestial.CelestialVariant;
import com.terrainlab.client.model.CelestialAmuletModel;
import com.terrainlab.item.CelestialAmuletItem;
import com.terrainlab.registry.ModModelLayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

import java.util.EnumMap;
import java.util.Map;

/**
 * Curios renderer shared by all Celestial amulets: the {@link CelestialAmuletModel}
 * pendant (baked lazily once per instance) with a per-variant
 * textures/entity/&lt;variant&gt;_amulet_render.png texture.
 */
public class CelestialAmuletRenderer implements ICurioRenderer.HumanoidRender {

    private static final Map<CelestialVariant, Identifier> TEXTURES = new EnumMap<>(CelestialVariant.class);

    static {
        for (CelestialVariant variant : CelestialVariant.values()) {
            TEXTURES.put(variant, Identifier.fromNamespaceAndPath(TerrainLab.MODID,
                    "textures/entity/" + variant.id() + "_amulet_render.png"));
        }
    }

    private EntityModel<HumanoidRenderState> model;

    @Override
    public EntityModel<HumanoidRenderState> getModel(ItemStack stack, SlotContext slotContext) {
        if (model == null) {
            model = new CelestialAmuletModel(
                    Minecraft.getInstance().getEntityModels().bakeLayer(ModModelLayers.CELESTIAL_AMULET));
        }
        return model;
    }

    @Override
    public Identifier getModelTexture(ItemStack stack, SlotContext slotContext) {
        CelestialVariant variant = stack.getItem() instanceof CelestialAmuletItem amulet
                ? amulet.variant()
                : CelestialVariant.BREATH;
        return TEXTURES.get(variant);
    }
}
