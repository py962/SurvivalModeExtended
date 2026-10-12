
        package com.terrainlab.item;

import com.terrainlab.entity.TieredArrowEntity;
import com.terrainlab.registry.ModDataComponents;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class TieredArrowItem extends ArrowItem {

    private final TieredArrowEntity.FeatherType featherType;

    public TieredArrowItem(
            Item.Properties properties,
            TieredArrowEntity.FeatherType featherType) {
        super(properties);
        this.featherType = featherType;
    }

    @Override
    public AbstractArrow createArrow(
            Level level,
            ItemStack ammo,
            LivingEntity shooter,
            @Nullable ItemStack weapon) {
        TieredArrowEntity arrow = new TieredArrowEntity(
                level,
                shooter,
                ammo.copyWithCount(1),
                weapon);

        configureFromStack(arrow, ammo);
        return arrow;
    }

    @Override
    public Projectile asProjectile(
            Level level,
            Position pos,
            ItemStack ammo,
            Direction direction) {
        TieredArrowEntity arrow = new TieredArrowEntity(
                com.terrainlab.registry.ModEntities.TIERED_ARROW.get(),
                level);

        arrow.setPos(pos.x(), pos.y(), pos.z());
        arrow.pickup = AbstractArrow.Pickup.ALLOWED;
        configureFromStack(arrow, ammo);
        return arrow;
    }

    /**
     * Allows Infinity bows to fire this custom arrow without consuming it.
     * Called by NeoForge's ammunition-use logic on the logical server.
     */
    @Override
    public boolean isInfinite(
            ItemStack ammo,
            ItemStack bow,
            LivingEntity livingEntity) {
        if (livingEntity.hasInfiniteMaterials()) {
            return true;
        }

        var enchantmentRegistry = livingEntity.level()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT);

        var infinity = enchantmentRegistry.get(Enchantments.INFINITY);

        return infinity.isPresent()
                && bow.getEnchantments().getLevel(infinity.get()) > 0;
    }

    private void configureFromStack(TieredArrowEntity arrow, ItemStack ammo) {
        arrow.setFeatherType(this.featherType);

        TieredArrowEntity.PotionTier tier = ammo.getOrDefault(
                ModDataComponents.ARROW_TIER.get(),
                TieredArrowEntity.PotionTier.NONE);

        if (tier != TieredArrowEntity.PotionTier.NONE) {
            arrow.setPotionTier(tier, ammo);
        }
    }
}

