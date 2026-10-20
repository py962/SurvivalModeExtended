package com.terrainlab.mixin;

import com.mojang.serialization.Codec;
import com.terrainlab.food.FlowerStews;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Brown mooshroom stews match the mod's crafted flower stews (FlowerStews). Vanilla only remembers the
 * fed flower's effects; this also remembers which flower (saved with the cow), and when milked with a
 * bowl the suspicious stew gets the crafted result's components: effects, "Stew of X" item_name and the
 * stew_flower id (which ModFoodEffectsHandler needs for Blue Orchid energy / Oxeye Daisy instant heal).
 * A cow fed before this existed (no stored flower) gives vanilla's stew, named if its effects match one
 * crafted stew. Torchflower is refused (its crafted stew is disabled): not consumed, nothing stored.
 */
@Mixin(MushroomCow.class)
public abstract class MushroomCowStewMixin {

    @Unique
    private static final String TERRAINLAB$TAG_STEW_FLOWER = "terrainlab_stew_flower";

    @Shadow
    private @Nullable SuspiciousStewEffects stewEffects;

    @Unique
    private @Nullable String terrainlab$stewFlower;

    // Only called from mobInteract's brown-mooshroom feeding branch; a present result with no stored
    // effects means vanilla is about to consume the item and store these effects.
    @Inject(method = "getEffectsFromItemStack", at = @At("RETURN"), cancellable = true, require = 1)
    private void terrainlab$feedFlower(ItemStack stack, CallbackInfoReturnable<Optional<SuspiciousStewEffects>> cir) {
        if (FlowerStews.isRefusedByMooshroom(stack)) {
            cir.setReturnValue(Optional.empty());
            return;
        }
        if (cir.getReturnValue().isPresent() && this.stewEffects == null) {
            FlowerStews.Stew stew = FlowerStews.forItem(stack.getItem());
            this.terrainlab$stewFlower = stew != null ? stew.flower() : null;
        }
    }

    @ModifyArg(method = "mobInteract",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemUtils;createFilledResult(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"),
            index = 2, require = 1)
    private ItemStack terrainlab$craftedStew(ItemStack stew) {
        if (stew.is(Items.SUSPICIOUS_STEW)) {
            FlowerStews.Stew crafted = FlowerStews.forFlower(this.terrainlab$stewFlower);
            if (crafted != null) {
                crafted.applyTo(stew);
            } else {
                SuspiciousStewEffects effects = stew.get(DataComponents.SUSPICIOUS_STEW_EFFECTS);
                String nameKey = effects != null ? FlowerStews.nameKeyForEffects(effects) : null;
                if (nameKey != null) {
                    stew.set(DataComponents.ITEM_NAME, Component.translatable(nameKey));
                }
            }
        }
        this.terrainlab$stewFlower = null;
        return stew;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"), require = 1)
    private void terrainlab$saveStewFlower(ValueOutput output, CallbackInfo ci) {
        output.storeNullable(TERRAINLAB$TAG_STEW_FLOWER, Codec.STRING, this.terrainlab$stewFlower);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"), require = 1)
    private void terrainlab$loadStewFlower(ValueInput input, CallbackInfo ci) {
        this.terrainlab$stewFlower = input.read(TERRAINLAB$TAG_STEW_FLOWER, Codec.STRING).orElse(null);
    }
}
