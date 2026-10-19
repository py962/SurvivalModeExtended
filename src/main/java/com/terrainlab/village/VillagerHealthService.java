package com.terrainlab.village;

import com.terrainlab.celestial.CelestialConduitRegistry;
import com.terrainlab.celestial.CelestialVariant;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.villager.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

/**
 * Villager healing rules. Every heal on a villager is cancelled except:
 * <ol>
 *   <li>heals made through {@link #healAllowed} by this mod: food eaten from the village chest
 *       (instant or over time via {@link VillagerPendingHeal}) and the slow natural heal below;</li>
 *   <li>Regeneration while an active Life conduit covers the villager (the conduit's own
 *       Regeneration; any other Regeneration inside that range also passes).</li>
 * </ol>
 * Natural healing is {@link #NATURAL_HEAL_AMOUNT} HP per {@link #NATURAL_HEAL_INTERVAL_TICKS},
 * counted from when the villager was last at full health. As a consequence vanilla's level-up
 * Regeneration, potions and any other heal source do nothing on villagers.
 * <p>
 * {@link #update} runs on the staggered villager read (every 100 ticks), so pending heals are
 * delivered in 100-tick steps.
 */
public final class VillagerHealthService {

    public static final long NATURAL_HEAL_INTERVAL_TICKS = 24000L; // one in-game day
    public static final float NATURAL_HEAL_AMOUNT = 1.0F;           // half a heart

    /** Re-entrancy-safe "this heal is ours" marker; healing only happens on the server thread. */
    private static int allowedHealDepth = 0;

    private VillagerHealthService() {
    }

    /** Heals the villager past the healing block. */
    public static void healAllowed(Villager villager, float amount) {
        if (amount <= 0.0F) {
            return;
        }
        allowedHealDepth++;
        try {
            villager.heal(amount);
        } finally {
            allowedHealDepth--;
        }
    }

    /** Queues a heal of {@code amount} HP spread over {@code ticks}; instant if {@code ticks <= 0}. */
    public static void queueHeal(Villager villager, float amount, int ticks) {
        if (amount <= 0.0F) {
            return;
        }
        if (ticks <= 0) {
            healAllowed(villager, amount);
            return;
        }
        VillagerPendingHeal current = villager.getData(ModAttachments.VILLAGER_PENDING_HEAL.get());
        VillagerPendingHeal merged = current.isEmpty()
                ? new VillagerPendingHeal(amount, ticks)
                : new VillagerPendingHeal(current.amount() + amount, Math.max(current.ticks(), ticks));
        villager.setData(ModAttachments.VILLAGER_PENDING_HEAL.get(), merged);
    }

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof Villager villager && !isHealAllowed(villager)) {
            event.setCanceled(true);
        }
    }

    private static boolean isHealAllowed(Villager villager) {
        if (allowedHealDepth > 0) {
            return true;
        }
        if (!(villager.level() instanceof ServerLevel level)) {
            return true; // client-side heals are cosmetic; the server's health is synced over them
        }
        return villager.hasEffect(MobEffects.REGENERATION)
                && CelestialConduitRegistry.get(level).hasActiveNearby(
                        CelestialVariant.LIFE, level.dimension(), villager.blockPosition());
    }

    /** Staggered read: delivers one step of any pending food heal, then the daily natural heal. */
    public static void update(Villager villager, long gameTime) {
        if (!villager.isAlive()) {
            return;
        }

        VillagerPendingHeal pending = villager.getData(ModAttachments.VILLAGER_PENDING_HEAL.get());
        if (!pending.isEmpty()) {
            int step = (int) Math.min(VillageConstants.STAGGER_INTERVAL_TICKS, pending.ticks());
            float amount = pending.ticks() <= step ? pending.amount() : pending.amount() * step / pending.ticks();
            healAllowed(villager, amount);
            int ticksLeft = pending.ticks() - step;
            villager.setData(ModAttachments.VILLAGER_PENDING_HEAL.get(), ticksLeft <= 0
                    ? VillagerPendingHeal.NONE
                    : new VillagerPendingHeal(pending.amount() - amount, ticksLeft));
        }

        long last = villager.getData(ModAttachments.VILLAGER_LAST_NATURAL_HEAL_TICK.get());
        if (villager.getHealth() >= villager.getMaxHealth() || last < 0L) {
            // The day's count starts from the last read at full health (or the first read ever).
            villager.setData(ModAttachments.VILLAGER_LAST_NATURAL_HEAL_TICK.get(), gameTime);
        } else if (gameTime - last >= NATURAL_HEAL_INTERVAL_TICKS) {
            healAllowed(villager, NATURAL_HEAL_AMOUNT);
            villager.setData(ModAttachments.VILLAGER_LAST_NATURAL_HEAL_TICK.get(), gameTime);
        }
    }
}
