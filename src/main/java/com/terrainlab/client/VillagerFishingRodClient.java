package com.terrainlab.client;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/**
 * Renders a fishing fisherman's rod as a held rod. While the synced {@code VILLAGER_FISHING} flag is set and
 * the main hand is a fishing rod, the held item is re-resolved with the third-person right-hand display
 * context (the vanilla handheld_rod transforms) and {@link #FISHING} is set on the render state;
 * CrossedArmsItemLayerFishingMixin then places it in the right hand instead of in front of the crossed arms,
 * and VillagerModelMoodMixin lifts the arms slightly.
 */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public final class VillagerFishingRodClient {

    public static final ContextKey<Boolean> FISHING =
            new ContextKey<>(Identifier.fromNamespaceAndPath(TerrainLab.MODID, "villager_fishing"));

    /** Extra lift of the crossed-arms part while fishing (radians, negative = forearms up). */
    public static final float ARMS_RAISE = -0.2F;
    /** Vanilla's rest rotation of the villager arms part. */
    public static final float ARMS_REST_X = -0.75F;
    /** A player's arm angle when holding an item (HumanoidModel ArmPose.ITEM at rest). */
    public static final float HOLD_ARM_X = (float) (-Math.PI / 10.0D);

    private VillagerFishingRodClient() {
    }

    @SubscribeEvent
    static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.<Villager, VillagerRenderState>registerEntityModifier(
                VillagerRenderer.class,
                (villager, state) -> {
                    if (villager.isBaby()
                            || !villager.getData(ModAttachments.VILLAGER_FISHING.get())
                            || !villager.getMainHandItem().is(Items.FISHING_ROD)) {
                        return;
                    }
                    state.setRenderData(FISHING, true);
                    Minecraft.getInstance().getItemModelResolver().updateForLiving(
                            state.heldItem, villager.getMainHandItem(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, villager);
                }
        );
    }
}
