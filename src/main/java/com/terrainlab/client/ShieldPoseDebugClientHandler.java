package com.terrainlab.client;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Temporary client-side diagnostic that logs the tick a held shield starts or stops
 * being used, with the hand and render arm, so first-person shield pose swaps can be
 * matched against what appears on screen.
 * <p>
 * TODO: remove once the shield pose is confirmed correct in-game.
 */
public class ShieldPoseDebugClientHandler {

    // Built lazily so it does not run before the shield DeferredItems are registered.
    private static Set<Item> shieldItems;

    private static Set<Item> shieldItems() {
        if (shieldItems == null) {
            Set<Item> set = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            for (var deferred : ModItems.SHIELD_ITEMS.values()) {
                set.add(deferred.get());
            }
            shieldItems = set;
        }
        return shieldItems;
    }

    private static boolean wasBlockingWithShield = false;

    // Captured when blocking starts; the use hand/item may already be reset on the tick it stops.
    private static InteractionHand lastShieldHand = InteractionHand.OFF_HAND;
    private static Item lastShieldItem = null;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }

        boolean usingShield = player.isUsingItem()
                && shieldItems().contains(player.getUseItem().getItem());

        if (usingShield && !wasBlockingWithShield) {
            lastShieldHand = player.getUsedItemHand();
            lastShieldItem = player.getUseItem().getItem();
            wasBlockingWithShield = true;
            logTransition(player, true);
        } else if (!usingShield && wasBlockingWithShield) {
            wasBlockingWithShield = false;
            logTransition(player, false);
        }
    }

    private static void logTransition(Player player, boolean raised) {
        // The main-hand item renders on getMainArm() and the off-hand item on the other arm,
        // which decides whether the firstperson_right/lefthand transform applies.
        boolean mainArmIsRight = player.getMainArm() == HumanoidArm.RIGHT;
        boolean isMainHand = lastShieldHand == InteractionHand.MAIN_HAND;
        HumanoidArm renderArm = (isMainHand == mainArmIsRight) ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
        String armKey = renderArm == HumanoidArm.RIGHT ? "right" : "left";

        if (raised) {
            // TerrainLab.LOGGER.info(
                    // "[ShieldPoseDebug] RAISED item={} hand={} renderArm={} -> should now show "
                            // + "shield_blocking.json's firstperson_{}hand pose (post-fix: the raised/"
                            // + "forward-tilted transform)",
                    // lastShieldItem, lastShieldHand, renderArm, armKey);
        } else {
            // TerrainLab.LOGGER.info(
                    // "[ShieldPoseDebug] LOWERED item={} hand={} renderArm={} -> should now show "
                            // + "shield.json's firstperson_{}hand pose (post-fix: the idle/at-rest transform)",
                    // lastShieldItem, lastShieldHand, renderArm, armKey);
        }
    }
}
