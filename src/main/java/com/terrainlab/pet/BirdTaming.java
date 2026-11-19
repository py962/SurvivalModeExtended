package com.terrainlab.pet;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.EventHooks;

/** Parrot-style taming for the mod's birds: each feeding has a 1 in 10 chance to tame. */
public final class BirdTaming {

    private static final int TAME_CHANCE = 10;

    private BirdTaming() {}

    /** Call after the item has been used up. */
    public static void fed(Parrot bird, Player player) {
        if (!bird.isSilent()) {
            bird.level().playSound(null, bird.getX(), bird.getY(), bird.getZ(), SoundEvents.PARROT_EAT,
                    bird.getSoundSource(), 1.0F, 1.0F + (bird.getRandom().nextFloat() - bird.getRandom().nextFloat()) * 0.2F);
        }
        if (!bird.level().isClientSide()) {
            if (bird.getRandom().nextInt(TAME_CHANCE) == 0 && !EventHooks.onAnimalTame(bird, player)) {
                bird.tame(player);
                bird.level().broadcastEntityEvent(bird, (byte) 7);
            } else {
                bird.level().broadcastEntityEvent(bird, (byte) 6);
            }
        }
    }
}
