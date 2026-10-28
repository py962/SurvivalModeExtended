package com.terrainlab.mixin;

import com.terrainlab.item.MapTargetCopying;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cartography table structure-target copying (see {@link MapTargetCopying}): the bottom slot also
 * accepts compasses, Rangefinder Compasses and filled maps, the result is computed for those before
 * vanilla's recipes, and the result slot keeps the source map when such a result is taken.
 */
@Mixin(CartographyTableMenu.class)
public abstract class CartographyTableMenuMixin extends AbstractContainerMenu {

    @Shadow @Final public Container container;
    @Shadow @Final private ResultContainer resultContainer;
    @Shadow @Final private ContainerLevelAccess access;

    protected CartographyTableMenuMixin(@Nullable MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V",
            at = @At("TAIL"))
    private void terrainlab$replaceSlots(int containerId, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        this.slots.set(1, new MapTargetCopying.RecipientSlot(this.slots.get(1), this.container));
        this.slots.set(2, new MapTargetCopying.ResultSlot(this.slots.get(2), this.resultContainer, this, access));
    }

    @Inject(method = "setupResultSlot", at = @At("HEAD"), cancellable = true)
    private void terrainlab$copyMapTarget(ItemStack mapStack, ItemStack additionalStack, ItemStack resultStack, CallbackInfo ci) {
        if (!MapTargetCopying.isRecipient(additionalStack)) {
            return;
        }
        // Server only, like vanilla's recipes (the client's access is NULL); the result syncs to the client.
        this.access.execute((level, pos) -> {
            ItemStack result = MapTargetCopying.craft(level, mapStack, additionalStack);
            if (result.isEmpty()) {
                this.resultContainer.removeItemNoUpdate(2);
            } else if (!ItemStack.matches(result, resultStack)) {
                this.resultContainer.setItem(2, result);
            }
            this.broadcastChanges();
        });
        ci.cancel();
    }
}
