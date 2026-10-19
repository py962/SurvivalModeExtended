package com.terrainlab.season.plants;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terrainlab.data.SeasonSavedData;
import com.terrainlab.registry.ModLootModifiers;
import com.terrainlab.season.Season;
import com.terrainlab.season.SeasonManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Seasonal apples from oak and dark oak leaves (which tables it applies to is set by the JSON
 * {@code condition}). Inside {@code #terrainlab:has_seasonal_ground_plants} biomes: no apples outside
 * MIDSUMMER / EARLY_AUTUMN / LATE_AUTUMN; in those seasons a second, independent apple roll using
 * {@code extra_roll} (a copy of vanilla's apple conditions) roughly doubles the vanilla chance.
 * Elsewhere loot is left untouched.
 */
public class SeasonalAppleLootModifier extends LootModifier {

    public static final MapCodec<SeasonalAppleLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst -> codecStart(inst)
            .and(LootItemCondition.CODEC.fieldOf("extra_roll").forGetter(m -> m.extraRoll))
            .apply(inst, SeasonalAppleLootModifier::new));

    private static final EnumSet<Season> APPLE_SEASONS = EnumSet.of(Season.MIDSUMMER, Season.EARLY_AUTUMN, Season.LATE_AUTUMN);

    private final Holder<LootItemCondition> extraRoll;

    public SeasonalAppleLootModifier(Optional<Holder<LootItemCondition>> condition, int priority, Holder<LootItemCondition> extraRoll) {
        super(condition, priority);
        this.extraRoll = extraRoll;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        Vec3 origin = context.getOptional(LootContextParams.ORIGIN);
        if (origin == null) {
            return generatedLoot;
        }
        ServerLevel level = context.getLevel();
        if (!level.getBiome(BlockPos.containing(origin)).is(SeasonalPlantCategory.AFFECTED_BIOMES)) {
            return generatedLoot;
        }
        Season season = SeasonManager.getCurrentSeason(level.getGameTime(), SeasonSavedData.get(level).getSeasonalOffset());
        if (!APPLE_SEASONS.contains(season)) {
            generatedLoot.removeIf(stack -> stack.is(Items.APPLE));
        } else if (extraRoll.value().test(context)) {
            generatedLoot.add(new ItemStack(Items.APPLE));
        }
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return ModLootModifiers.SEASONAL_APPLES.get();
    }
}
