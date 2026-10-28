package com.terrainlab.registry;

import com.terrainlab.entity.OversizedMapFrameEntity;
import com.terrainlab.entity.GhostFishEntity;
import com.terrainlab.entity.GraniteGolemEntity;
import com.terrainlab.entity.HouseboatEntity;
import com.terrainlab.entity.FrostbittenEntity;
import com.terrainlab.entity.CaribouEntity;
import com.terrainlab.entity.OwlEntity;
import com.terrainlab.entity.GooseEntity;
import com.terrainlab.entity.VultureEntity;
import com.terrainlab.entity.GrizzlyBearEntity;
import com.terrainlab.entity.VoidGlassFallingEntity;
import com.terrainlab.entity.RangerEntity;
import com.terrainlab.entity.ArcheologistEntity;
import com.terrainlab.entity.MarinerEntity;
import com.terrainlab.entity.PriestEntity;
import com.terrainlab.entity.PirateRaiderEntity;
import com.terrainlab.entity.OutlawRaiderEntity;
import com.terrainlab.entity.TrapperRaiderEntity;
import com.terrainlab.entity.DispenserMinecart;
import com.terrainlab.entity.DropperMinecart;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.entity.vehicle.boat.ChestBoat;
import net.neoforged.bus.api.IEventBus;
import com.terrainlab.entity.TieredArrowEntity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.terrainlab.TerrainLab.MODID;

