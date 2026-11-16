package com.terrainlab.village;

import com.terrainlab.entity.GraniteGolemEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.Comparator;

/**
 * Toolsmiths, weaponsmiths and armorers patch up damaged iron and copper golems within 8 blocks, and
 * masons granite golems: on each staggered read, the most damaged one nearby regains what an iron
 * ingot gives.
 */
public final class SmithGolemRepairService {

    private SmithGolemRepairService() {
    }

    private static final double RANGE = 8.0;
    private static final float REPAIR_AMOUNT = 25.0F;

    public static void update(Villager villager, ServerLevel level) {
        if (villager.isBaby() || villager.isSleeping() || villager.isTrading()) {
            return;
        }
        boolean smith = isSmith(villager);
        boolean mason = villager.getVillagerData().profession().is(VillagerProfession.MASON);
        if (!smith && !mason) {
            return;
        }
        level.getEntitiesOfClass(AbstractGolem.class, villager.getBoundingBox().inflate(RANGE),
                        g -> g.isAlive() && !(g instanceof SnowGolem) && !(g instanceof Shulker)
                                && (g instanceof GraniteGolemEntity ? mason : smith)
                                && g.getHealth() < g.getMaxHealth() && villager.distanceToSqr(g) <= RANGE * RANGE)
                .stream()
                .min(Comparator.comparingDouble(g -> g.getHealth() / g.getMaxHealth()))
                .ifPresent(golem -> {
                    villager.getLookControl().setLookAt(golem);
                    golem.heal(REPAIR_AMOUNT);
                    level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.IRON_GOLEM_REPAIR,
                            SoundSource.NEUTRAL, 1.0F, 1.0F);
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, golem.getX(), golem.getY() + golem.getBbHeight() * 0.6,
                            golem.getZ(), 6, 0.4, 0.5, 0.4, 0.0);
                });
    }

    private static boolean isSmith(Villager villager) {
        var profession = villager.getVillagerData().profession();
        return profession.is(VillagerProfession.TOOLSMITH)
                || profession.is(VillagerProfession.WEAPONSMITH)
                || profession.is(VillagerProfession.ARMORER);
    }
}
