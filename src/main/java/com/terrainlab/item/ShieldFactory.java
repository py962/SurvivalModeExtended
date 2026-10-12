package com.terrainlab.item;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.BlocksAttacks;

import java.util.List;
import java.util.Optional;

/**
 * Builds the {@link Item.Properties} for a wood shield from its {@link ShieldStats}, including the
 * {@code blocks_attacks} component.
 */
public final class ShieldFactory {

    private ShieldFactory() {}

    /**
     * Adds durability, enchantability, repair material, blocking behaviour and break sound.
     *
     * @param properties properties that already have their id set
     */
    public static Item.Properties buildProperties(Item.Properties properties, ShieldStats stats) {
        return properties
                .durability(stats.maxDamage())
                .enchantable(ShieldStats.ENCHANTMENT_VALUE)
                // Any planks repair it, as with the vanilla shield.
                .repairable(ItemTags.PLANKS)
                .component(DataComponents.BLOCKS_ATTACKS, buildBlocksAttacks(stats))
                .component(DataComponents.BREAK_SOUND, SoundEvents.SHIELD_BREAK);
    }

    /**
     * Full negation within the stat-driven blocking angle, flat durability loss per block, and
     * vanilla shield sounds and bypass tag.
     */
    private static BlocksAttacks buildBlocksAttacks(ShieldStats stats) {
        BlocksAttacks.DamageReduction fullBlock = new BlocksAttacks.DamageReduction(
                stats.blockingAngleDegrees(), // horizontalBlockingAngle
                Optional.empty(),      // type: all non-bypassed damage types
                0.0f,                  // base
                1.0f                   // factor: negate 100% within the angle
        );

        // Flat durability loss per block, the same for every shield.
        BlocksAttacks.ItemDamageFunction itemDamage = new BlocksAttacks.ItemDamageFunction(
                0.0f,                                     // threshold
                ShieldStats.ITEM_DAMAGE_PER_BLOCK,         // base
                0.0f                                       // factor
        );

        // Typed locals: inline Optional arguments fail joint generic inference. Empty sounds mean silent.
        Optional<Holder<SoundEvent>> blockSound = Optional.of(SoundEvents.SHIELD_BLOCK);
        Optional<Holder<SoundEvent>> disabledSound = Optional.of(SoundEvents.SHIELD_BREAK);
        Optional<TagKey<DamageType>> bypassedBy = Optional.of(DamageTypeTags.BYPASSES_SHIELD);

        return new BlocksAttacks(
                stats.blockDelaySeconds(),
                stats.disableCooldownScale(),
                List.of(fullBlock),
                itemDamage,
                bypassedBy,
                blockSound,
                disabledSound
        );
    }
}
