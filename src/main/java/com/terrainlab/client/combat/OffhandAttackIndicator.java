package com.terrainlab.client.combat;

import com.terrainlab.TerrainLab;
import com.terrainlab.combat.DualWield;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.AttackRange;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Dual wielding HUD: a second attack indicator for the off hand, using the vanilla attack
 * indicator sprites and following the player's Attack Indicator option. Crosshair mode draws it
 * 8px below vanilla's (clear of the 7px-tall "full" icon); hotbar mode draws it just outside the
 * off-hand slot. Shown only while right-click is an off-hand strike.
 */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public final class OffhandAttackIndicator {

    private static final Identifier CROSSHAIR_FULL = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_full");
    private static final Identifier CROSSHAIR_BACKGROUND = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_background");
    private static final Identifier CROSSHAIR_PROGRESS = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_progress");
    private static final Identifier HOTBAR_BACKGROUND = Identifier.withDefaultNamespace("hud/hotbar_attack_indicator_background");
    private static final Identifier HOTBAR_PROGRESS = Identifier.withDefaultNamespace("hud/hotbar_attack_indicator_progress");

    /** Gap below vanilla's crosshair indicator. */
    private static final int CROSSHAIR_OFFSET_Y = 8;

    private OffhandAttackIndicator() {}

    @SubscribeEvent
    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR,
                Identifier.fromNamespaceAndPath(TerrainLab.MODID, "offhand_attack_indicator_crosshair"),
                OffhandAttackIndicator::extractCrosshair);
        event.registerAbove(VanillaGuiLayers.HOTBAR,
                Identifier.fromNamespaceAndPath(TerrainLab.MODID, "offhand_attack_indicator_hotbar"),
                OffhandAttackIndicator::extractHotbar);
    }

    private static LocalPlayer activePlayer(Minecraft mc, AttackIndicatorStatus mode) {
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || mc.options.attackIndicator().get() != mode) {
            return null;
        }
        // Hidden when the main hand's own use (e.g. spear charge) will take the right-click.
        return DualWield.canOffhandStrike(player) && !DualWield.mainHandTakesRightClick(player) ? player : null;
    }

    private static void extractCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = activePlayer(mc, AttackIndicatorStatus.CROSSHAIR);
        if (player == null
                || !mc.options.getCameraType().isFirstPerson()
                || mc.debugEntries.isCurrentlyEnabled(DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR)) {
            return;
        }

        float scale = DualWield.offhandStrengthScale(player, 0.0F);
        boolean full = false;
        Entity picked = mc.crosshairPickEntity;
        if (picked instanceof LivingEntity && scale >= 1.0F && mc.hitResult != null) {
            full = DualWield.offhandAttackDelay(player) > 5.0F
                    && picked.isAlive()
                    && DualWield.isStrikeTarget(player, picked);
            AttackRange range = player.getOffhandItem().get(DataComponents.ATTACK_RANGE);
            full &= range == null || range.isInRange(player, mc.hitResult.getLocation());
        }

        int y = graphics.guiHeight() / 2 - 7 + 16 + CROSSHAIR_OFFSET_Y;
        int x = graphics.guiWidth() / 2 - 8;
        if (full) {
            graphics.blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_FULL, x, y, 16, 16);
        } else if (scale < 1.0F) {
            int progress = (int) (scale * 17.0F);
            graphics.blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_BACKGROUND, x, y, 16, 4);
            graphics.blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_PROGRESS, 16, 4, 0, 0, x, y, progress, 4);
        }
    }

    private static void extractHotbar(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = activePlayer(mc, AttackIndicatorStatus.HOTBAR);
        if (player == null) {
            return;
        }
        float scale = DualWield.offhandStrengthScale(player, 0.0F);
        if (scale >= 1.0F) {
            return;
        }

        // Just outside the off-hand slot (vanilla draws the main-hand one on the opposite side).
        int center = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() - 20;
        int x = player.getMainArm().getOpposite() == HumanoidArm.LEFT
                ? center - 91 - 29 - 4 - 18
                : center + 91 + 29 + 4;
        int progress = (int) (scale * 19.0F);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_BACKGROUND, x, y, 18, 18);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_PROGRESS, 18, 18, 0, 18 - progress, x, y + 18 - progress, 18, progress);
    }
}
