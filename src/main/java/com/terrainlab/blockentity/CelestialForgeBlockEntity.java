package com.terrainlab.blockentity;

import com.terrainlab.block.CelestialForgeBlock;
import com.terrainlab.celestial.CelestialVariant;
import com.terrainlab.event.ModAuroraHandler;
import com.terrainlab.menu.CelestialForgeMenu;
import com.terrainlab.recipe.CelestialForgeRecipe;
import com.terrainlab.recipe.CelestialForgeRecipeInput;
import com.terrainlab.registry.ModBlockEntities;
import com.terrainlab.registry.ModSounds;
import com.terrainlab.registry.ModRecipeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Celestial Forge ritual. Under an aurora with open sky, a gem placed in the forge's single slot
 * starts the ritual: the gem floats above the forge, swirling light rays of the gem's colour rise
 * into the sky and a music-box tune plays. Components thrown into the light are drawn in; once
 * the recipe's count has arrived they are transformed into a pair of pearls, which are thrown out
 * as items and the light show ends. If the aurora fades, the sky is blocked or time runs out, the
 * gem goes back into the slot (where it waits until it is taken out or replaced) and any
 * components are thrown out.
 */
public class CelestialForgeBlockEntity extends BlockEntity implements Container, MenuProvider {

    public static final int SLOT_GEM = 0;

    public static final int RITUAL_TICKS = 1200;    // 60 s to throw in the components
    public static final int TRANSFORM_TICKS = 60;   // components spiral in and become pearls
    private static final double CATCH_RADIUS = 1.75; // blocks around the forge's top centre
    private static final double CATCH_HEIGHT = 3.0;  // blocks above the forge

    public enum Phase { IDLE, RITUAL, TRANSFORM }

    private ItemStack slot = ItemStack.EMPTY;
    private ItemStack gem = ItemStack.EMPTY;
    private final List<ItemStack> absorbed = new ArrayList<>();
    private final List<ItemStack> legacyDrops = new ArrayList<>();
    private Phase phase = Phase.IDLE;
    private @Nullable CelestialVariant variant;
    private int phaseTicks;
    private boolean ready;
    // Set when an aborted ritual returns its gem to the slot, so it does not restart on its own.
    private boolean holdGem;

