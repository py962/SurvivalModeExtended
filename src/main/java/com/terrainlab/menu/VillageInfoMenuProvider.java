package com.terrainlab.menu;

import com.terrainlab.village.VillageInfoSnapshot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * MenuProvider for the bell's village info screen, wrapping an already-captured
 * {@link VillageInfoSnapshot.Snapshot}.
 */
public final class VillageInfoMenuProvider implements MenuProvider {

    private final VillageInfoSnapshot.Snapshot snapshot;

    public VillageInfoMenuProvider(VillageInfoSnapshot.Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("menu.terrainlab.village_info");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new VillageInfoMenu(containerId, inventory, snapshot);
    }
}
