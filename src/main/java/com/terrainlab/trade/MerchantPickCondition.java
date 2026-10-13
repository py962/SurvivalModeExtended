package com.terrainlab.trade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Picks exactly one trade out of a group for each merchant: the trade passes when
 * {@code index} is this merchant's pick among {@code count} options. The pick is derived from
 * the merchant's UUID and the group name, so it is stable and the options never both appear.
 * Replaces 1.21.11's "random choice" listings (e.g. the Shepherd's brown/black/grey wool).
 */
public record MerchantPickCondition(String group, int index, int count) implements LootItemCondition {

    public static final MapCodec<MerchantPickCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("group").forGetter(MerchantPickCondition::group),
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("index").forGetter(MerchantPickCondition::index),
            ExtraCodecs.POSITIVE_INT.fieldOf("count").forGetter(MerchantPickCondition::count)
    ).apply(i, MerchantPickCondition::new));

    @Override
    public MapCodec<MerchantPickCondition> codec() {
        return MAP_CODEC;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams() {
        return Set.of(LootContextParams.THIS_ENTITY);
    }

    @Override
    public boolean test(LootContext context) {
        Entity merchant = context.getOptional(LootContextParams.THIS_ENTITY);
        if (merchant == null) {
            return index == 0;
        }
        long seed = merchant.getUUID().getMostSignificantBits() ^ merchant.getUUID().getLeastSignificantBits()
                ^ ((long) group.hashCode() * 0x9E3779B97F4A7C15L);
        seed ^= seed >>> 33;
        seed *= 0xff51afd7ed558ccdL;
        seed ^= seed >>> 33;
        return Math.floorMod(seed, count) == index;
    }
}
