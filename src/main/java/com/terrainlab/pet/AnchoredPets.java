package com.terrainlab.pet;

import com.terrainlab.entity.GooseEntity;
import com.terrainlab.entity.OwlEntity;
import com.terrainlab.entity.VultureEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.wolf.Wolf;

/**
 * The pets that are bed-anchored instead of following their owner (PetBedAnchorHandler,
 * PetRoamingHandler, FollowOwnerGoalMixin, TamableAnimalMixin, PetSafetyTeleportHandler,
 * GoatHornSummonHandler). Vanilla parrots are deliberately excluded.
 */
public final class AnchoredPets {

    private AnchoredPets() {}

    /** Wolves, cats and the mod's tamable birds (goose, owl, vulture). Checks type only, not tameness. */
    public static boolean isAnchoredPet(Object entity) {
        return entity instanceof Wolf
                || entity instanceof Cat
                || isAnchoredBird(entity);
    }

    /** The flying anchored pets: these get {@link FlyTowardsHomeGoal} instead of MoveTowardsRestrictionGoal. */
    public static boolean isAnchoredBird(Object entity) {
        return entity instanceof GooseEntity
                || entity instanceof OwlEntity
                || entity instanceof VultureEntity;
    }

    /** True for a tamed pet ordered to sit: its own wandering goals must not move it. */
    public static boolean isSittingPet(TamableAnimal pet) {
        return pet.isTame() && pet.isOrderedToSit();
    }

    /** True for a tamed pet with a roaming zone that it is currently outside of (it should head home, not wander). */
    public static boolean isTameAndAwayFromHome(TamableAnimal pet) {
        return pet.isTame() && pet.hasHome() && !pet.isWithinHome();
    }

    /**
     * Whether a tamed pet may pick {@code target} as a wandering destination: always true for wild
     * animals or pets without a roaming zone, otherwise the target must lie inside the zone.
     */
    public static boolean isAllowedTarget(TamableAnimal pet, BlockPos target) {
        return !pet.isTame() || !pet.hasHome() || pet.isWithinHome(target);
    }

    /** Whether a tamed pet's own wander/travel goals may start at all (not sitting, not outside its zone). */
    public static boolean mayWander(TamableAnimal pet) {
        return !isSittingPet(pet) && !isTameAndAwayFromHome(pet);
    }
}
