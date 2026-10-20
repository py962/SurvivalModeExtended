package com.terrainlab.client.combat;

import com.terrainlab.TerrainLab;
import com.terrainlab.combat.DualWield;
import com.terrainlab.mixin.MultiPlayerGameModeInvoker;
import com.terrainlab.network.OffhandStrikePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Dual wielding, client side: the off-hand strike, with exactly the precedence of an off-hand
 * shield raise.
 *
 * <p>Vanilla's {@code Minecraft.startUseItem} tries the main hand first (entity interaction, block
 * use, item use), then the off hand (entity interaction, block use), and only then calls
 * {@code useItem(OFF_HAND)}, which is where a shield is raised. {@code OffhandStrikeUseItemMixin}
 * diverts that one call here when dual wielding: anything that succeeded earlier (trading, pets
 * sitting, mounting, feeding, stripping, a main-hand spear charge...) already ended the click.
 *
 * <p>Strike: a living entity under the crosshair within reach is struck, anything else is a swing
 * at nothing (a spear jab finds its own targets). One strike per press: vanilla repeats
 * startUseItem every 4 ticks while the key is held, and those repeats do nothing until release.
 */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public final class OffhandStrikeInputHandler {

    /** A strike already fired (or was attempted) for the current press of the use key. */
    private static boolean strikeFiredThisPress;

    private OffhandStrikeInputHandler() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Minecraft.getInstance().options.keyUse.isDown()) {
            strikeFiredThisPress = false;
        }
    }

    /**
     * Called in place of vanilla's {@code useItem(OFF_HAND)} from startUseItem.
     *
     * @return true if the off-hand use was taken over (the caller then skips vanilla's useItem)
     */
    public static boolean tryReplaceOffhandUse(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            return false;
        }
        if (!DualWield.canOffhandStrike(player)) {
            DualWield.debug("client: off-hand use stays vanilla, dual={}", DualWield.isDualWielding(player));
            return false;
        }
        ItemStack off = player.getOffhandItem();
        if (strikeFiredThisPress) {
            DualWield.debug("client: swallowed held-key repeat");
            return true;
        }
        strikeFiredThisPress = true;

        PiercingWeapon piercing = off.get(DataComponents.PIERCING_WEAPON);
        if (DualWield.offhandBelowMinimumCharge(player, 0)) {
            DualWield.debug("client: no strike, off-hand below minimum charge");
            return true; // Like a main-hand spear: an uncharged jab does nothing (press again when charged).
        }

        int entityId = OffhandStrikePacket.NO_TARGET;
        Entity target = null;
        HitResult hit = mc.hitResult;
        if (piercing == null && hit != null && hit.getType() == HitResult.Type.ENTITY
                && hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            AttackRange range = off.get(DataComponents.ATTACK_RANGE);
            boolean inReach = range != null
                    ? range.isInRange(player, hit.getLocation())
                    : player.isWithinEntityInteractionRange(entity, 0.0);
            if (inReach
                    && mc.level.getWorldBorder().isWithinBounds(entity.blockPosition())
                    && DualWield.isStrikeTarget(player, entity)) {
                target = entity;
                entityId = entity.getId();
            } else {
                DualWield.debug("client: {} not strikable (reach={}), swinging at nothing", entity, inReach);
            }
        }

        DualWield.debug("client: strike target={} sentId={} charge={} spear={}", target, entityId,
                DualWield.offhandStrengthScale(player, 0.5F), piercing != null);
        ((MultiPlayerGameModeInvoker) mc.gameMode).terrainlab$ensureHasSentCarriedItem();
        ClientPacketDistributor.sendToServer(new OffhandStrikePacket(entityId));
        DualWield.resetOffhand(player);
        player.swing(InteractionHand.OFF_HAND, off.getAttackAnimation(), false);
        if (piercing != null) {
            piercing.makeSound(player);
        }
        return true;
    }
}
