package com.terrainlab.item;

import com.terrainlab.entity.HouseboatEntity;
import com.terrainlab.registry.ModEntities;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Places a {@link HouseboatEntity} where the player is looking, including on water. */
public class HouseboatItem extends Item {

    public HouseboatItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player,
                                 InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player,
                ClipContext.Fluid.ANY);

        if (hit.getType() == HitResult.Type.MISS) {
            return InteractionResult.PASS;
        }

        Vec3 hitVec = hit.getLocation();

        if (!level.isClientSide()) {
            HouseboatEntity houseboat = new HouseboatEntity(
                    ModEntities.HOUSEBOAT.get(), level,
                    () -> this);
            houseboat.setInitialPos(hitVec.x, hitVec.y, hitVec.z);
            houseboat.setYRot(player.getYRot());
            level.addFreshEntity(houseboat);
        }

        if (!player.getAbilities().instabuild) {
            itemstack.shrink(1);
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.SUCCESS;
    }
}
