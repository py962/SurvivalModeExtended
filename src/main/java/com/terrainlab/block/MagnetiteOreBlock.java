package com.terrainlab.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.util.valueproviders.UniformInt;
import org.joml.Vector3f;

/**
 * Magnetite ore: a copy of vanilla's redstone ore behaviour (lights up when touched, fades on
 * random tick) with orange particles. Emits no redstone signal. Drops come from the loot table;
 * XP matches redstone ore.
 */
public class MagnetiteOreBlock extends Block {
    public static final BooleanProperty LIT = RedstoneTorchBlock.LIT;

    // 0xFF8000 orange.
    private static final DustParticleOptions MAGNETITE_DUST_PARTICLE =
            new DustParticleOptions(16744448, 1.0F);

    public MagnetiteOreBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(LIT, false));
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        interact(state, level, pos);
        super.attack(state, level, pos, player);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!entity.isSteppingCarefully()) {
            interact(state, level, pos);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected InteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult
    ) {
        if (level.isClientSide()) {
            spawnParticles(level, pos);
        } else {
            interact(state, level, pos);
        }

        return (InteractionResult) (stack.getItem() instanceof BlockItem
                && new BlockPlaceContext(player, hand, stack, hitResult).canPlace()
                ? InteractionResult.PASS
                : InteractionResult.SUCCESS);
    }

    private static void interact(BlockState state, Level level, BlockPos pos) {
        spawnParticles(level, pos);
        if (!state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, true), 3);
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(LIT);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, false), 3);
        }
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool, boolean dropExp) {
        super.spawnAfterBreak(state, level, pos, tool, dropExp);
    }

    @Override
    public int getExpDrop(BlockState state, net.minecraft.world.level.LevelAccessor level, BlockPos pos,
                          net.minecraft.world.level.block.entity.@org.jspecify.annotations.Nullable BlockEntity blockEntity,
                          net.minecraft.world.entity.@org.jspecify.annotations.Nullable Entity breaker, ItemStack tool) {
        return UniformInt.of(1, 5).sample(level.getRandom());
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            spawnParticles(level, pos);
        }
    }

    private static void spawnParticles(Level level, BlockPos pos) {
        double d0 = 0.5625;
        RandomSource random = level.getRandom();

        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.getBlockState(neighbor).isSolidRender()) {
                Direction.Axis axis = direction.getAxis();
                double dx = axis == Direction.Axis.X ? 0.5 + d0 * direction.getStepX() : random.nextFloat();
                double dy = axis == Direction.Axis.Y ? 0.5 + d0 * direction.getStepY() : random.nextFloat();
                double dz = axis == Direction.Axis.Z ? 0.5 + d0 * direction.getStepZ() : random.nextFloat();
                level.addParticle(MAGNETITE_DUST_PARTICLE, pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz, 0.0, 0.0, 0.0);
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
}