/** Entity type registrations. */
public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MODID);


    // Oversized (2x2 / 3x3) map frames. The entity's bounding box spans the whole
    // footprint on a wall, floor or ceiling (OversizedMapFrameEntity#calculateBoundingBox),
    // so .sized() below is unused; the renderer draws one enlarged frame and map.
    public static final DeferredHolder<EntityType<?>, EntityType<OversizedMapFrameEntity>>
            LARGE_MAP_FRAME =
            ENTITY_TYPES.register("large_map_frame", id ->
                    EntityType.Builder.<OversizedMapFrameEntity>of(
                                    OversizedMapFrameEntity::new,
                                    MobCategory.MISC)
                            .noLootTable()
                            .sized(0.5F, 0.5F)
                            .eyeHeight(0.0F)
                            .clientTrackingRange(10)
                            .updateInterval(Integer.MAX_VALUE)
                            .build(net.minecraft.resources.ResourceKey.create(
                                    Registries.ENTITY_TYPE, id)));

    public static final DeferredHolder<EntityType<?>, EntityType<OversizedMapFrameEntity>>
            EXTRA_LARGE_MAP_FRAME =
            ENTITY_TYPES.register("extra_large_map_frame", id ->
                    EntityType.Builder.<OversizedMapFrameEntity>of(
                                    OversizedMapFrameEntity::new,
                                    MobCategory.MISC)
                            .noLootTable()
                            .sized(0.5F, 0.5F)
                            .eyeHeight(0.0F)
                            .clientTrackingRange(10)
                            .updateInterval(Integer.MAX_VALUE)
                            .build(net.minecraft.resources.ResourceKey.create(
                                    Registries.ENTITY_TYPE, id)));


    public static final DeferredHolder<EntityType<?>, EntityType<HouseboatEntity>> HOUSEBOAT =
            ENTITY_TYPES.register("houseboat", id ->
                    EntityType.Builder.<HouseboatEntity>of(
                                    (type, level) -> new HouseboatEntity(type, level,
                                            () -> ModItems.HOUSEBOAT_ITEM.get()),
                                    MobCategory.MISC)
                            .sized(3.0F, 0.5625F)
                            .build(net.minecraft.resources.ResourceKey.create(
                                    Registries.ENTITY_TYPE, id)));

    public static final DeferredHolder<EntityType<?>, EntityType<GooseEntity>> GOOSE =
            ENTITY_TYPES.register("goose", id ->
                    EntityType.Builder.<GooseEntity>of(
                                    GooseEntity::new,
                                    MobCategory.CREATURE)
                            .sized(0.5F, 1.0F)
                            .build(net.minecraft.resources.ResourceKey.create(
                                    Registries.ENTITY_TYPE, id)));

    public static final DeferredHolder<EntityType<?>, EntityType<VultureEntity>> VULTURE =
            ENTITY_TYPES.register("vulture", id ->
                    EntityType.Builder.<VultureEntity>of(
                                    VultureEntity::new,
                                    MobCategory.CREATURE)
                            .sized(0.7F, 1.3F)
                            .build(net.minecraft.resources.ResourceKey.create(
                                    Registries.ENTITY_TYPE, id)));

    public static final DeferredHolder<EntityType<?>, EntityType<GraniteGolemEntity>> GRANITE_GOLEM =
            ENTITY_TYPES.register("granite_golem", id ->
                    EntityType.Builder.<GraniteGolemEntity>of(
                                    GraniteGolemEntity::new,
                                    MobCategory.MISC)
                            .sized(1.4F, 2.7F)
                            .build(net.minecraft.resources.ResourceKey.create(
                                    Registries.ENTITY_TYPE, id)));

    // Translucent, noclipping deep-sea fish; WATER_AMBIENT like vanilla fish.
    public static final DeferredHolder<EntityType<?>, EntityType<GhostFishEntity>> GHOST_FISH =
            ENTITY_TYPES.register("ghost_fish", id ->
                    EntityType.Builder.<GhostFishEntity>of(
                                    GhostFishEntity::new,
                                    MobCategory.WATER_AMBIENT)
                            .sized(0.5F, 0.3F)
                            .build(net.minecraft.resources.ResourceKey.create(
                                    Registries.ENTITY_TYPE, id)));

    // Tipped arrow with normal/splash/lingering potion tiers and feather flight modifiers; vanilla arrow size.
    public static final DeferredHolder<EntityType<?>, EntityType<TieredArrowEntity>> TIERED_ARROW =
            ENTITY_TYPES.register("tiered_arrow", id ->
                    EntityType.Builder.<TieredArrowEntity>of(
                                    TieredArrowEntity::new,
                                    MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(4)
                            .updateInterval(20)
                            .build(net.minecraft.resources.ResourceKey.create(
                                    Registries.ENTITY_TYPE, id)));

    // Container minecarts that fire on powered activator rails; sized like vanilla's hopper minecart.
    public static final DeferredHolder<EntityType<?>, EntityType<DispenserMinecart>> DISPENSER_MINECART =
            ENTITY_TYPES.register("dispenser_minecart", id ->
                    EntityType.Builder.<DispenserMinecart>of(DispenserMinecart::new, MobCategory.MISC)
                            .noLootTable()
                            .sized(0.98F, 0.7F)
                            .passengerAttachments(0.1875F)
                            .clientTrackingRange(8)
                            .build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, id)));

    public static final DeferredHolder<EntityType<?>, EntityType<DropperMinecart>> DROPPER_MINECART =
            ENTITY_TYPES.register("dropper_minecart", id ->
                    EntityType.Builder.<DropperMinecart>of(DropperMinecart::new, MobCategory.MISC)
                            .noLootTable()
                            .sized(0.98F, 0.7F)
                            .passengerAttachments(0.1875F)
                            .clientTrackingRange(8)
                            .build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, id)));

    // Hitboxes below match each mob's vanilla parent unless noted.

    // Zombie size.
    public static final DeferredHolder<EntityType<?>, EntityType<FrostbittenEntity>> FROSTBITTEN =
            ENTITY_TYPES.register("frostbitten", id ->
                    EntityType.Builder.<FrostbittenEntity>of(
                                    FrostbittenEntity::new,
                                    MobCategory.MONSTER)
                            .sized(0.6F, 1.95F)
                            .build(net.minecraft.resources.ResourceKey.create(
                                    Registries.ENTITY_TYPE, id)));

    // Llama size.
    public static final DeferredHolder<EntityType<?>, EntityType<CaribouEntity>> CARIBOU =
            ENTITY_TYPES.register("caribou", id ->
                    EntityType.Builder.<CaribouEntity>of(
                                    CaribouEntity::new,
                                    MobCategory.CREATURE)
                            .sized(0.9F, 1.87F)
                            // Head centre is ~1.25 blocks up (cow-like stance); the 1.87 height is antlers.
                            .eyeHeight(1.4F)
                            .build(net.minecraft.resources.ResourceKey.create(
                                    Registries.ENTITY_TYPE, id)));

    // Larger than a parrot, matching OwlRenderer's 1.4x scale.
    public static final DeferredHolder<EntityType<?>, EntityType<OwlEntity>> OWL =
            ENTITY_TYPES.register("owl", id ->
                    EntityType.Builder.<OwlEntity>of(
                                    OwlEntity::new,
                                    MobCategory.CREATURE)
                            .sized(0.7F, 1.3F)
                            .build(net.minecraft.resources.ResourceKey.create(
                                    Registries.ENTITY_TYPE, id)));

    // Polar bear size.
    public static final DeferredHolder<EntityType<?>, EntityType<GrizzlyBearEntity>> GRIZZLY_BEAR =
            ENTITY_TYPES.register("grizzly_bear", id ->
                    EntityType.Builder.<GrizzlyBearEntity>of(
                                    GrizzlyBearEntity::new,
                                    MobCategory.CREATURE)
                            .sized(1.3F, 1.4F)
                            .build(net.minecraft.resources.ResourceKey.create(
                                    Registries.ENTITY_TYPE, id)));

    // No vanilla analogue; roughly double a ravager's hitbox.
    public static final DeferredHolder<EntityType<?>, EntityType<com.terrainlab.entity.ElephantEntity>> ELEPHANT =
            ENTITY_TYPES.register("elephant", id ->
                    EntityType.Builder.<com.terrainlab.entity.ElephantEntity>of(
                                    com.terrainlab.entity.ElephantEntity::new,
                                    MobCategory.CREATURE)
                            // Matches the model's body: 2-block-wide trunk plus ears, ~4 blocks long
                            // (a square box can't follow the length), back at 3.75, head top 4.1.
                            .sized(3.2F, 3.9F)
                            .eyeHeight(3.4F)
                            .build(net.minecraft.resources.ResourceKey.create(
                                    Registries.ENTITY_TYPE, id)));

    // Falling Void Glass block, sized like vanilla's falling_block.
    public static final DeferredHolder<EntityType<?>, EntityType<VoidGlassFallingEntity>> VOID_GLASS_FALLING =
            ENTITY_TYPES.register("void_glass_falling", id ->
                    EntityType.Builder.<VoidGlassFallingEntity>of(
                                    VoidGlassFallingEntity::new,
                                    MobCategory.MISC)
                            .sized(0.98F, 0.98F)
                            .build(net.minecraft.resources.ResourceKey.create(
                                    Registries.ENTITY_TYPE, id)));

    // Trail Traders: wandering-trader variants with natural biome spawning. CREATURE rather than
    // MISC because MISC is not part of the natural spawn cycle. Wandering-trader hitbox.
    public static final DeferredHolder<EntityType<?>, EntityType<RangerEntity>> RANGER =
            ENTITY_TYPES.register("ranger", id ->
                    EntityType.Builder.<RangerEntity>of(
                                    RangerEntity::new,
                                    MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .build(net.minecraft.resources.ResourceKey.create(
                                    Registries.ENTITY_TYPE, id)));

    public static final DeferredHolder<EntityType<?>, EntityType<ArcheologistEntity>> ARCHEOLOGIST =
            ENTITY_TYPES.register("archeologist", id ->
                    EntityType.Builder.<ArcheologistEntity>of(
                                    ArcheologistEntity::new,
                                    MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .build(net.minecraft.resources.ResourceKey.create(
                                    Registries.ENTITY_TYPE, id)));

    public static final DeferredHolder<EntityType<?>, EntityType<MarinerEntity>> MARINER =
            ENTITY_TYPES.register("mariner", id ->
                    EntityType.Builder.<MarinerEntity>of(
                                    MarinerEntity::new,
                                    MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .build(net.minecraft.resources.ResourceKey.create(
                                    Registries.ENTITY_TYPE, id)));

    public static final DeferredHolder<EntityType<?>, EntityType<PriestEntity>> PRIEST =
            ENTITY_TYPES.register("priest", id ->
                    EntityType.Builder.<PriestEntity>of(
                                    PriestEntity::new,
                                    MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .build(net.minecraft.resources.ResourceKey.create(
                                    Registries.ENTITY_TYPE, id)));

    // Pillager raider variants: vanilla Pillager behaviour and size; they differ in gear layers and
    // the structures that spawn them.
    public static final DeferredHolder<EntityType<?>, EntityType<PirateRaiderEntity>> PIRATE_RAIDER =
            ENTITY_TYPES.register("pirate_raider", id ->
                    EntityType.Builder.<PirateRaiderEntity>of(
                                    PirateRaiderEntity::new,
                                    MobCategory.MONSTER)
                            .sized(0.6F, 1.95F)
                            .build(net.minecraft.resources.ResourceKey.create(
                                    Registries.ENTITY_TYPE, id)));

    // The outlaw rides a horse (OutlawPatrolSpawnHandler); without the illager ridingOffset the vehicle
    // attachment defaults to zero and the rider floats above the saddle.
    public static final DeferredHolder<EntityType<?>, EntityType<OutlawRaiderEntity>> OUTLAW_RAIDER =
            ENTITY_TYPES.register("outlaw_raider", id ->
                    EntityType.Builder.<OutlawRaiderEntity>of(
                                    OutlawRaiderEntity::new,
                                    MobCategory.MONSTER)
                            .sized(0.6F, 1.95F)
                            .passengerAttachments(2.0F)
                            .ridingOffset(-0.6F)
                            .build(net.minecraft.resources.ResourceKey.create(
                                    Registries.ENTITY_TYPE, id)));

    public static final DeferredHolder<EntityType<?>, EntityType<TrapperRaiderEntity>> TRAPPER_RAIDER =
            ENTITY_TYPES.register("trapper_raider", id ->
                    EntityType.Builder.<TrapperRaiderEntity>of(
                                    TrapperRaiderEntity::new,
                                    MobCategory.MONSTER)
                            .sized(0.6F, 1.95F)
                            .build(net.minecraft.resources.ResourceKey.create(
                                    Registries.ENTITY_TYPE, id)));

    // Wood-set boats: vanilla Boat / ChestBoat with their own entity types (as vanilla does per
    // wood since 1.21.2); the entity type decides the dropped item and the renderer's texture.
    public static final DeferredHolder<EntityType<?>, EntityType<Boat>> PINE_BOAT =
            boat("pine_boat", () -> ModItems.PINE_BOAT.get());
    public static final DeferredHolder<EntityType<?>, EntityType<ChestBoat>> PINE_CHEST_BOAT =
            chestBoat("pine_chest_boat", () -> ModItems.PINE_CHEST_BOAT.get());
    public static final DeferredHolder<EntityType<?>, EntityType<Boat>> AZALEA_BOAT =
            boat("azalea_boat", () -> ModItems.AZALEA_BOAT.get());
    public static final DeferredHolder<EntityType<?>, EntityType<ChestBoat>> AZALEA_CHEST_BOAT =
            chestBoat("azalea_chest_boat", () -> ModItems.AZALEA_CHEST_BOAT.get());
    public static final DeferredHolder<EntityType<?>, EntityType<Boat>> ENDERWOOD_BOAT =
            boat("enderwood_boat", () -> ModItems.ENDERWOOD_BOAT.get());
    public static final DeferredHolder<EntityType<?>, EntityType<ChestBoat>> ENDERWOOD_CHEST_BOAT =
            chestBoat("enderwood_chest_boat", () -> ModItems.ENDERWOOD_CHEST_BOAT.get());

    // Size, eye height and tracking range copied from vanilla's EntityType.OAK_BOAT.
    private static DeferredHolder<EntityType<?>, EntityType<Boat>> boat(
            String name, java.util.function.Supplier<net.minecraft.world.item.Item> dropItem) {
        return ENTITY_TYPES.register(name, id ->
                EntityType.Builder.<Boat>of((type, level) -> new Boat(type, level, dropItem), MobCategory.MISC)
                        .noLootTable()
                        .sized(1.375F, 0.5625F)
                        .eyeHeight(0.5625F)
                        .clientTrackingRange(10)
                        .build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, id)));
    }

    private static DeferredHolder<EntityType<?>, EntityType<ChestBoat>> chestBoat(
            String name, java.util.function.Supplier<net.minecraft.world.item.Item> dropItem) {
        return ENTITY_TYPES.register(name, id ->
                EntityType.Builder.<ChestBoat>of((type, level) -> new ChestBoat(type, level, dropItem), MobCategory.MISC)
                        .noLootTable()
                        .sized(1.375F, 0.5625F)
                        .eyeHeight(0.5625F)
                        .clientTrackingRange(10)
                        .build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, id)));
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
