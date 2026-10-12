package com.terrainlab.client.item.properties;

import com.mojang.serialization.MapCodec;
import com.terrainlab.client.ClientShieldAutoBlockState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Item-model condition {@code terrainlab:shield_raised}, used by all 13 wood
 * shields' item definitions in place of vanilla's {@code minecraft:using_item}.
 * True when:
 * <ul>
 *   <li>the shield is actually raised - identical to vanilla's IsUsingItem
 *       (entity is using an item and it's this exact stack); or</li>
 *   <li>the shield just passively auto-blocked and is inside its short flash
 *       window (ClientShieldAutoBlockState), and this stack is the one in the
 *       hand that blocked.</li>
 * </ul>
 * Registered in TerrainLabClient#onRegisterConditionalItemModelProperties.
 */
public record ShieldRaisedProperty() implements ConditionalItemModelProperty {

    public static final MapCodec<ShieldRaisedProperty> MAP_CODEC = MapCodec.unit(new ShieldRaisedProperty());

    @Override
    public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity,
                       int seed, ItemDisplayContext displayContext) {
        if (entity == null) {
            return false;
        }
        if (entity.isUsingItem() && entity.getUseItem() == stack) {
            return true; // vanilla minecraft:using_item behaviour
        }
        for (InteractionHand hand : InteractionHand.values()) {
            if (ClientShieldAutoBlockState.isFlashing(entity, hand) && isThisStack(entity.getItemInHand(hand), stack)) {
                return true;
            }
        }
        return false;
    }

    // The first-person renderer can briefly hold its own reference to the
    // held stack (ItemInHandRenderer's equip animation), so fall back to an
    // item+components match rather than relying on identity alone.
    private static boolean isThisStack(ItemStack inHand, ItemStack rendered) {
        return inHand == rendered || ItemStack.isSameItemSameComponents(inHand, rendered);
    }

    @Override
    public MapCodec<ShieldRaisedProperty> type() {
        return MAP_CODEC;
    }
}
