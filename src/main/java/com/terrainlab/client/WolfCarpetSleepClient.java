package com.terrainlab.client;

import com.terrainlab.TerrainLab;
import com.terrainlab.pet.WolfCarpetSleep;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/** Copies the synced carpet-sleep flag onto the wolf render state for WolfModelCarpetSleepMixin. */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public final class WolfCarpetSleepClient {

    public static final ContextKey<Boolean> CARPET_ASLEEP =
            new ContextKey<>(Identifier.fromNamespaceAndPath(TerrainLab.MODID, "wolf_carpet_asleep"));

    private WolfCarpetSleepClient() {}

    @SubscribeEvent
    static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.<Wolf, WolfRenderState>registerEntityModifier(
                WolfRenderer.class,
                (wolf, state) -> state.setRenderData(CARPET_ASLEEP, WolfCarpetSleep.isAsleep(wolf)));
    }
}
