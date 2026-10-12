package com.terrainlab.celestial;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The eight Celestial Forge families. Each defines its conduit frame block (counted against
 * the vanilla conduit frame shape, see {@link CelestialFrame}) and a render tint.
 *
 * <p>BREATH is special: its conduit is vanilla's {@code minecraft:conduit}, so vanilla decides
 * activation and its {@link #frameBlock()} is only the ring material in the conduit craft
 * recipe. See {@link BreathConduitHooks}.
 */
public enum CelestialVariant {
    // Third argument: render tint (0xRRGGBB), roughly the colour of the variant's Forge core item.
    BREATH("breath", () -> Blocks.PRISMARINE, 0xBAAD96),
    LIFE("life", () -> Blocks.MOSS_BLOCK, 0x4AEDD9),
    DEATH("death", () -> Blocks.CRYING_OBSIDIAN, 0x000000),
    // Shield's frame also accepts every other copper block (see isFrameBlock); this is the one shown.
    SHIELD("shield", () -> Blocks.COPPER_BLOCK, 0xF2EEE3),
    PEACE("peace", () -> Blocks.BONE_BLOCK, 0x385B96),
    EYE("eye", () -> Blocks.POLISHED_DEEPSLATE, 0x8C4797),
    LUCK("luck", () -> Blocks.GILDED_BLACKSTONE, 0x17A44C),
    // Supplier: the mod's block is not registered yet when this enum initialises.
    END("end", () -> com.terrainlab.registry.ModBlocks.VOID_GLASS.get(), 0xD7E896);

    public static final Codec<CelestialVariant> CODEC =
            Codec.STRING.xmap(CelestialVariant::byId, CelestialVariant::id);

    public static final StreamCodec<? super RegistryFriendlyByteBuf, CelestialVariant> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(CelestialVariant::byId, CelestialVariant::id);

    private final String id;
    private final java.util.function.Supplier<Block> frameBlock;
    private final int tintColor;

    CelestialVariant(String id, java.util.function.Supplier<Block> frameBlock, int tintColor) {
        this.id = id;
        this.frameBlock = frameBlock;
        this.tintColor = tintColor;
    }

    public String id() {
        return id;
    }

    /** The frame material that must ring a placed conduit of this variant. */
    public Block frameBlock() {
        return frameBlock.get();
    }

    /**
     * Whether a block counts towards this variant's conduit frame. Shield takes any copper block,
     * waxed or not, at any oxidation stage, so a frame keeps working as it ages.
     */
    public boolean isFrameBlock(BlockState state) {
        if (this == SHIELD) {
            return state.is(Blocks.COPPER_BLOCK) || state.is(Blocks.EXPOSED_COPPER)
                    || state.is(Blocks.WEATHERED_COPPER) || state.is(Blocks.OXIDIZED_COPPER)
                    || state.is(Blocks.WAXED_COPPER_BLOCK) || state.is(Blocks.WAXED_EXPOSED_COPPER)
                    || state.is(Blocks.WAXED_WEATHERED_COPPER) || state.is(Blocks.WAXED_OXIDIZED_COPPER);
        }
        return state.is(frameBlock());
    }

    /** Packed 0xRRGGBB render tint, no alpha. */
    public int tintColor() {
        return tintColor;
    }

    /** True only for BREATH, whose conduit is vanilla's. Registry loops over custom conduit blocks/items skip it. */
    public boolean usesVanillaConduit() {
        return this == BREATH;
    }

    public static CelestialVariant byId(String id) {
        for (CelestialVariant v : values()) {
            if (v.id.equals(id)) return v;
        }
        throw new IllegalArgumentException("Unknown CelestialVariant id: " + id);
    }
}
