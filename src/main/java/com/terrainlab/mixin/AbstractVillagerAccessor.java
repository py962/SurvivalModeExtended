package com.terrainlab.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@code AbstractVillager#addOffersFromItemListings} for WanderingTraderSampleCountMixin.
 * Targets the declaring superclass because {@code @Shadow} cannot resolve inherited methods.
 */
@Mixin(AbstractVillager.class)
public interface AbstractVillagerAccessor {

    @Invoker("addOffersFromItemListings")
    void terrainlab$invokeAddOffersFromItemListings(
            ServerLevel level, MerchantOffers offers, VillagerTrades.ItemListing[] pool, int count
    );
}
