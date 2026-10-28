package com.terrainlab.blockentity;

import com.mojang.serialization.Codec;
import com.terrainlab.item.PotionMixing;
import com.terrainlab.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Potion cauldron contents: the poured potions (up to {@link PotionMixing#MAX_POTIONS}) and their
 * blended colour, synced to clients for the liquid tint.
 */
public class PotionCauldronBlockEntity extends BlockEntity {

    private static final Codec<List<PotionContents>> POTIONS_CODEC = PotionContents.CODEC.listOf();

    private final List<PotionContents> potions = new ArrayList<>();
    /** Read by the tint source on chunk-meshing threads. */
    private volatile int color = PotionMixing.blendColor(List.of());

    public PotionCauldronBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POTION_CAULDRON_BE.get(), pos, state);
    }

    public List<PotionContents> getPotions() {
        return Collections.unmodifiableList(potions);
    }

    public int getColor() {
        return color;
    }

    public boolean addPotion(PotionContents potion) {
        if (potions.size() >= PotionMixing.MAX_POTIONS) {
            return false;
        }
        potions.add(potion);
        color = PotionMixing.blendColor(potions);
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return true;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        potions.clear();
        input.read("Potions", POTIONS_CODEC).ifPresent(list -> {
            for (PotionContents p : list) {
                if (potions.size() < PotionMixing.MAX_POTIONS) {
                    potions.add(p);
                }
            }
        });
        color = PotionMixing.blendColor(potions);
        // New colour from the server: re-mesh so the tint updates.
        if (level != null && level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!potions.isEmpty()) {
            output.store("Potions", POTIONS_CODEC, List.copyOf(potions));
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }
}
