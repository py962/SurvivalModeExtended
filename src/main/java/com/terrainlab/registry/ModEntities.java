package com.terrainlab.registry;

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
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import com.terrainlab.entity.TieredArrowEntity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.terrainlab.TerrainLab.MODID;

/** Entity type registrations. */
public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

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
                            .sized(5.3F, 4.0F)
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

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
