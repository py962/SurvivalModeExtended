package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.entity.GooseEntity;
import com.terrainlab.entity.VultureEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** Tamed geese and vultures chase whoever attacks their owner, without doing damage. */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class PetBirdDefenceHandler {

    private static final double DEFEND_RANGE = 16.0;

    private PetBirdDefenceHandler() {
    }

    @SubscribeEvent
    public static void onOwnerHurt(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer owner)
                || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || attacker == owner
                || (attacker instanceof TamableAnimal pet && pet.isOwnedBy(owner))) {
            return;
        }
        for (Parrot bird : owner.level().getEntitiesOfClass(Parrot.class, owner.getBoundingBox().inflate(DEFEND_RANGE),
                bird -> bird.isTame() && bird.isOwnedBy(owner))) {
            if (bird instanceof GooseEntity goose) {
                goose.defendOwner(attacker);
            } else if (bird instanceof VultureEntity vulture) {
                vulture.defendOwner(attacker);
            }
        }
    }
}
