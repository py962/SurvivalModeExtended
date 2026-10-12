package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModAttachments;
import com.terrainlab.attachment.HorseSprintConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Applies the horse sprint speed boost while the rider holds sprint, draining and
 * regenerating per-horse stamina with an exhaustion lockout.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class HorseSprintHandler {

    @SubscribeEvent
    public static void onHorseTick(EntityTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;
        // Horse only; donkeys, mules, skeleton/zombie horses and camels are excluded.
        if (!(event.getEntity() instanceof Horse horse)) return;

        AttributeInstance speedAttr = horse.getAttributes().getInstance(Attributes.MOVEMENT_SPEED);
        if (speedAttr == null) return;

        Entity controller = horse.getControllingPassenger();
        // isSprinting() does not reflect the sprint key while mounted; the client sends it via HorseSprintInputPacket.
        boolean riderWantsSprint = controller instanceof Player player
                && player.getData(ModAttachments.PLAYER_WANTS_HORSE_SPRINT.get());

        float stamina = horse.getData(ModAttachments.HORSE_STAMINA.get());
        boolean exhausted = horse.getData(ModAttachments.HORSE_EXHAUSTED.get());

        if (exhausted && stamina >= HorseSprintConfig.RESUME_THRESHOLD) {
            exhausted = false;
        }

        boolean canSprint = riderWantsSprint && !exhausted && stamina > 0.0f;

        if (controller instanceof Player && horse.tickCount % 20 == 0) {
            // TerrainLab.LOGGER.info(
            //         "[HORSE-SPRINT] controllerIsPlayer={} syncedWantsSprint={} riderWantsSprint={} "
            //                 + "stamina={} exhausted={} canSprint={} hasModifier={} baseSpeed={} finalSpeed={}",
            //         controller instanceof Player,
            //         controller instanceof Player player && player.getData(ModAttachments.PLAYER_WANTS_HORSE_SPRINT.get()),
            //         riderWantsSprint,
            //         stamina,
            //         exhausted,
            //         canSprint,
            //         speedAttr.hasModifier(HorseSprintConfig.SPRINT_MODIFIER_ID),
            //         speedAttr.getBaseValue(),
            //         speedAttr.getValue()
            // );
        }

        if (canSprint) {
            if (!speedAttr.hasModifier(HorseSprintConfig.SPRINT_MODIFIER_ID)) {
                speedAttr.addTransientModifier(new AttributeModifier(
                        HorseSprintConfig.SPRINT_MODIFIER_ID,
                        HorseSprintConfig.SPRINT_MULTIPLIER,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
            }
            stamina -= HorseSprintConfig.DRAIN_PER_TICK;
            if (stamina <= 0.0f) {
                stamina = 0.0f;
                exhausted = true;
            }
        } else {
            if (speedAttr.hasModifier(HorseSprintConfig.SPRINT_MODIFIER_ID)) {
                speedAttr.removeModifier(HorseSprintConfig.SPRINT_MODIFIER_ID);
            }
            stamina = Math.min(HorseSprintConfig.MAX_STAMINA, stamina + HorseSprintConfig.REGEN_PER_TICK);
        }

        horse.setData(ModAttachments.HORSE_STAMINA.get(), stamina);
        horse.setData(ModAttachments.HORSE_EXHAUSTED.get(), exhausted);
    }
}
