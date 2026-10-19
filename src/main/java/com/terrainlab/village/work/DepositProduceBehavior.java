package com.terrainlab.village.work;

import com.terrainlab.village.VillageChestService;
import com.terrainlab.village.VillagerWorkConstants;
import com.terrainlab.village.VillagerWorkState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Walks to the village food chest beside the bell, opens it, moves the profession's produce in
 * (converted per {@link ProducePlan}) and closes it. What does not fit stays in the villager's
 * inventory. Requires the chest to exist.
 */
public class DepositProduceBehavior extends AbstractVillagerWorkBehavior {

    /** What a profession delivers and when. */
    public interface ProducePlan {
        /** Should a delivery trip start now? */
        boolean wantsTrip(Villager body, VillagerWorkState state, long now);

        /** Removes the produce from the villager's inventory and returns it, already converted. */
        List<ItemStack> extract(ServerLevel level, Villager body);
    }

    private enum Phase { WALK, OPEN, CLOSE }

    private final ProducePlan produce;
    private Phase phase = Phase.WALK;
    private @Nullable BlockPos chestPos;
    private @Nullable BlockPos bellPos;
    private long phaseUntil;
    private boolean lidOpen;

    public DepositProduceBehavior(ResourceKey<VillagerProfession> profession, Activity activity, ProducePlan produce) {
        super(profession, activity);
        this.produce = produce;
    }

    @Override
    protected boolean plan(ServerLevel level, Villager body, BlockPos jobSite, long now) {
        if (!produce.wantsTrip(body, VillagerWork.state(body), now)) {
            return false;
        }
        Optional<BlockPos> bell = VillagerWork.bell(body, level, jobSite);
        if (bell.isEmpty()) {
            return false;
        }
        Optional<BlockPos> chest = VillageChestService.findChest(level, bell.get());
        if (chest.isEmpty()) {
            return false;
        }
        bellPos = bell.get();
        chestPos = chest.get();
        phase = Phase.WALK;
        beginLeg(now);
        return true;
    }

    @Override
    protected void work(ServerLevel level, Villager body, long now) {
        if (chestPos == null || bellPos == null) {
            fail(body, now);
            return;
        }
        Vec3 chestCentre = Vec3.atCenterOf(chestPos);
        switch (phase) {
            case WALK -> {
                if (VillagerWork.withinReach(body, chestCentre)) {
                    VillagerWork.setChestOpen(level, chestPos, true);
                    lidOpen = true;
                    phase = Phase.OPEN;
                    phaseUntil = now + VillagerWorkConstants.CHEST_OPEN_TICKS;
                } else if (legTimedOut(now)) {
                    fail(body, now);
                } else {
                    walkTo(body, chestPos, 1);
                }
            }
            case OPEN -> {
                stayAndLook(body, chestCentre);
                if (now >= phaseUntil) {
                    transfer(level, body);
                    VillagerWork.setState(body, VillagerWork.state(body).withLastDeposit(now));
                    phase = Phase.CLOSE;
                    phaseUntil = now + VillagerWorkConstants.CHEST_OPEN_TICKS;
                }
            }
            case CLOSE -> {
                stayAndLook(body, chestCentre);
                if (now >= phaseUntil) {
                    closeLid(level);
                    finish();
                }
            }
        }
    }

    private void transfer(ServerLevel level, Villager body) {
        for (ItemStack stack : produce.extract(level, body)) {
            ItemStack rest = VillageChestService.deposit(level, bellPos, stack);
            VillagerWork.giveOrDrop(body, level, rest);
        }
    }

    private void closeLid(ServerLevel level) {
        if (lidOpen && chestPos != null) {
            VillagerWork.setChestOpen(level, chestPos, false);
        }
        lidOpen = false;
    }

    @Override
    protected void cleanup(ServerLevel level, Villager body, long now) {
        closeLid(level);
        phase = Phase.WALK;
    }
}
