package com.terrainlab.command;

import com.mojang.brigadier.context.CommandContext;
import com.terrainlab.TerrainLab;
import com.terrainlab.entity.AbstractTrailTraderEntity;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Operator debug command {@code /terrainlab guides}: for each guide type, whether one is on the
 * world's bonded record, every loaded guide of that type (position, biome, distance), and whether
 * the biome you are standing in can spawn it. Unloaded guides are not listed, only the record.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class GuideDebugCommand {

    private GuideDebugCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("terrainlab")
                .then(Commands.literal("guides")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(GuideDebugCommand::run)));
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel here = source.getLevel();
        Vec3 origin = source.getPosition();
        BlockPos originPos = BlockPos.containing(origin);
        Map<EntityType<?>, UUID> bonded = here.getServer().overworld()
                .getData(ModAttachments.LIVING_TRAIL_TRADERS.get());

        int loadedTotal = 0;
        for (EntityType<?> type : AbstractTrailTraderEntity.guideTypes()) {
            String name = type.toShortString();
            UUID record = bonded.get(type);
            boolean canSpawnHere = AbstractTrailTraderEntity.biomeListsType(here, originPos, type);
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "%s: bonded record %s | can spawn in this biome: %s",
                    name, record == null ? "none" : record.toString().substring(0, 8), canSpawnHere ? "yes" : "no")), false);

            for (ServerLevel level : here.getServer().getAllLevels()) {
                for (AbstractTrailTraderEntity guide : level.getEntities(
                        EntityTypeTest.forClass(AbstractTrailTraderEntity.class), g -> g.getType() == type)) {
                    loadedTotal++;
                    BlockPos pos = guide.blockPosition();
                    String biome = level.getBiome(pos).unwrapKey()
                            .map(key -> key.identifier().toString()).orElse("?");
                    String distance = level == here
                            ? String.format(Locale.ROOT, "%.0f blocks away", Math.sqrt(guide.distanceToSqr(origin)))
                            : "other dimension";
                    String line = String.format(Locale.ROOT, "  - %s at %s in %s (%s), uuid %s",
                            level.dimension().identifier().getPath(), pos.toShortString(), biome, distance,
                            guide.getUUID().toString().substring(0, 8));
                    source.sendSuccess(() -> Component.literal(line), false);
                }
            }
        }
        int total = loadedTotal;
        source.sendSuccess(() -> Component.literal(total + " guide(s) loaded."), false);
        return total;
    }
}
