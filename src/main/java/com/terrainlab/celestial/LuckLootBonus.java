package com.terrainlab.celestial;

import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jspecify.annotations.Nullable;

/**
 * Luck effect bonus roll for container-type loot: while the player opening/brushing/fishing/unlocking
 * has the vanilla Luck mob effect (Luck amulet, Luck conduit), every pool of the OUTERMOST loot table
 * gets one extra roll (like a vanilla bonus_rolls of +1 at luck 1).
 *
 * <p>Scope: tables whose param set is CHEST, VAULT, ARCHAEOLOGY or FISHING. Nested tables (loot_table
 * entries, e.g. gameplay/fishing -> gameplay/fishing/fish) are not boosted.
 *
 * <p>State is per thread: {@link #DEPTH} counts nested {@code LootTable.getRandomItemsRaw(LootContext, ..)}
 * calls, {@link #ACTIVE} is whether the depth-1 table qualified. Driven by LootTableLuckBonusMixin /
 * LootPoolLuckBonusMixin; always restored in a finally block.
 */
public final class LuckLootBonus {
    private LuckLootBonus() {}

    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);
    private static final ThreadLocal<boolean[]> ACTIVE = ThreadLocal.withInitial(() -> new boolean[1]);

    /** Called on entry to LootTable#getRandomItemsRaw(LootContext, Consumer). Returns the previous ACTIVE value. */
    public static boolean enter(LootTable table, LootContext context) {
        int depth = ++DEPTH.get()[0];
        boolean[] active = ACTIVE.get();
        boolean previous = active[0];
        if (depth == 1) {
            active[0] = qualifies(table, context);
        }
        return previous;
    }

    /** Called in finally on exit from LootTable#getRandomItemsRaw(LootContext, Consumer). */
    public static void exit(boolean previousActive) {
        int[] depth = DEPTH.get();
        depth[0] = Math.max(0, depth[0] - 1);
        ACTIVE.get()[0] = depth[0] == 0 ? false : previousActive;
    }

    /** Extra rolls for the pool currently being rolled (only the outermost table's own pools). */
    public static int extraRolls() {
        return DEPTH.get()[0] == 1 && ACTIVE.get()[0] ? 1 : 0;
    }

    private static final String VILLAGE_PREFIX = "chests/village/";

    /**
     * With Luck, village house chests roll their unmodified vanilla table (kept as
     * terrainlab:chests/village_vanilla/...) instead of the mod's low-value one. Only for the outermost
     * table; the substitute then gets the usual Luck bonus roll as the outermost table itself.
     */
    public static @Nullable LootTable luckySubstitute(LootTable table, LootContext context) {
        if (DEPTH.get()[0] != 0) return null;
        net.minecraft.resources.Identifier id;
        try {
            id = table.getLootTableId();
        } catch (NullPointerException e) {
            return null;
        }
        if (!id.getNamespace().equals("minecraft") || !id.getPath().startsWith(VILLAGE_PREFIX)) return null;
        if (!qualifies(table, context)) return null;
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(com.terrainlab.TerrainLab.MODID,
                        "chests/village_vanilla/" + id.getPath().substring(VILLAGE_PREFIX.length())));
        LootTable substitute = context.getLevel().getServer().reloadableRegistries().getLootTable(key);
        return substitute == LootTable.EMPTY ? null : substitute;
    }

    private static boolean qualifies(LootTable table, LootContext context) {
        ContextKeySet set = table.getParamSet();
        if (set != LootContextParamSets.CHEST && set != LootContextParamSets.VAULT
                && set != LootContextParamSets.ARCHAEOLOGY && set != LootContextParamSets.FISHING) {
            return false;
        }
        Player player = resolvePlayer(context);
        return player != null && !player.level().isClientSide() && player.hasEffect(MobEffects.LUCK);
    }

    /**
     * CHEST / VAULT / ARCHAEOLOGY: THIS_ENTITY is the player (absent for worldgen/unopened fills -> no bonus).
     * FISHING: THIS_ENTITY is the FishingHook (player = its owner); ATTACKING_ENTITY is the owner too.
     */
    private static @Nullable Player resolvePlayer(LootContext context) {
        Entity self = context.getOptional(LootContextParams.THIS_ENTITY);
        if (self instanceof Player p) return p;
        if (self instanceof FishingHook hook) {
            Player owner = hook.getPlayerOwner();
            if (owner != null) return owner;
        }
        Entity attacker = context.getOptional(LootContextParams.ATTACKING_ENTITY);
        return attacker instanceof Player p ? p : null;
    }
}
