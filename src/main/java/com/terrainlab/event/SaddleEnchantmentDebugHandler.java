package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * TEMP DIAGNOSTIC: logs whether Frost Walker, Feather Falling and Depth Strider on a saddle
 * are loaded with the saddle slot and take effect on the mount (startup slot check, equip,
 * per-second environment snapshot, and fall-damage reduction).
 * <p>
 * TODO: remove once saddle enchantment effects are confirmed in-game.
 */
public class SaddleEnchantmentDebugHandler {

    private static final String[] WATCHED_ENCHANTMENTS = {
            "frost_walker", "feather_falling", "depth_strider"
    };

    private static final Map<String, Holder<Enchantment>> RESOLVED = new HashMap<>();

    private static Holder<Enchantment> resolve(RegistryAccess registryAccess, String path) {
        return RESOLVED.computeIfAbsent(path, p -> {
            ResourceKey<Enchantment> key = ResourceKey.create(
                    Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath("minecraft", p));
            return registryAccess.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        });
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        RegistryAccess registryAccess = event.getServer().registryAccess();
        for (String path : WATCHED_ENCHANTMENTS) {
            Holder<Enchantment> holder = resolve(registryAccess, path);
            boolean matchesSaddle = holder.value().matchingSlot(EquipmentSlot.SADDLE);
            // TerrainLab.LOGGER.info(
                    // "[SADDLE-ENCHANT] startup check minecraft:{} slots={} matchingSlot(SADDLE)={}",
                    // path, holder.value().definition().slots(), matchesSaddle);
        }
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getSlot() != EquipmentSlot.SADDLE) return;

        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel)) return;

        ItemStack to = event.getTo();
        if (to.isEmpty()) return;

        RegistryAccess registryAccess = entity.registryAccess();
        for (String path : WATCHED_ENCHANTMENTS) {
            Holder<Enchantment> holder = resolve(registryAccess, path);
            int level = EnchantmentHelper.getItemEnchantmentLevel(holder, to);
            if (level > 0) {
                // TerrainLab.LOGGER.info(
                        // "[SADDLE-ENCHANT] {} equipped onto {} (slot=SADDLE) carrying minecraft:{} level {} matchingSlot(SADDLE)={}",
                        // to.getItem(), entity.getType().toShortString(), path, level,
                        // holder.value().matchingSlot(EquipmentSlot.SADDLE));
            }
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity instanceof Player) return;
        if (entity.tickCount % 20 != 0) return;
        if (!(entity.level() instanceof ServerLevel level)) return;

        ItemStack saddle = entity.getItemBySlot(EquipmentSlot.SADDLE);
        if (saddle.isEmpty()) return;

        RegistryAccess registryAccess = entity.registryAccess();

        int frostLevel = EnchantmentHelper.getItemEnchantmentLevel(resolve(registryAccess, "frost_walker"), saddle);
        if (frostLevel > 0) {
            BlockPos feet = entity.blockPosition();
            BlockState atFeet = level.getBlockState(feet);
            BlockState below = level.getBlockState(feet.below());
            // TerrainLab.LOGGER.info(
                    // "[SADDLE-ENCHANT] frost_walker env-check {} onGround={} hasVehicle={} atFeet={} below={}",
                    // entity.getType().toShortString(), entity.onGround(), entity.getVehicle() != null,
                    // atFeet.getBlock(), below.getBlock());
        }

        int depthLevel = EnchantmentHelper.getItemEnchantmentLevel(resolve(registryAccess, "depth_strider"), saddle);
        if (depthLevel > 0) {
            double efficiency = entity.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY);
            // TerrainLab.LOGGER.info(
                    // "[SADDLE-ENCHANT] depth_strider env-check {} waterMovementEfficiency={}",
                    // entity.getType().toShortString(), efficiency);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return;
        if (!(entity.level() instanceof ServerLevel)) return;
        if (entity.getItemBySlot(EquipmentSlot.SADDLE).isEmpty()) return;
        if (!event.getSource().is(DamageTypeTags.IS_FALL)) return;

        // TerrainLab.LOGGER.info(
                // "[SADDLE-ENCHANT] feather_falling damage-check {} original={} final={} enchantReduction={}",
                // entity.getType().toShortString(), event.getOriginalDamage(), event.getNewDamage(),
                // event.getReduction(DamageContainer.Reduction.ENCHANTMENTS));
    }
}
