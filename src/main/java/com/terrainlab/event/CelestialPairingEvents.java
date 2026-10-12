package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.blockentity.CelestialConduitBlockEntity;
import com.terrainlab.celestial.CelestialPairing;
import com.terrainlab.celestial.CelestialVariant;
import com.terrainlab.item.CelestialAmuletItem;
import com.terrainlab.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.UUID;

/**
 * Pairing pieces the conduit blocks cannot do themselves:
 * <ul>
 *   <li>Right-click pairing on the vanilla conduit (the Breath conduit), matching
 *   CelestialConduitBlock#useItemOn. Its pair id lives in the vanilla block entity's component map.</li>
 *   <li>One id per conduit: a conduit placed with an id another conduit already uses (two conduits
 *   made from one pearl pair) takes a fresh id, so the two never share a registry record.</li>
 * </ul>
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class CelestialPairingEvents {

    private CelestialPairingEvents() {
    }

    @SubscribeEvent
    public static void onRightClickConduit(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !event.getLevel().getBlockState(event.getPos()).is(Blocks.CONDUIT)) {
            return;
        }
        ItemStack amulet = event.getEntity().getMainHandItem();
        if (!(amulet.getItem() instanceof CelestialAmuletItem)) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (!(event.getLevel() instanceof ServerLevel level)
                || !(level.getBlockEntity(event.getPos()) instanceof ConduitBlockEntity conduit)) {
            return;
        }
        BlockPos pos = event.getPos();
        CelestialPairing.pair(level, pos, event.getEntity(), amulet, CelestialVariant.BREATH,
                conduit.components().get(ModDataComponents.CELESTIAL_PAIR_ID.get()),
                newId -> setVanillaPairId(conduit, newId));
    }

    @SubscribeEvent
    public static void onConduitPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (blockEntity instanceof CelestialConduitBlockEntity conduit) {
            UUID id = conduit.pairId();
            if (id != null && CelestialPairing.idTakenElsewhere(level, id, pos)) {
                conduit.setPairId(UUID.randomUUID());
                level.sendBlockUpdated(pos, event.getPlacedBlock(), event.getPlacedBlock(), 3);
            }
        } else if (blockEntity instanceof ConduitBlockEntity conduit) {
            UUID id = conduit.components().get(ModDataComponents.CELESTIAL_PAIR_ID.get());
            if (id != null && CelestialPairing.idTakenElsewhere(level, id, pos)) {
                setVanillaPairId(conduit, UUID.randomUUID());
            }
        }
    }

    private static void setVanillaPairId(ConduitBlockEntity conduit, UUID id) {
        conduit.setComponents(DataComponentMap.builder()
                .addAll(conduit.components())
                .set(ModDataComponents.CELESTIAL_PAIR_ID.get(), id)
                .build());
        conduit.setChanged();
    }
}
