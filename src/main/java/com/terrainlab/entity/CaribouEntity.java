
        package com.terrainlab.entity;

import com.terrainlab.event.CelestialLuckXpHandler;
import com.terrainlab.registry.ModAttachments;
import com.terrainlab.registry.ModItems;
import com.terrainlab.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

public class CaribouEntity extends Llama {

    private static final long REGROWTH_COOLDOWN_TICKS = 24000L;

    public CaribouEntity(
            EntityType<? extends Llama> entityType,
            Level level
    ) {
        super(entityType, level);
    }

    @Override
    public void performRangedAttack(
            LivingEntity target,
            float distanceFactor
    ) {
        // No-op: caribou never spit.
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.CARIBOU_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(
            net.minecraft.world.damagesource.DamageSource damageSource
    ) {
        return ModSounds.CARIBOU_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.CARIBOU_DEATH.get();
    }

    /**
     * Spawns on animal-spawnable ground without a light-level check.
     */
    public static boolean checkCaribouSpawnRules(
            EntityType<CaribouEntity> type,
            LevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random
    ) {
        return level.getBlockState(pos.below())
                .is(BlockTags.ANIMALS_SPAWNABLE_ON);
    }

    /**
     * Shears produce caribou fur, with a one-day regrowth cooldown.
     */
    @Override
    public InteractionResult mobInteract(
            Player player,
            InteractionHand hand
    ) {
        ItemStack itemInHand = player.getItemInHand(hand);

        if (!itemInHand.is(Items.SHEARS)) {
            return super.mobInteract(player, hand);
        }

        long lastSheared = getData(
                ModAttachments.LAST_SHEARED_TIME.get()
        );

        long now = level().getGameTime();

        if (lastSheared >= 0
                && now - lastSheared < REGROWTH_COOLDOWN_TICKS) {
            // On cooldown: no fur, durability loss, sound or XP.
            return InteractionResult.CONSUME;
        }

        if (!level().isClientSide()) {
            ItemEntity furDrop = new ItemEntity(
                    level(),
                    getX(),
                    getY() + 0.5,
                    getZ(),
                    new ItemStack(
                            ModItems.CARIBOU_FUR.get(),
                            1 + random.nextInt(2)
                    )
            );

            level().addFreshEntity(furDrop);

            setData(
                    ModAttachments.LAST_SHEARED_TIME.get(),
                    now
            );

            itemInHand.hurtAndBreak(
                    1,
                    player,
                    EquipmentSlot.MAINHAND
            );

            playSound(
                    SoundEvents.SHEEP_SHEAR,
                    1.0F,
                    1.0F
            );

            // Award 2 XP after successful shearing, if within a Luck Conduit.
            if (level() instanceof ServerLevel serverLevel) {
                CelestialLuckXpHandler.awardShearingXp(
                        serverLevel,
                        this
                );
            }
        }

        return InteractionResult.SUCCESS;
    }
}
