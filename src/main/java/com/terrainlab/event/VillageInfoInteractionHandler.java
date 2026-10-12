package com.terrainlab.event;

import com.terrainlab.menu.VillageInfoMenu;
import com.terrainlab.menu.VillageInfoMenuProvider;
import com.terrainlab.village.VillageInfoSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Right-clicking a vanilla Bell opens the read-only village-info GUI, populated from a
 * {@link VillageInfoSnapshot} taken at click time. The click is cancelled, so the bell no
 * longer rings when used by hand.
 */
public final class VillageInfoInteractionHandler {

    private VillageInfoInteractionHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }
        if (!level.getBlockState(event.getPos()).is(Blocks.BELL)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos bellPos = event.getPos();
        VillageInfoSnapshot.Snapshot snapshot = VillageInfoSnapshot.capture(serverLevel, bellPos);

        serverPlayer.openMenu(new VillageInfoMenuProvider(snapshot),
                buf -> VillageInfoMenu.writeSnapshot(buf, snapshot));

        event.setCanceled(true);
    }
}
