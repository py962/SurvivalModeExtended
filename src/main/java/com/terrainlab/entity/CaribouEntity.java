package com.terrainlab.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.level.Level;

/**
 * Caribou: a Llama (chest inventory, caravans, carpets, trader escort) that
 * never spits and can be sheared for fur on a cooldown. Drops come from its
 * loot table.
 */
public class CaribouEntity extends Llama {

    public CaribouEntity(EntityType<? extends Llama> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        // No-op: disables spitting; the spit goal still runs but produces nothing.
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return com.terrainlab.registry.ModSounds.CARIBOU_AMBIENT.get();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource damageSource) {
        return com.terrainlab.registry.ModSounds.CARIBOU_HURT.get();
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return com.terrainlab.registry.ModSounds.CARIBOU_DEATH.get();
    }

    /** Spawns on animal-spawnable ground; no light check. */
    public static boolean checkCaribouSpawnRules(net.minecraft.world.entity.EntityType<CaribouEntity> type,
                                                 net.minecraft.world.level.LevelAccessor level,
                                                 net.minecraft.world.entity.EntitySpawnReason reason,
                                                 net.minecraft.core.BlockPos pos,
                                                 net.minecraft.util.RandomSource random) {
        return level.getBlockState(pos.below()).is(net.minecraft.tags.BlockTags.ANIMALS_SPAWNABLE_ON);
    }

    // Fur regrowth cooldown: one in-game day.
    private static final long REGROWTH_COOLDOWN_TICKS = 24000L;

    // Shears are handled here; everything else falls through to Llama.
    @Override
    public net.minecraft.world.InteractionResult mobInteract(
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        net.minecraft.world.item.ItemStack itemInHand = player.getItemInHand(hand);
        if (itemInHand.is(net.minecraft.world.item.Items.SHEARS)) {
            long lastSheared = this.getData(com.terrainlab.registry.ModAttachments.LAST_SHEARED_TIME.get());
            long now = this.level().getGameTime();
            if (lastSheared >= 0 && now - lastSheared < REGROWTH_COOLDOWN_TICKS) {
                // On cooldown: consume the interaction with no fur, damage or sound.
                return net.minecraft.world.InteractionResult.CONSUME;
            }
            if (!this.level().isClientSide()) {
                net.minecraft.world.entity.item.ItemEntity furDrop = new net.minecraft.world.entity.item.ItemEntity(
                        this.level(), this.getX(), this.getY() + 0.5, this.getZ(),
                        new net.minecraft.world.item.ItemStack(com.terrainlab.registry.ModItems.CARIBOU_FUR.get(), 1 + this.random.nextInt(2)));
                this.level().addFreshEntity(furDrop);
                this.setData(com.terrainlab.registry.ModAttachments.LAST_SHEARED_TIME.get(), now);
                itemInHand.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                this.playSound(net.minecraft.sounds.SoundEvents.SHEEP_SHEAR, 1.0F, 1.0F);
            }
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }
}
