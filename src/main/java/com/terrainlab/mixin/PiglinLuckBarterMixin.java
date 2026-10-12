package com.terrainlab.mixin;

import com.terrainlab.celestial.CelestialConduitRegistry;
import com.terrainlab.celestial.CelestialVariant;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * A piglin makes two barters for each gold ingot when it is within range of an active Luck conduit,
 * or when the player it is bartering with (the one it throws the results to) wears an active Luck
 * amulet: the bartering loot table is rolled a second time and both results are thrown, as
 * separate stacks.
 */
@Mixin(PiglinAi.class)
public abstract class PiglinLuckBarterMixin {

    @Inject(method = "getBarterResponseItems", at = @At("RETURN"), cancellable = true, require = 1)
    private static void terrainlab$luckDoubleBarter(Piglin piglin, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (!(piglin.level() instanceof ServerLevel level)) {
            return;
        }

        boolean conduit = CelestialConduitRegistry.get(level)
                .hasActiveNearby(CelestialVariant.LUCK, level.dimension(), piglin.blockPosition());
        boolean amulet = piglin.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER)
                .map(player -> player.getData(ModAttachments.LUCK_AMULET_ACTIVE.get()))
                .orElse(false);

        if (!conduit && !amulet) {
            return;
        }

        // Same roll as vanilla's getBarterResponseItems (not called again, to avoid re-entering this hook).
        LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.PIGLIN_BARTERING);
        List<ItemStack> second = table.getRandomItems(
                new LootParams.Builder(level)
                        .withParameter(LootContextParams.THIS_ENTITY, piglin)
                        .create(LootContextParamSets.PIGLIN_BARTER));

        List<ItemStack> both = new ArrayList<>(cir.getReturnValue());
        both.addAll(second);
        cir.setReturnValue(both);
    }
}
