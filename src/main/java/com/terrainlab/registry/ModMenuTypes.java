package com.terrainlab.registry;

import com.terrainlab.TerrainLab;
import com.terrainlab.menu.AncientCodexMenu;
import com.terrainlab.menu.FletchingTableMenu;
import com.terrainlab.menu.ReplicatorMenu;
import com.terrainlab.menu.VillageInfoMenu;
import com.terrainlab.menu.CelestialForgeMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Menu type registrations; the (id, inventory, buf) factories are the client-side constructors. */
public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(BuiltInRegistries.MENU, TerrainLab.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ReplicatorMenu>> REPLICATOR_MENU =
            MENU_TYPES.register("replicator", () ->
                    IMenuTypeExtension.create((id, inventory, buf) ->
                            new ReplicatorMenu(id, inventory, buf)));

    public static final DeferredHolder<MenuType<?>, MenuType<AncientCodexMenu>> ANCIENT_CODEX_MENU =
            MENU_TYPES.register("ancient_codex", () ->
                    IMenuTypeExtension.create(AncientCodexMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<FletchingTableMenu>> FLETCHING_TABLE_MENU =
            MENU_TYPES.register("fletching_table", () ->
                    IMenuTypeExtension.create((id, inventory, buf) ->
                            new FletchingTableMenu(id, inventory, buf)));

    // Read-only village info GUI opened from the bell.
    public static final DeferredHolder<MenuType<?>, MenuType<VillageInfoMenu>> VILLAGE_INFO_MENU =
            MENU_TYPES.register("village_info", () ->
                    IMenuTypeExtension.create((id, inventory, buf) ->
                            new VillageInfoMenu(id, inventory, buf)));

    public static final DeferredHolder<MenuType<?>, MenuType<CelestialForgeMenu>> CELESTIAL_FORGE_MENU =
            MENU_TYPES.register("celestial_forge", () ->
                    IMenuTypeExtension.create((id, inventory, buf) ->
                            new CelestialForgeMenu(id, inventory, buf)));

    public static void register(IEventBus eventBus) {
        MENU_TYPES.register(eventBus);
    }
}
