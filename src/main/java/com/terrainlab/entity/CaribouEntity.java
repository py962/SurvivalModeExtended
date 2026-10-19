
        package com.terrainlab.entity;

import com.terrainlab.event.CelestialLuckXpHandler;
import com.terrainlab.registry.ModAttachments;
import com.terrainlab.registry.ModEntities;
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
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.jspecify.annotations.Nullable;

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

    /**
     * Bred calves are caribou (Llama#getBreedOffspring then copies strength and variant from
     * the parents). Crossbreeding with llamas is blocked by LlamaCaribouMateMixin.
     */
    @Override
    protected @Nullable Llama makeNewLlama() {
        return ModEntities.CARIBOU.get().create(this.level(), EntitySpawnReason.BREEDING);
    }

    /**
     * Calves are the caribou box (and eye height) at half size. Llama's own baby size is built
     * from the llama's eye height, which would put a calf's eyes above its head.
     */
    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        EntityDimensions adult = this.getType().getDimensions();
        return this.isBaby() ? adult.scale(this.getAgeScale()) : adult;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isBaby() ? ModSounds.CARIBOU_BABY_AMBIENT.get() : ModSounds.CARIBOU_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(
            net.minecraft.world.damagesource.DamageSource damageSource
    ) {
        return this.isBaby() ? ModSounds.CARIBOU_BABY_HURT.get() : ModSounds.CARIBOU_HURT.get();
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

            // Award shearing XP after successful shearing (applies everywhere).
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