    public CelestialForgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CELESTIAL_FORGE_BE.get(), pos, state);
    }

    // ---- readiness ----------------------------------------------------------

    public static boolean isReady(Level level, BlockPos pos) {
        return ModAuroraHandler.isAuroraActive(level.dimension()) && level.canSeeSky(pos.above());
    }

    /** Live readiness check used when opening the GUI; the client defers to the server. */
    public static boolean canOpen(Level level, BlockPos pos) {
        return level.isClientSide() || isReady(level, pos);
    }

    public Phase phase() {
        return phase;
    }

    public @Nullable CelestialVariant variant() {
        return variant;
    }

    public ItemStack gem() {
        return gem;
    }

    public List<ItemStack> absorbed() {
        return absorbed;
    }

    /** Ticks into the current phase. */
    public int phaseTicks() {
        return phaseTicks;
    }

    // ---- server tick --------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, CelestialForgeBlockEntity be) {
        ServerLevel serverLevel = (ServerLevel) level;

        if (!be.legacyDrops.isEmpty()) {
            for (ItemStack stack : be.legacyDrops) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack);
            }
            be.legacyDrops.clear();
            be.setChanged();
        }

        boolean wasReady = be.ready;
        be.ready = isReady(level, pos);
        if (wasReady != be.ready) {
            // ACTIVE drives the top-face model, which only sees the BlockState.
            level.setBlock(pos, state.setValue(CelestialForgeBlock.ACTIVE, be.ready), 3);
        }

        switch (be.phase) {
            case IDLE -> be.tickIdle(serverLevel, pos);
            case RITUAL -> be.tickRitual(serverLevel, pos);
            case TRANSFORM -> be.tickTransform(serverLevel, pos);
        }
    }

    private void tickIdle(ServerLevel level, BlockPos pos) {
        if (slot.isEmpty() || !ready || holdGem) {
            return;
        }
        Optional<RecipeHolder<CelestialForgeRecipe>> recipe = findRecipe(level, slot, ItemStack.EMPTY);
        if (recipe.isEmpty()) {
            return;
        }
        gem = slot.split(1);
        variant = recipe.get().value().variant();
        phase = Phase.RITUAL;
        phaseTicks = 0;
        closeMenus(level);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.0F);
        sync();
    }

    private void tickRitual(ServerLevel level, BlockPos pos) {
        phaseTicks++;
        if (!ready || phaseTicks >= RITUAL_TICKS) {
            abort(level, pos);
            return;
        }
        playMusic(level, pos, phaseTicks);

        Optional<RecipeHolder<CelestialForgeRecipe>> recipe = findRecipe(level, gem, ItemStack.EMPTY);
        if (recipe.isEmpty()) {
            abort(level, pos);
            return;
        }
        CelestialForgeRecipe forge = recipe.get().value();
        if (phaseTicks % 2 == 0 && catchThrownItems(level, pos, forge)) {
            sync();
        }
        if (absorbedCount() >= forge.thrownCount()) {
            phase = Phase.TRANSFORM;
            phaseTicks = 0;
            stopRitualMusic(level, pos);
            level.playSound(null, pos, SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.BLOCKS, 1.0F, 1.4F);
            // Synth swell that lands its low boom as the pearls appear (TRANSFORM_TICKS = 3 s).
            level.playSound(null, pos, ModSounds.CELESTIAL_FORGE_TRANSFORM.get(), SoundSource.RECORDS, 1.0F, 1.0F);
            sync();
        }
    }

    private void tickTransform(ServerLevel level, BlockPos pos) {
        phaseTicks++;
        if (phaseTicks < TRANSFORM_TICKS) {
            return;
        }

        ItemStack thrown = absorbed.isEmpty() ? ItemStack.EMPTY : absorbed.getFirst().copyWithCount(absorbedCount());
        Optional<RecipeHolder<CelestialForgeRecipe>> recipe = findRecipe(level, gem, thrown);
        if (recipe.isEmpty()) {
            abort(level, pos);
            return;
        }
        ItemStack pearls = recipe.get().value().assemble(new CelestialForgeRecipeInput(gem, thrown), level.registryAccess());

        Vec3 top = Vec3.atCenterOf(pos).add(0.0, 1.3, 0.0);
        RandomSource random = level.getRandom();
        for (int i = 0; i < pearls.getCount(); i++) {
            ItemEntity pearl = new ItemEntity(level, top.x, top.y, top.z, pearls.copyWithCount(1),
                    (random.nextDouble() - 0.5) * 0.15, 0.25, (random.nextDouble() - 0.5) * 0.15);
            pearl.setDefaultPickUpDelay();
            level.addFreshEntity(pearl);
        }
        int colour = rayColour(variant);
        level.sendParticles(new DustParticleOptions(colour, 1.5F), top.x, top.y, top.z, 60, 0.6, 0.6, 0.6, 0.05);
        level.sendParticles(ParticleTypes.END_ROD, top.x, top.y, top.z, 40, 0.3, 0.3, 0.3, 0.15);
        level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1.0F, 1.2F);

        gem = ItemStack.EMPTY;
        absorbed.clear();
        reset();
    }

    /** Draws matching thrown items in the catch zone into the forge, up to the recipe's count. */
    private boolean catchThrownItems(ServerLevel level, BlockPos pos, CelestialForgeRecipe recipe) {
        int needed = recipe.thrownCount() - absorbedCount();
        if (needed <= 0) {
            return false;
        }
        Vec3 centre = Vec3.atCenterOf(pos).add(0.0, 0.5, 0.0);
        AABB zone = new AABB(centre.x - CATCH_RADIUS, centre.y, centre.z - CATCH_RADIUS,
                centre.x + CATCH_RADIUS, centre.y + CATCH_HEIGHT, centre.z + CATCH_RADIUS);
        boolean caught = false;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, zone,
                e -> e.isAlive() && recipe.thrown().test(e.getItem()))) {
            ItemStack stack = item.getItem();
            int take = Math.min(needed, stack.getCount());
            ItemStack taken = stack.split(take);
            if (stack.isEmpty()) {
                item.discard();
            } else {
                item.setItem(stack);
            }
            absorbed.add(taken);
            needed -= take;
            caught = true;
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2F, 0.8F + absorbedCount() * 0.2F);
            if (needed <= 0) {
                break;
            }
        }
        return caught;
    }

    private int absorbedCount() {
        int count = 0;
        for (ItemStack stack : absorbed) {
            count += stack.getCount();
        }
        return count;
    }

    /**
     * Ends the light show. The gem goes back into the slot so it cannot be lost to item despawn
     * when nobody is nearby; the components are thrown out.
     */
    private void abort(ServerLevel level, BlockPos pos) {
        Vec3 top = Vec3.atCenterOf(pos).add(0.0, 1.0, 0.0);
        if (slot.isEmpty()) {
            slot = gem;
            holdGem = true;
        } else {
            Containers.dropItemStack(level, top.x, top.y, top.z, gem);
        }
        for (ItemStack stack : absorbed) {
            Containers.dropItemStack(level, top.x, top.y, top.z, stack);
        }
        gem = ItemStack.EMPTY;
        absorbed.clear();
        stopRitualMusic(level, pos);
        level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
        reset();
    }

    private void reset() {
        phase = Phase.IDLE;
        phaseTicks = 0;
        variant = null;
        sync();
    }

    private void closeMenus(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (player.containerMenu instanceof CelestialForgeMenu menu && menu.isFor(this)) {
                player.closeContainer();
            }
        }
    }

    private static Optional<RecipeHolder<CelestialForgeRecipe>> findRecipe(Level level, ItemStack core, ItemStack thrown) {
        if (core.isEmpty()) {
            return Optional.empty();
        }
        // recipeAccess() is declared as RecipeAccess, which lacks getRecipeFor.
        return ((RecipeManager) level.recipeAccess()).getRecipeFor(
                ModRecipeTypes.CELESTIAL_FORGE.get(), new CelestialForgeRecipeInput(core, thrown), level);
    }

    /** True for any item that is the gem of some forge recipe. */
    public static boolean isGem(Level level, ItemStack stack) {
        if (level.isClientSide() || !(level.recipeAccess() instanceof RecipeManager)) {
            return true; // the server decides; the client cannot see the recipes
        }
        return findRecipe(level, stack, ItemStack.EMPTY).isPresent();
    }

    // ---- music ----------------------------------------------------------------

    // Synth loop (sounds/block/celestial_forge/ritual.ogg), exactly RITUAL_LOOP_TICKS long; replayed
    // on that cadence for as long as the ritual lasts and stopped when it ends.
    private static final int RITUAL_LOOP_TICKS = 400;
    private static final double MUSIC_STOP_RANGE = 48.0;

    private static void playMusic(ServerLevel level, BlockPos pos, int ticks) {
        if ((ticks - 1) % RITUAL_LOOP_TICKS == 0) {
            level.playSound(null, pos, ModSounds.CELESTIAL_FORGE_RITUAL.get(), SoundSource.RECORDS, 1.0F, 1.0F);
        }
    }

    /** Cuts the ritual loop for nearby players, so it does not play on after the ritual ends. */
    private static void stopRitualMusic(ServerLevel level, BlockPos pos) {
        ClientboundStopSoundPacket stop = new ClientboundStopSoundPacket(
                ModSounds.CELESTIAL_FORGE_RITUAL.getId(), SoundSource.RECORDS);
        Vec3 centre = Vec3.atCenterOf(pos);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(centre) <= MUSIC_STOP_RANGE * MUSIC_STOP_RANGE) {
                player.connection.send(stop);
            }
        }
    }

    // ---- client tick: particles and the aurora tint ------------------------

    public static void clientTick(Level level, BlockPos pos, BlockState state, CelestialForgeBlockEntity be) {
        if (be.phase == Phase.IDLE) {
            return;
        }
        be.phaseTicks++;
        RandomSource random = level.getRandom();
        int colour = rayColour(be.variant);
        Vec3 top = Vec3.atCenterOf(pos).add(0.0, 1.2, 0.0);

        // Coloured motes spiralling upward around the beam.
        int motes = be.phase == Phase.TRANSFORM ? 6 : 2;
        for (int i = 0; i < motes; i++) {
            double angle = (be.phaseTicks * 0.25) + random.nextDouble() * Math.PI * 2.0;
            double radius = 0.4 + random.nextDouble() * 0.6;
            level.addParticle(new DustParticleOptions(colour, 1.0F),
                    top.x + Math.cos(angle) * radius, top.y + random.nextDouble() * 1.5, top.z + Math.sin(angle) * radius,
                    0.0, 0.05, 0.0);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.END_ROD, top.x + (random.nextDouble() - 0.5),
                    top.y + random.nextDouble(), top.z + (random.nextDouble() - 0.5), 0.0, 0.08, 0.0);
        }

        com.terrainlab.client.ClientAuroraState.tintFrom(pos, auroraColour(be.variant));
    }

    /** Aurora colour during a ritual: the same as the rays (black for Death). */
    public static int auroraColour(@Nullable CelestialVariant variant) {
        return rayColour(variant);
    }

    /** Light colour for a variant, matching its gem. */
    public static int rayColour(@Nullable CelestialVariant variant) {
        if (variant == null) {
            return 0xFFFFFF;
        }
        return switch (variant) {
            case BREATH -> 0x3FB8D6;  // heart of the sea
            case LIFE -> 0x4AEDD9;    // diamond
            case DEATH -> 0x000000;   // black rays, drawn by a normally blended pass
            case SHIELD -> 0xF2EEE3;  // nether quartz
            case PEACE -> 0x3B6BE0;   // lapis
            case EYE -> 0xB46CE8;     // amethyst
            case LUCK -> 0x2BE070;    // emerald
            case END -> 0xE58CF0;     // end crystal
        };
    }

    // ---- sync and persistence ---------------------------------------------------

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
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

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        slot = input.read("Item0", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        gem = input.read("Gem", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        absorbed.clear();
        absorbed.addAll(input.read("Absorbed", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()));
        phase = input.read("Phase", com.mojang.serialization.Codec.STRING).map(CelestialForgeBlockEntity::parsePhase).orElse(Phase.IDLE);
        variant = input.read("Variant", CelestialVariant.CODEC).orElse(null);
        phaseTicks = input.getIntOr("PhaseTicks", 0);
        holdGem = input.getBooleanOr("HoldGem", false);
        // Forges from before the ritual rework kept a ring of 8 ingredients and an output slot.
        for (int i = 1; i <= 9; i++) {
            input.read("Item" + i, ItemStack.OPTIONAL_CODEC).filter(s -> !s.isEmpty()).ifPresent(legacyDrops::add);
        }
    }

    private static Phase parsePhase(String name) {
        try {
            return Phase.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Phase.IDLE;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!slot.isEmpty()) {
            output.store("Item0", ItemStack.OPTIONAL_CODEC, slot);
        }
        if (!gem.isEmpty()) {
            output.store("Gem", ItemStack.OPTIONAL_CODEC, gem);
        }
        if (!absorbed.isEmpty()) {
            output.store("Absorbed", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(absorbed));
        }
        output.putString("Phase", phase.name());
        if (variant != null) {
            output.store("Variant", CelestialVariant.CODEC, variant);
        }
        output.putInt("PhaseTicks", phaseTicks);
        if (holdGem) {
            output.putBoolean("HoldGem", true);
        }
    }

    /** Gem, components and slot contents drop when the forge is broken. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, gem);
            for (ItemStack stack : absorbed) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
            for (ItemStack stack : legacyDrops) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
        }
        gem = ItemStack.EMPTY;
        absorbed.clear();
        legacyDrops.clear();
        super.preRemoveSideEffects(pos, state); // drops the slot contents
    }

    // ---- menu and container (the single gem slot) ---------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.terrainlab.celestial_forge");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CelestialForgeMenu(id, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return phase == Phase.IDLE && level != null && isGem(level, stack);
    }

    @Override
    public boolean isEmpty() {
        return slot.isEmpty();
    }

    @Override
    public ItemStack getItem(int index) {
        return slot;
    }

    @Override
    public ItemStack removeItem(int index, int amount) {
        ItemStack split = slot.split(amount);
        holdGem = false;
        setChanged();
        return split;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack stack = slot;
        slot = ItemStack.EMPTY;
        holdGem = false;
        return stack;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        slot = stack;
        holdGem = false;
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        slot = ItemStack.EMPTY;
        setChanged();
    }
}
