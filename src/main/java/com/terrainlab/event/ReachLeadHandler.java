package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Reach on leads: a lead with Reach I/II/III in the main hand leashes mobs 1/2/3 blocks further away.
 * <p>
 * The enchantment's own attribute effect (reach.json) already gives +0.5 entity interaction range per
 * level to anything in the main hand; this tops it up by another +0.5 per level while the held item is a
 * lead. Main hand only: an off-hand lead would otherwise extend the main-hand weapon's attack reach.
 * Server-side; entity_interaction_range is a synced attribute so the client's picking follows.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class ReachLeadHandler {

    private static final Identifier MODIFIER_ID =
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "reach_lead_bonus");
    /** Extra range per Reach level on a lead, on top of reach.json's 0.5/level. */
    private static final double BONUS_PER_LEVEL = 0.5;

    private ReachLeadHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        AttributeInstance range = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (range == null) {
            return;
        }

        double bonus = 0.0;
        ItemStack held = player.getMainHandItem();
        if (held.is(Items.LEAD) && held.isEnchanted()) {
            Holder<Enchantment> reach = player.level().registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(ModEnchantments.REACH);
            bonus = EnchantmentHelper.getItemEnchantmentLevel(reach, held) * BONUS_PER_LEVEL;
        }

        AttributeModifier current = range.getModifier(MODIFIER_ID);
        if (bonus <= 0.0) {
            if (current != null) {
                range.removeModifier(MODIFIER_ID);
            }
        } else if (current == null || current.amount() != bonus) {
            range.addOrUpdateTransientModifier(
                    new AttributeModifier(MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
