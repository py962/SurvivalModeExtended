package com.terrainlab.celestial;

import com.terrainlab.item.CelestialAmuletItem;
import com.terrainlab.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Amulet-to-conduit pairing, shared by the custom Celestial conduits and the vanilla conduit
 * (Breath). Right-clicking a conduit with a same-family amulet in the main hand gives the amulet
 * that conduit's pair id; an unpaired conduit gets a fresh id first. Several amulets can share one
 * conduit.
 */
public final class CelestialPairing {

    private CelestialPairing() {
    }

    /**
     * Server side. Pairs {@code amulet} with the conduit at {@code pos}. {@code conduitPairId} is the
     * conduit's current id (null when unpaired); {@code assignConduitId} stores a new one on it.
     */
    public static void pair(ServerLevel level, BlockPos pos, Player player, ItemStack amulet,
                            CelestialVariant conduitVariant, @Nullable UUID conduitPairId,
                            Consumer<UUID> assignConduitId) {
        if (!(amulet.getItem() instanceof CelestialAmuletItem amuletItem)) {
            return;
        }

        if (amuletItem.variant() != conduitVariant) {
            player.sendOverlayMessage(Component.translatable(
                    "message.terrainlab.celestial.pair.wrong_variant", variantName(amuletItem.variant())));
            return;
        }

        if (conduitPairId == null) {
            // Drop the old positional registry entry; the conduit re-registers under its id on its next check.
            CelestialConduitRegistry.get(level)
                    .removeConduit(CelestialConduitRegistry.keyFor(null, level.dimension(), pos));
            conduitPairId = UUID.randomUUID();
            assignConduitId.accept(conduitPairId);
        }

        if (conduitPairId.equals(amulet.get(ModDataComponents.CELESTIAL_PAIR_ID.get()))) {
            player.sendOverlayMessage(Component.translatable("message.terrainlab.celestial.pair.already"));
            return;
        }

        // Only the amulet changes; the conduit keeps its id, so its other amulets stay paired.
        amulet.set(ModDataComponents.CELESTIAL_PAIR_ID.get(), conduitPairId);
        player.sendOverlayMessage(Component.translatable(
                "message.terrainlab.celestial.pair.paired", variantName(conduitVariant)));
    }

    /**
     * Whether {@code pairId} already belongs to a different conduit in the registry, so a newly placed
     * conduit carrying it must take a fresh id (two conduits made from one pearl pair).
     */
    public static boolean idTakenElsewhere(ServerLevel level, UUID pairId, BlockPos pos) {
        return CelestialConduitRegistry.get(level).get(pairId)
                .map(record -> !record.dimension().equals(level.dimension()) || !record.pos().equals(pos))
                .orElse(false);
    }

    private static String variantName(CelestialVariant variant) {
        return variant.id().toLowerCase(Locale.ROOT);
    }
}
