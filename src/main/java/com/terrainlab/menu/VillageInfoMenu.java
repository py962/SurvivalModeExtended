
package com.terrainlab.menu;

import com.terrainlab.registry.ModMenuTypes;
import com.terrainlab.village.VillageInfoSnapshot;
import com.terrainlab.village.VillageTier;
import com.terrainlab.village.VillagerThermalState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only menu for the bell-click village info screen.
 * Has no slots; the village snapshot is sent once in the open-menu buffer.
 * Reopen to refresh.
 */
public class VillageInfoMenu extends AbstractContainerMenu {

    private final VillageInfoSnapshot.Snapshot snapshot;

    /**
     * Client-side reconstruction: called by the MenuType factory
     * registered in ModMenuTypes.
     */
    public VillageInfoMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, readSnapshot(buf));
    }

    /**
     * Server-side: real data, captured once by
     * VillageInfoInteractionHandler at click-time.
     */
    public VillageInfoMenu(
            int id,
            Inventory inventory,
            VillageInfoSnapshot.Snapshot snapshot
    ) {
        super(ModMenuTypes.VILLAGE_INFO_MENU.get(), id);
        this.snapshot = snapshot;
    }

    public VillageInfoSnapshot.Snapshot getSnapshot() {
        return snapshot;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        BlockPos bellPos = snapshot.bellPos();
        return bellPos == null
                || player.blockPosition().distSqr(bellPos) < 400.0;
    }

    public static void writeSnapshot(
            FriendlyByteBuf buf,
            VillageInfoSnapshot.Snapshot snapshot
    ) {
        buf.writeBlockPos(snapshot.bellPos());
        buf.writeUtf(snapshot.villageType());
        buf.writeInt(snapshot.population());
        buf.writeDouble(snapshot.happiness());
        buf.writeDouble(snapshot.dayAverageHappiness());
        buf.writeInt(snapshot.tradeQuality());
        buf.writeUtf(snapshot.tier().name());

        buf.writeVarInt(snapshot.villagers().size());

        for (VillageInfoSnapshot.Row row : snapshot.villagers()) {
            buf.writeUtf(row.professionKey());
            buf.writeInt(row.level());
            buf.writeBoolean(row.scared());
            buf.writeBoolean(row.tired());
            buf.writeBoolean(row.trapped());
            buf.writeBoolean(row.hungry());
            buf.writeUtf(row.thermalState().name());
        }
    }

    private static VillageInfoSnapshot.Snapshot readSnapshot(
            FriendlyByteBuf buf
    ) {
        BlockPos bellPos = buf.readBlockPos();
        String villageType = buf.readUtf();
        int population = buf.readInt();
        double happiness = buf.readDouble();
        double dayAverageHappiness = buf.readDouble();
        int tradeQuality = buf.readInt();
        VillageTier tier = VillageTier.valueOf(buf.readUtf());

        int rowCount = buf.readVarInt();
        List<VillageInfoSnapshot.Row> rows = new ArrayList<>(rowCount);

        for (int i = 0; i < rowCount; i++) {
            String professionKey = buf.readUtf();
            int level = buf.readInt();
            boolean scared = buf.readBoolean();
            boolean tired = buf.readBoolean();
            boolean trapped = buf.readBoolean();
            boolean hungry = buf.readBoolean();

            VillagerThermalState thermalState =
                    VillagerThermalState.valueOf(buf.readUtf());

            rows.add(new VillageInfoSnapshot.Row(
                    professionKey,
                    level,
                    scared,
                    tired,
                    trapped,
                    hungry,
                    thermalState
            ));
        }

        return new VillageInfoSnapshot.Snapshot(
                bellPos,
                villageType,
                population,
                happiness,
                dayAverageHappiness,
                tradeQuality,
                tier,
                rows
        );
    }
}
