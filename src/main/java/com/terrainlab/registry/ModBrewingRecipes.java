package com.terrainlab.registry;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

/** Brewing recipes for the mod's potions. */
@EventBusSubscriber(modid = com.terrainlab.TerrainLab.MODID)
public class ModBrewingRecipes {

    @SubscribeEvent
    public static void registerBrewingRecipes(RegisterBrewingRecipesEvent event) {
        PotionBrewing.Builder builder = event.getBuilder();

        // Base mixes from Awkward Potion; addStartMix also adds the Water -> Mundane mix, as in vanilla.
        builder.addStartMix(Items.PITCHER_PLANT, ModPotions.GRIP);
        builder.addStartMix(Items.TORCHFLOWER, ModPotions.WARMTH);
        builder.addStartMix(Items.FIRE_CHARGE, ModPotions.EXPLOSIVE);
        builder.addStartMix(ModItems.SCULK_FLOWER.get(), ModPotions.SPECTRAL_VISION);
        builder.addStartMix(ModItems.GHOST_FISH_SCALE.get(), ModPotions.PHASING);
        builder.addStartMix(ModItems.NIGHTCAP_MUSHROOM.get(), ModPotions.BORROWING);

        // Fermented Spider Eye inversions.
        builder.addMix(ModPotions.PHASING.getDelegate(), Items.FERMENTED_SPIDER_EYE, ModPotions.PHASE_BLOCKING.getDelegate());
        builder.addMix(ModPotions.GRIP.getDelegate(), Items.FERMENTED_SPIDER_EYE, ModPotions.SLIPPERY.getDelegate());
        builder.addMix(ModPotions.WARMTH.getDelegate(), Items.FERMENTED_SPIDER_EYE, ModPotions.FREEZING.getDelegate());

        // Redstone extends.
        builder.addMix(ModPotions.PHASING.getDelegate(), Items.REDSTONE, ModPotions.LONG_PHASING.getDelegate());
        builder.addMix(ModPotions.PHASE_BLOCKING.getDelegate(), Items.REDSTONE, ModPotions.LONG_PHASE_BLOCKING.getDelegate());
        builder.addMix(ModPotions.GRIP.getDelegate(), Items.REDSTONE, ModPotions.LONG_GRIP.getDelegate());
        builder.addMix(ModPotions.SLIPPERY.getDelegate(), Items.REDSTONE, ModPotions.LONG_SLIPPERY.getDelegate());
        builder.addMix(ModPotions.BORROWING.getDelegate(), Items.REDSTONE, ModPotions.LONG_BORROWING.getDelegate());
        builder.addMix(ModPotions.WARMTH.getDelegate(), Items.REDSTONE, ModPotions.LONG_WARMTH.getDelegate());
        builder.addMix(ModPotions.FREEZING.getDelegate(), Items.REDSTONE, ModPotions.LONG_FREEZING.getDelegate());
        builder.addMix(ModPotions.SPECTRAL_VISION.getDelegate(), Items.REDSTONE, ModPotions.LONG_SPECTRAL_VISION.getDelegate());

        // Glowstone amplifies.
        builder.addMix(ModPotions.EXPLOSIVE.getDelegate(), Items.GLOWSTONE_DUST, ModPotions.STRONG_EXPLOSIVE.getDelegate());

        // Long variants invert to long variants.
        builder.addMix(ModPotions.LONG_PHASING.getDelegate(), Items.FERMENTED_SPIDER_EYE, ModPotions.LONG_PHASE_BLOCKING.getDelegate());
        builder.addMix(ModPotions.LONG_GRIP.getDelegate(), Items.FERMENTED_SPIDER_EYE, ModPotions.LONG_SLIPPERY.getDelegate());
        builder.addMix(ModPotions.LONG_WARMTH.getDelegate(), Items.FERMENTED_SPIDER_EYE, ModPotions.LONG_FREEZING.getDelegate());
    }
}
