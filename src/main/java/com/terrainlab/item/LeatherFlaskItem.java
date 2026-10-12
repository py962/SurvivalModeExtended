package com.terrainlab.item;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModDataComponents;
import com.terrainlab.temperature.PlayerInternalTemperature;
import com.terrainlab.temperature.PlayerTemperatureConstants;
import com.terrainlab.temperature.WaterDrinkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Refillable water flask holding {@link LeatherFlaskConstants#MAX_CHARGES} charges; each drink applies
 * the same cooling push as other cooling drinks. Right-clicking a small body of water refills it,
 * right-clicking elsewhere drinks one charge. A missing charge component reads as full.
 * <p>
 * Overrides {@code use} rather than {@code useOn} because the default block raycast ignores fluids.
 */
public class LeatherFlaskItem extends Item {

    private static final int USE_DURATION_TICKS = 32; // matches vanilla potion drink duration

    public LeatherFlaskItem(Properties properties) {
        super(properties);
    }

    private static int getCharges(ItemStack stack) {
        Integer charges = stack.get(ModDataComponents.FLASK_CHARGES.get());
        // TerrainLab.LOGGER.info("[LeatherFlask] getCharges() raw component value: {}", charges);
        return charges != null ? charges : LeatherFlaskConstants.MAX_CHARGES;
    }

    /**
     * Flasks in a stack share one charge count, so a refill or a drink splits one flask off
     * the stack and changes only that one.
     */
    private static void setChargesOnOne(ItemStack stack, int charges, Player player) {
        if (stack.getCount() <= 1) {
            stack.set(ModDataComponents.FLASK_CHARGES.get(), charges);
            return;
        }
        if (getCharges(stack) == charges) {
            return;
        }
        ItemStack single = stack.copyWithCount(1);
        single.set(ModDataComponents.FLASK_CHARGES.get(), charges);
        stack.shrink(1);
        if (!player.getInventory().add(single)) {
            player.drop(single, false);
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // TerrainLab.LOGGER.info("[LeatherFlask] use() called, clientSide={}, hand={}", level.isClientSide(), hand);

        BlockHitResult hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        // TerrainLab.LOGGER.info("[LeatherFlask] raycast hit type: {}", hitResult.getType());

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            var pos = hitResult.getBlockPos();
            boolean isWater = level.getFluidState(pos).is(FluidTags.WATER);
            boolean isLargeBody = isWater && WaterDrinkHandler.isLargeBodyOfWater(level, pos);
            // TerrainLab.LOGGER.info("[LeatherFlask] hit block at {}, isWater={}, isLargeBody={}", pos, isWater, isLargeBody);

            if (isWater && !isLargeBody) {
                if (!level.isClientSide()) {
                    setChargesOnOne(stack, LeatherFlaskConstants.MAX_CHARGES, player);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0F, 1.0F);
                    // TerrainLab.LOGGER.info("[LeatherFlask] refilled to MAX_CHARGES={}", LeatherFlaskConstants.MAX_CHARGES);
                }
                return InteractionResult.SUCCESS;
            }
        }

        // Start drinking manually: Item#use only does this for CONSUMABLE-component items.
        int duration = getUseDuration(stack, player);
        // TerrainLab.LOGGER.info("[LeatherFlask] no water hit, getUseDuration()={}", duration);

        if (duration > 0) {
            player.startUsingItem(hand);
            // TerrainLab.LOGGER.info("[LeatherFlask] startUsingItem() called, returning CONSUME");
            return InteractionResult.CONSUME;
        }
        // TerrainLab.LOGGER.info("[LeatherFlask] duration <= 0, returning PASS (likely 0 charges)");
        return InteractionResult.PASS;
    }


/** Plays the vanilla potion-drinking sound while the flask is being used. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseTicks) {
        if (level.isClientSide()) {
            int elapsed = USE_DURATION_TICKS - remainingUseTicks;

            if (elapsed > USE_DURATION_TICKS * 0.21875F && remainingUseTicks % 4 == 0) {
                entity.playSound(
                        SoundEvents.GENERIC_DRINK.value(),
                        0.5F,
                        net.minecraft.util.Mth.randomBetween(entity.getRandom(), 0.9F, 1.0F)
                );
            }
        }
    }



    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        ItemUseAnimation anim = getCharges(stack) > 0 ? ItemUseAnimation.DRINK : ItemUseAnimation.NONE;
        // TerrainLab.LOGGER.info("[LeatherFlask] getUseAnimation() -> {}", anim);
        return anim;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        int duration = getCharges(stack) > 0 ? USE_DURATION_TICKS : 0;
        // TerrainLab.LOGGER.info("[LeatherFlask] getUseDuration() -> {}", duration);
        return duration;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        // TerrainLab.LOGGER.info("[LeatherFlask] finishUsingItem() called, clientSide={}", level.isClientSide());
        if (!level.isClientSide() && entity instanceof ServerPlayer player) {
            int charges = getCharges(stack);
            if (charges > 0) {
                setChargesOnOne(stack, charges - 1, player);
                PlayerInternalTemperature.applyInstantPush(player, PlayerTemperatureConstants.COOLING_FOOD_PUSH);
                // TerrainLab.LOGGER.info("[LeatherFlask] consumed charge, {} -> {}, cooling push applied", charges, charges - 1);
            } else {
                // TerrainLab.LOGGER.info("[LeatherFlask] finishUsingItem() called with 0 charges, no-op");
            }
        }
        return stack;
    }

    // Item bar shows the fill level; always visible, so a full flask shows a full bar.
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int charges = getCharges(stack);
        return Math.round(13.0F * charges / LeatherFlaskConstants.MAX_CHARGES);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        // Default biome water colour.
        return 0x3F76E4;
    }
}
