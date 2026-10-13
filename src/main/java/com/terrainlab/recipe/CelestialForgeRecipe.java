package com.terrainlab.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terrainlab.celestial.CelestialVariant;
import com.terrainlab.registry.ModDataComponents;
import com.terrainlab.registry.ModItems;
import com.terrainlab.registry.ModRecipeTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/**
 * A Celestial Forge ritual: a core gem placed in the forge, then {@code thrownCount} of the
 * thrown component tossed into its light. Produces two pearls of the variant sharing a fresh
 * pair id.
 */
public class CelestialForgeRecipe implements Recipe<CelestialForgeRecipeInput> {

    private final CelestialVariant variant;
    private final Ingredient core;
    private final Ingredient thrown;
    private final int thrownCount;
    private PlacementInfo placementInfo;

    public CelestialForgeRecipe(CelestialVariant variant, Ingredient core, Ingredient thrown, int thrownCount) {
        this.variant = variant;
        this.core = core;
        this.thrown = thrown;
        this.thrownCount = thrownCount;
    }

    public CelestialVariant variant() {
        return variant;
    }

    public Ingredient core() {
        return core;
    }

    public Ingredient thrown() {
        return thrown;
    }

    public int thrownCount() {
        return thrownCount;
    }

    /** Matches on the gem alone, or on the gem plus enough thrown components when one is given. */
    @Override
    public boolean matches(CelestialForgeRecipeInput input, Level level) {
        if (input.core().isEmpty() || !core.test(input.core())) {
            return false;
        }
        ItemStack thrownStack = input.thrown();
        return thrownStack.isEmpty() || (thrown.test(thrownStack) && thrownStack.getCount() >= thrownCount);
    }

    @Override
    public ItemStack assemble(CelestialForgeRecipeInput input) {
        ItemStack pearl = new ItemStack(ModItems.pearlFor(variant), 2);
        pearl.set(ModDataComponents.CELESTIAL_PAIR_ID.get(), UUID.randomUUID());
        pearl.set(ModDataComponents.CELESTIAL_VARIANT.get(), variant);
        return pearl;
    }

    /** Convenience overload kept from the pre-26.x signature; the registries are no longer needed. */
    public ItemStack assemble(CelestialForgeRecipeInput input, HolderLookup.Provider registries) {
        return assemble(input);
    }

    @Override
    public boolean showNotification() {
        return true;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<CelestialForgeRecipe> getSerializer() {
        return Serializer.INSTANCE;
    }

    @Override
    public RecipeType<CelestialForgeRecipe> getType() {
        return ModRecipeTypes.CELESTIAL_FORGE.get();
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        // The Forge has no recipe book; required by the interface only.
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public PlacementInfo placementInfo() {
        if (this.placementInfo == null) {
            this.placementInfo = PlacementInfo.create(List.of(core, thrown));
        }
        return this.placementInfo;
    }

    public static final class Serializer {
        private Serializer() {
        }

        public static final MapCodec<CelestialForgeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                CelestialVariant.CODEC.fieldOf("variant").forGetter(CelestialForgeRecipe::variant),
                Ingredient.CODEC.fieldOf("core").forGetter(CelestialForgeRecipe::core),
                Ingredient.CODEC.fieldOf("thrown").forGetter(CelestialForgeRecipe::thrown),
                Codec.intRange(1, 64).optionalFieldOf("thrown_count", 2).forGetter(CelestialForgeRecipe::thrownCount)
        ).apply(instance, CelestialForgeRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, CelestialForgeRecipe> STREAM_CODEC = StreamCodec.composite(
                CelestialVariant.STREAM_CODEC, CelestialForgeRecipe::variant,
                Ingredient.CONTENTS_STREAM_CODEC, CelestialForgeRecipe::core,
                Ingredient.CONTENTS_STREAM_CODEC, CelestialForgeRecipe::thrown,
                ByteBufCodecs.VAR_INT, CelestialForgeRecipe::thrownCount,
                CelestialForgeRecipe::new);

        public static final RecipeSerializer<CelestialForgeRecipe> INSTANCE = new RecipeSerializer<>(CODEC, STREAM_CODEC);
    }
}
