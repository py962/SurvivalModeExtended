package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.pet.AnchoredPets;
import com.terrainlab.util.SafeSpotFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.TamableAnimal;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Safety net for tamed anchored pets (wolves, cats, geese, owls, vultures): on fire, drowning or freezing damage the pet is
 * teleported to the nearest safe spot.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class PetSafetyTeleportHandler {

    private static final int SEARCH_RADIUS = 10; // blocks

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof TamableAnimal pet)) return;
        if (!AnchoredPets.isAnchoredPet(pet)) return;
        if (!pet.isTame()) return;

        boolean isHazard = event.getSource().is(DamageTypeTags.IS_FIRE)
                || event.getSource().is(DamageTypeTags.IS_DROWNING)
                || event.getSource().is(DamageTypeTags.IS_FREEZING);
        if (!isHazard) return;
        if (pet.level().isClientSide()) return;

        // Already clear of the hazard and only still burning: put the fire out rather than
        // teleporting again on every burn tick.
        if (event.getSource().is(DamageTypeTags.IS_FIRE) && !pet.isInLava() && !isInFireBlock(pet)) {
            pet.clearFire();
            return;
        }

        BlockPos safe = SafeSpotFinder.findNearestSafeSpot(pet.level(), pet.blockPosition(), SEARCH_RADIUS);
        if (safe != null) {
            pet.teleportTo(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
            pet.clearFire();
        }
    }

    private static boolean isInFireBlock(TamableAnimal pet) {
        return pet.level().getBlockStatesIfLoaded(pet.getBoundingBox())
                .anyMatch(state -> state.is(net.minecraft.tags.BlockTags.FIRE));
    }
}
