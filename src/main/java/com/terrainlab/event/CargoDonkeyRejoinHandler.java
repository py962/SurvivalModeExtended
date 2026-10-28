package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.entity.AbstractTrailTraderEntity;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * Reunites a guide and its cargo donkey as soon as either loads. A guide that teleports while its
 * donkey's chunk is unloaded leaves the donkey behind; without this, the donkey would reattach its
 * lead by ID from far away (drawn across the gap, or snapped by vanilla). The move is deferred to
 * the server's task queue, since entities must not be moved while they are being added.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class CargoDonkeyRejoinHandler {

    private CargoDonkeyRejoinHandler() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        if (event.getEntity() instanceof Donkey donkey) {
            Optional<UUID> ownerId = donkey.getData(ModAttachments.CARGO_DONKEY_OWNER_TRADER.get());
            if (ownerId.isPresent()) {
                UUID owner = ownerId.get();
                level.getServer().execute(() -> {
                    if (level.getEntity(owner) instanceof AbstractTrailTraderEntity guide) {
                        bringToGuide(donkey, guide);
                    }
                });
            }
        } else if (event.getEntity() instanceof AbstractTrailTraderEntity guide) {
            UUID guideId = guide.getUUID();
            level.getServer().execute(() -> {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof Donkey donkey
                            && donkey.getData(ModAttachments.CARGO_DONKEY_OWNER_TRADER.get())
                            .filter(guideId::equals).isPresent()) {
                        bringToGuide(donkey, guide);
                    }
                }
            });
        }
    }

    private static void bringToGuide(Donkey donkey, AbstractTrailTraderEntity guide) {
        if (!donkey.isAlive() || !guide.isAlive() || donkey.level() != guide.level()) {
            return;
        }
        // Out of range: lead removed without a drop, moved beside the guide, fresh lead.
        AbstractTrailTraderEntity.reuniteCargoDonkey(donkey, guide, "rejoin");
    }
}
