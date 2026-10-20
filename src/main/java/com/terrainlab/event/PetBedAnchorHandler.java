package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.entity.ElephantEntity;
import com.terrainlab.entity.HouseboatEntity;
import com.terrainlab.pet.AnchoredPets;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;

import java.util.List;
import java.util.Optional;

/**
 * When a player sets their spawn, anchors each of their nearby tamed anchored pets (wolves, cats,
 * geese, owls, vultures - see {@link AnchoredPets}) to that
 * position (one-time claim; already-anchored pets are left alone). Temporary beds neither set
 * the spawn nor anchor pets: the houseboat/howdah phantom beds (the player is still riding the
 * vehicle when the event fires) and any block in the {@code terrainlab:temporary_beds} tag.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class PetBedAnchorHandler {

    private static final double CLAIM_RADIUS = 32.0; // blocks

    /** Max squared distance from vehicle to its phantom bed (actual is at most ~3.2 blocks). */
    private static final double TEMP_BED_MAX_DIST_SQ = 25.0;

    public static final TagKey<Block> TEMPORARY_BEDS = TagKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(TerrainLab.MODID, "temporary_beds"));

    private static final boolean DEBUG_LOGGING = false;

    @SubscribeEvent
    public static void onSetSpawn(PlayerSetSpawnEvent event) {
        BlockPos bedPos = event.getNewSpawn();
        if (bedPos == null) {
            // if (DEBUG_LOGGING) TerrainLab.LOGGER.info("[PET_ANCHOR] spawn cleared (null) - ignoring");
            return;
        }

        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) {
            // if (DEBUG_LOGGING) TerrainLab.LOGGER.info("[PET_ANCHOR] client-side event - ignoring");
            return;
        }

        if (isTemporaryBed(player, level, bedPos)) {
            // A temporary bed is removed after use, so it must not replace the player's real spawn.
            // Cancelling keeps the previous respawn point; sleeping itself is unaffected.
            event.setCanceled(true);
            if (DEBUG_LOGGING) {
                // TerrainLab.LOGGER.info("[PET_ANCHOR] {} spawn set at {} is a TEMPORARY bed (vehicle={}) - not anchoring pets",
                        // player.getName().getString(), bedPos, player.getVehicle());
            }
            return;
        }

        List<TamableAnimal> pets = level.getEntitiesOfClass(
                TamableAnimal.class,
                player.getBoundingBox().inflate(CLAIM_RADIUS),
                pet -> AnchoredPets.isAnchoredPet(pet)
                        && pet.isTame()
                        && pet.isOwnedBy(player));

        if (DEBUG_LOGGING) {
            // TerrainLab.LOGGER.info("[PET_ANCHOR] real bed at {}: {} owned anchored pets within {} blocks",
                    // bedPos, pets.size(), CLAIM_RADIUS);
        }

        for (TamableAnimal pet : pets) {
            Optional<BlockPos> current = pet.getData(ModAttachments.ANCHOR_BED_POS.get());
            if (current.isEmpty()) {
                pet.setData(ModAttachments.ANCHOR_BED_POS.get(), Optional.of(bedPos));
                // if (DEBUG_LOGGING) TerrainLab.LOGGER.info("[PET_ANCHOR] anchored {} to {}", pet, bedPos);
            } else if (DEBUG_LOGGING) {
                // TerrainLab.LOGGER.info("[PET_ANCHOR] {} already anchored to {} - untouched", pet, current.get());
            }
        }
    }

    /**
     * True for a houseboat/howdah phantom bed (player still riding, bed next to the vehicle)
     * or any block in the temporary_beds tag.
     */
    private static boolean isTemporaryBed(Player player, ServerLevel level, BlockPos bedPos) {
        Entity vehicle = player.getVehicle();
        if ((vehicle instanceof HouseboatEntity || vehicle instanceof ElephantEntity)
                && bedPos.distSqr(vehicle.blockPosition()) <= TEMP_BED_MAX_DIST_SQ) {
            return true;
        }
        return level.getBlockState(bedPos).is(TEMPORARY_BEDS);
    }
}
