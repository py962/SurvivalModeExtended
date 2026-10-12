package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Shearing a vanilla goat drops Goat Fur, with a one-day regrowth cooldown tracked in the
 * {@code LAST_SHEARED_TIME} attachment.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class GoatShearHandler {

    // ticks; same regrowth cooldown as CaribouEntity.
    private static final long REGROWTH_COOLDOWN_TICKS = 24000L;

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Goat goat)) return;
        if (event.getLevel().isClientSide()) return;

        ItemStack itemInHand = event.getItemStack();
        if (!itemInHand.is(Items.SHEARS)) return;

        long lastSheared = goat.getData(com.terrainlab.registry.ModAttachments.LAST_SHEARED_TIME.get());
        long now = goat.level().getGameTime();
        if (lastSheared >= 0 && now - lastSheared < REGROWTH_COOLDOWN_TICKS) {
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
            return;
        }

        ItemEntity furDrop = new ItemEntity(
                event.getLevel(), goat.getX(), goat.getY() + 0.5, goat.getZ(),
                new ItemStack(ModItems.GOAT_FUR.get(), 1 + goat.getRandom().nextInt(2)));
        event.getLevel().addFreshEntity(furDrop);
        goat.setData(com.terrainlab.registry.ModAttachments.LAST_SHEARED_TIME.get(), now);

        itemInHand.hurtAndBreak(1, event.getEntity(), EquipmentSlot.MAINHAND);
        goat.playSound(SoundEvents.SHEEP_SHEAR, 1.0F, 1.0F);

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
