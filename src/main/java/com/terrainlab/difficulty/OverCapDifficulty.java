package com.terrainlab.difficulty;

import com.terrainlab.TerrainLab;
import com.terrainlab.mixin.CreeperAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Difficulty past vanilla's cap. Vanilla's mob effects stop growing at local difficulty 4.0; with the
 * per-player score (PlayerDifficulty) a player can reach 9.0 on Isolation. The part above 4.0, as a
 * fraction of the way to 9.0 ({@link #excess}), drives extra effects, about 50% harder at the top:
 * <ul>
 *   <li>hostile mobs: +50% health and attack damage, +10% speed;</li>
 *   <li>zombies and skeletons: missing armour filled with iron and worn armour upgraded a tier
 *       (each up to 50% of the time), gear enchanted more often and at higher levels;</li>
 *   <li>zombies call reinforcements more; skeletons shoot up to 50% faster; pillagers get Quick Charge;</li>
 *   <li>creepers up to 25% charged; up to 15% of hostile mobs are elites with Speed, Strength or
 *       Resistance;</li>
 *   <li>hostile mob cap around the player up to 50% higher (see the mob cap mixins).</li>
 * </ul>
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class OverCapDifficulty {
    private static final float CAP = 4.0F;
    private static final float TOP = 9.0F;

    private static final double HEALTH_BONUS = 0.5;
    private static final double DAMAGE_BONUS = 0.5;
    private static final double SPEED_BONUS = 0.1;
    private static final double REINFORCEMENT_BONUS = 0.05;
    private static final float GEAR_CHANCE = 0.5F;
    private static final int EXTRA_ENCHANT_LEVELS = 12;
    private static final float CHARGED_CREEPER_CHANCE = 0.25F;
    private static final float ELITE_CHANCE = 0.15F;
    public static final float FASTER_SHOOTING = 0.5F;
    public static final float MOB_CAP_BONUS = 0.5F;

    private static final Identifier HEALTH_ID = id("over_cap_health");
    private static final Identifier DAMAGE_ID = id("over_cap_damage");
    private static final Identifier SPEED_ID = id("over_cap_speed");
    private static final Identifier REINFORCEMENT_ID = id("over_cap_reinforcements");

    /** Spawned mobs waiting to join the level, with the excess at their spawn point. */
    private static final Map<Mob, Float> PENDING = Collections.synchronizedMap(new WeakHashMap<>());
    /** Each online player's own excess, refreshed every second, for the mob cap checks. */
    private static final Map<UUID, Float> PLAYER_EXCESS = new HashMap<>();
    private static volatile float highestExcess = 0.0F;

    private OverCapDifficulty() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TerrainLab.MODID, path);
    }

    /** 0 at local difficulty 4.0 or below, rising to 1 at 9.0. */
    public static float excess(ServerLevel level, BlockPos pos) {
        float effective = PlayerDifficulty.effectiveAt(level, pos);
        if (Float.isNaN(effective)) return 0.0F;
        return Mth.clamp((effective - CAP) / (TOP - CAP), 0.0F, 1.0F);
    }

    public static float playerExcess(ServerPlayer player) {
        return PLAYER_EXCESS.getOrDefault(player.getUUID(), 0.0F);
    }

    /** The largest player excess on the server, for the global mob cap. */
    public static float highestExcess() {
        return highestExcess;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;
        PLAYER_EXCESS.clear();
        float highest = 0.0F;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            float e = excess(player.level(), player.blockPosition());
            PLAYER_EXCESS.put(player.getUUID(), e);
            highest = Math.max(highest, e);
        }
        highestExcess = highest;
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        Mob mob = event.getEntity();
        if (!(mob instanceof Enemy) || !(event.getLevel().getLevel() instanceof ServerLevel level)) return;
        float e = excess(level, BlockPos.containing(event.getX(), event.getY(), event.getZ()));
        if (e > 0.0F) PENDING.put(mob, e);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getEntity() instanceof Mob mob)) return;
        Float e = PENDING.remove(mob);
        if (e == null || !(event.getLevel() instanceof ServerLevel level)) return;
        apply(mob, level, e);
    }

    private static void apply(Mob mob, ServerLevel level, float e) {
        RandomSource random = mob.getRandom();

        addBonus(mob.getAttribute(Attributes.MAX_HEALTH), HEALTH_ID, HEALTH_BONUS * e);
        mob.setHealth(mob.getMaxHealth());
        addBonus(mob.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_ID, DAMAGE_BONUS * e);
        addBonus(mob.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID, SPEED_BONUS * e);

        if (mob instanceof Zombie) {
            AttributeInstance reinforcements = mob.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
            if (reinforcements != null) {
                reinforcements.addOrReplacePermanentModifier(new AttributeModifier(REINFORCEMENT_ID,
                        REINFORCEMENT_BONUS * e, AttributeModifier.Operation.ADD_VALUE));
            }
        }
        if (mob instanceof Zombie || mob instanceof AbstractSkeleton) {
            upgradeGear(mob, level, random, e);
        }
        if (mob instanceof AbstractSkeleton skeleton) {
            skeleton.reassessWeaponGoal(); // picks up the faster shooting (AbstractSkeletonFasterShootingMixin)
        }
        if (mob instanceof Pillager) {
            ItemStack crossbow = mob.getMainHandItem();
            if (crossbow.is(Items.CROSSBOW) && random.nextFloat() < e) {
                var quickCharge = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.QUICK_CHARGE);
                crossbow.enchant(quickCharge, e > 0.66F ? 2 : 1);
            }
        }
        if (mob instanceof Creeper creeper && random.nextFloat() < CHARGED_CREEPER_CHANCE * e) {
            creeper.getEntityData().set(CreeperAccessor.terrainlab$poweredKey(), true);
        }
        if (random.nextFloat() < ELITE_CHANCE * e) {
            makeElite(mob, random);
        }
    }

    private static void addBonus(AttributeInstance attribute, Identifier id, double amount) {
        if (attribute == null || amount <= 0.0) return;
        attribute.addOrReplacePermanentModifier(
                new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    private static void upgradeGear(Mob mob, ServerLevel level, RandomSource random, float e) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack worn = mob.getItemBySlot(slot);
            if (random.nextFloat() >= GEAR_CHANCE * e) continue;
            Item better = worn.isEmpty() ? armour("iron", slot) : upgrade(worn.getItem(), slot);
            if (better != null) {
                mob.setItemSlot(slot, new ItemStack(better));
            }
        }
        int cost = 5 + 18 + Math.round(EXTRA_ENCHANT_LEVELS * e);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = mob.getItemBySlot(slot);
            if (stack.isEmpty() || stack.isEnchanted() || random.nextFloat() >= GEAR_CHANCE * e) continue;
            mob.setItemSlot(slot, EnchantmentHelper.enchantItem(random, stack, cost,
                    level.registryAccess(), Optional.empty()));
        }
    }

    private static Item upgrade(Item item, EquipmentSlot slot) {
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (path.startsWith("iron_")) return armour("diamond", slot);
        if (path.startsWith("leather_") || path.startsWith("golden_") || path.startsWith("chainmail_")
                || path.startsWith("copper_")) {
            return armour("iron", slot);
        }
        return null;
    }

    private static Item armour(String material, EquipmentSlot slot) {
        String piece = switch (slot) {
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            case FEET -> "boots";
            default -> null;
        };
        if (piece == null) return null;
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(material + "_" + piece));
        return item == Items.AIR ? null : item;
    }

    private static void makeElite(Mob mob, RandomSource random) {
        var effects = java.util.List.of(MobEffects.SPEED, MobEffects.STRENGTH, MobEffects.RESISTANCE);
        boolean any = false;
        for (var effect : effects) {
            if (random.nextBoolean()) {
                mob.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, 0));
                any = true;
            }
        }
        if (!any) {
            mob.addEffect(new MobEffectInstance(effects.get(random.nextInt(effects.size())),
                    MobEffectInstance.INFINITE_DURATION, 0));
        }
    }
}
