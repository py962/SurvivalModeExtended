package com.terrainlab.registry;

import com.terrainlab.TerrainLab;
import com.terrainlab.worldgen.BuriedHideoutPieces;
import com.terrainlab.worldgen.BuriedHideoutStructure;
import com.terrainlab.worldgen.RelaxedEndCityStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Structure type and structure piece type registrations. */
public class ModStructures {

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, TerrainLab.MODID);

    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, TerrainLab.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<RelaxedEndCityStructure>> RELAXED_END_CITY =
            STRUCTURE_TYPES.register("relaxed_end_city",
                    () -> () -> RelaxedEndCityStructure.CODEC);

    // Outlaws Hideout buried in a badlands hill, with its front and rail escape tunnels.
    public static final DeferredHolder<StructureType<?>, StructureType<BuriedHideoutStructure>> BURIED_HIDEOUT =
            STRUCTURE_TYPES.register("buried_hideout",
                    () -> () -> BuriedHideoutStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> HIDEOUT_CHAMBER =
            STRUCTURE_PIECE_TYPES.register("hideout_chamber",
                    () -> (StructurePieceType.StructureTemplateType) BuriedHideoutPieces.ChamberPiece::new);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> HIDEOUT_COVER =
            STRUCTURE_PIECE_TYPES.register("hideout_cover",
                    () -> (StructurePieceType.ContextlessType) BuriedHideoutPieces.CoverPiece::new);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> HIDEOUT_TUNNEL =
            STRUCTURE_PIECE_TYPES.register("hideout_tunnel",
                    () -> (StructurePieceType.ContextlessType) BuriedHideoutPieces.TunnelPiece::new);

    public static void register(IEventBus eventBus) {
        STRUCTURE_TYPES.register(eventBus);
        STRUCTURE_PIECE_TYPES.register(eventBus);
    }
}
