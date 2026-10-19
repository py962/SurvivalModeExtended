package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;

/**
 * Enchanted (or named) leads survive leashing: the lead used to leash an entity is remembered on the
 * leashed entity in {@link ModAttachments#LEASH_LEAD_ITEM}, and the lead dropped when that leash breaks
 * or is untied is a copy of it instead of a plain lead.
 * <p>
 * The leash belongs to the leashed entity for its whole life, so moving it between holders (player, fence
 * knot, happy ghast, another player) keeps the stored lead. Hooks:
 * <ul>
 *   <li>{@code EntityLeadConsumeMixin}: vanilla right-click leashing (incl. happy ghasts and boats).</li>
 *   <li>{@link MountHitchHandler}: leashing a ridden mount with a held lead.</li>
 *   <li>{@code LeashableEnchantedLeadMixin}: drops the stored lead, forgets it when the leash is removed
 *   without a drop, and forgets stale data when a brand-new leash forms by other means.</li>
 *   <li>{@link ModLeashHandler}: the ghast's lead becoming a houseboat's tow line.</li>
 *   <li>Mob conversion (below): the converted mob keeps the leash, so it keeps the lead.</li>
 * </ul>
 * Server-side only.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class EnchantedLeadHandler {

    private EnchantedLeadHandler() {
    }

    /** Remembers {@code lead} on {@code leashed}; a plain lead clears any stored one. Call after the leash forms. */
    public static void record(Entity leashed, ItemStack lead) {
        if (leashed.level().isClientSide()) return;
        if (!lead.is(Items.LEAD) || lead.isComponentsPatchEmpty()) {
            forget(leashed);
        } else {
            leashed.setData(ModAttachments.LEASH_LEAD_ITEM.get(), lead.copyWithCount(1));
        }
    }

    /** The stored lead, or EMPTY. */
    public static ItemStack peek(Entity leashed) {
        return leashed.hasData(ModAttachments.LEASH_LEAD_ITEM.get())
                ? leashed.getData(ModAttachments.LEASH_LEAD_ITEM.get())
                : ItemStack.EMPTY;
    }

    /** The lead to drop for this entity's leash (stored copy, or a plain lead), clearing the stored one. */
    public static ItemStack takeLeadToDrop(Entity leashed) {
        ItemStack stored = peek(leashed);
        forget(leashed);
        return stored.isEmpty() ? new ItemStack(Items.LEAD) : stored.copy();
    }

    public static void forget(Entity leashed) {
        if (leashed.hasData(ModAttachments.LEASH_LEAD_ITEM.get())) {
            leashed.removeData(ModAttachments.LEASH_LEAD_ITEM.get());
        }
    }

    @SubscribeEvent
    public static void onConversion(LivingConversionEvent.Post event) {
        Entity from = event.getEntity();
        Entity to = event.getOutcome();
        if (from.level().isClientSide()) return;
        ItemStack stored = peek(from);
        if (stored.isEmpty()) return;
        if (to instanceof Leashable leashable && leashable.isLeashed()) {
            to.setData(ModAttachments.LEASH_LEAD_ITEM.get(), stored.copy());
        }
    }
}
