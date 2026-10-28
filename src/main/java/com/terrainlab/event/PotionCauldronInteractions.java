package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.block.PotionCauldronBlock;
import com.terrainlab.blockentity.PotionCauldronBlockEntity;
import com.terrainlab.item.LeatherFlaskItem;
import com.terrainlab.item.PotionMixing;
import com.terrainlab.registry.ModBlocks;
import com.terrainlab.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.cauldron.CauldronInteractions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCauldronInteractionEvent;

/**
 * Cauldron interactions:
 * <ul>
 *   <li>Infinite cauldron water: a glass bottle or a Leather Flask filled from a water cauldron
 *       leaves its level unchanged (buckets still empty it).</li>
 *   <li>Potion mixing: drinkable potions with effects poured into an empty cauldron make a
 *       {@link PotionCauldronBlock} (up to 3 potions); a glass bottle takes them all back out as
 *       one Custom Potion ({@link PotionMixing}) and empties the cauldron.</li>
 * </ul>
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class PotionCauldronInteractions {

    /** Interactions of the potion cauldron block. */
    public static final CauldronInteraction.Dispatcher POTION_CAULDRON = new CauldronInteraction.Dispatcher();

    private PotionCauldronInteractions() {
    }

    @SubscribeEvent
    static void onRegisterDispatchers(RegisterCauldronInteractionEvent.Dispatcher event) {
        event.register(Identifier.fromNamespaceAndPath(TerrainLab.MODID, "potion"), POTION_CAULDRON);
    }

    @SubscribeEvent
    static void onRegisterInteractions(RegisterCauldronInteractionEvent.Interaction event) {
        Identifier empty = Identifier.withDefaultNamespace("empty");
        Identifier water = Identifier.withDefaultNamespace("water");

        event.register(water, Items.GLASS_BOTTLE, PotionCauldronInteractions::fillBottleFromWater);
        event.register(water, ModItems.LEATHER_FLASK.get(), PotionCauldronInteractions::fillFlaskFromWater);

        // Wrap the existing empty-cauldron potion interaction (water bottle -> water cauldron).
        // Read from the item map: building an ItemStack here crashes (item components aren't bound yet).
        CauldronInteraction previous = ((com.terrainlab.mixin.CauldronDispatcherAccessor) (Object) CauldronInteractions.EMPTY)
                .terrainlab$items().getOrDefault(Items.POTION, (state, level, pos, player, hand, stack) -> InteractionResult.TRY_WITH_EMPTY_HAND);
        event.register(empty, Items.POTION, (state, level, pos, player, hand, stack) ->
                PotionMixing.canPour(stack)
                        ? pourPotion(state, level, pos, player, hand, stack)
                        : previous.interact(state, level, pos, player, hand, stack));

        POTION_CAULDRON.put(Items.POTION, (state, level, pos, player, hand, stack) -> {
            if (!PotionMixing.canPour(stack)) {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            }
            // Full: refuse the 4th potion outright (FAIL, so the player doesn't start drinking it instead).
            return state.getValue(PotionCauldronBlock.LEVEL) < PotionMixing.MAX_POTIONS
                    ? pourPotion(state, level, pos, player, hand, stack)
                    : InteractionResult.FAIL;
        });
        POTION_CAULDRON.put(Items.GLASS_BOTTLE, PotionCauldronInteractions::takeCustomPotion);
    }

    /** Vanilla's water-cauldron bottle fill, minus lowering the level. */
    private static InteractionResult fillBottleFromWater(BlockState state, Level level, BlockPos pos, Player player,
                                                         InteractionHand hand, ItemStack stack) {
        if (!level.isClientSide()) {
            Item used = stack.getItem();
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player,
                    PotionContents.createItemStack(Items.POTION, Potions.WATER)));
            player.awardStat(Stats.USE_CAULDRON);
            player.awardStat(Stats.ITEM_USED.get(used));
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** Refills the Leather Flask from a water cauldron without lowering the level. */
    private static InteractionResult fillFlaskFromWater(BlockState state, Level level, BlockPos pos, Player player,
                                                        InteractionHand hand, ItemStack stack) {
        if (!level.isClientSide()) {
            LeatherFlaskItem.refillOne(stack, player);
            player.awardStat(Stats.USE_CAULDRON);
            player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** Pours one potion into an empty cauldron or a potion cauldron with room (caller checked). */
    private static InteractionResult pourPotion(BlockState state, Level level, BlockPos pos, Player player,
                                                InteractionHand hand, ItemStack stack) {
        if (!level.isClientSide()) {
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents == null) {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            }
            if (state.is(ModBlocks.POTION_CAULDRON.get())) {
                if (!(level.getBlockEntity(pos) instanceof PotionCauldronBlockEntity cauldron) || !cauldron.addPotion(contents)) {
                    return InteractionResult.TRY_WITH_EMPTY_HAND;
                }
                level.setBlockAndUpdate(pos, state.setValue(PotionCauldronBlock.LEVEL, cauldron.getPotions().size()));
            } else {
                level.setBlockAndUpdate(pos, ModBlocks.POTION_CAULDRON.get().defaultBlockState());
                if (!(level.getBlockEntity(pos) instanceof PotionCauldronBlockEntity cauldron)) {
                    return InteractionResult.FAIL;
                }
                cauldron.addPotion(contents);
            }
            Item used = stack.getItem();
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
            player.awardStat(Stats.USE_CAULDRON);
            player.awardStat(Stats.ITEM_USED.get(used));
            level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** Bottles every poured potion as one Custom Potion and empties the cauldron. */
    private static InteractionResult takeCustomPotion(BlockState state, Level level, BlockPos pos, Player player,
                                                      InteractionHand hand, ItemStack stack) {
        if (!level.isClientSide()) {
            List<PotionContents> potions = level.getBlockEntity(pos) instanceof PotionCauldronBlockEntity cauldron
                    ? List.copyOf(cauldron.getPotions()) : List.of();
            Item used = stack.getItem();
            if (!potions.isEmpty()) {
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, PotionMixing.createCustomPotion(potions)));
                player.awardStat(Stats.USE_CAULDRON);
                player.awardStat(Stats.ITEM_USED.get(used));
            }
            BlockState newState = Blocks.CAULDRON.defaultBlockState();
            level.setBlockAndUpdate(pos, newState);
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
