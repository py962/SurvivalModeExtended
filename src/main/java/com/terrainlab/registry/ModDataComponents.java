package com.terrainlab.registry;

import com.terrainlab.TerrainLab;
import com.terrainlab.celestial.CelestialVariant;
import com.terrainlab.entity.TieredArrowEntity;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.Unit;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;

/** Item data component types. */
public class ModDataComponents {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, TerrainLab.MODID);

    // Potion tier (none/normal/splash/lingering) a tiered arrow stack was fletched with.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TieredArrowEntity.PotionTier>> ARROW_TIER =
            DATA_COMPONENT_TYPES.register("arrow_tier", () ->
                    DataComponentType.<TieredArrowEntity.PotionTier>builder()
                            .persistent(Codec.STRING.xmap(
                                    TieredArrowEntity.PotionTier::valueOf,
                                    TieredArrowEntity.PotionTier::name))
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8.map(
                                    TieredArrowEntity.PotionTier::valueOf,
                                    TieredArrowEntity.PotionTier::name))
                            .build());

    // Leather flask remaining charges (0-20); a missing component is read as full.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> FLASK_CHARGES =
            DATA_COMPONENT_TYPES.register("flask_charges", () ->
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.VAR_INT)
                            .build());

    // Special flower a suspicious stew was made from, for effects that aren't MobEffects
    // (energy/health); Dandelion's effect goes into the vanilla component instead.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> STEW_FLOWER =
            DATA_COMPONENT_TYPES.register("stew_flower", () ->
                    DataComponentType.<String>builder()
                            .persistent(Codec.STRING)
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                            .build());

    // Pair id shared by the two pearls of one Celestial Forge craft and carried onto the
    // crafted conduit and amulet, so an amulet only links to its own conduit.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> CELESTIAL_PAIR_ID =
            DATA_COMPONENT_TYPES.register("celestial_pair_id", () ->
                    DataComponentType.<UUID>builder()
                            .persistent(UUIDUtil.CODEC)
                            .networkSynchronized(UUIDUtil.STREAM_CODEC)
                            .build());

    // Celestial variant of a pearl (conduit and amulet items are already per-variant).
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CelestialVariant>> CELESTIAL_VARIANT =
            DATA_COMPONENT_TYPES.register("celestial_variant", () ->
                    DataComponentType.<CelestialVariant>builder()
                            .persistent(CelestialVariant.CODEC)
                            .networkSynchronized(CelestialVariant.STREAM_CODEC)
                            .build());

    // Atlas stack with a generation job in flight, and its centre. Removed once MAP_ID is set:
    // untouched (neither), pending (this only) or done (MAP_ID only).
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> ATLAS_PENDING_ORIGIN =
            DATA_COMPONENT_TYPES.register("atlas_pending_origin", () ->
                    DataComponentType.<BlockPos>builder()
                            .persistent(BlockPos.CODEC)
                            .networkSynchronized(BlockPos.STREAM_CODEC)
                            .build());

    // Marks a villager trade cost that only accepts an enchanted copy of the item (see ItemCostEnchantedMixin).
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> REQUIRES_ENCHANTMENT =
            DATA_COMPONENT_TYPES.register("requires_enchantment", () ->
                    DataComponentType.<Unit>builder()
                            .persistent(Unit.CODEC)
                            .networkSynchronized(Unit.STREAM_CODEC)
                            .build());

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENT_TYPES.register(modEventBus);
    }
}
