package com.terrainlab.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.terrainlab.TerrainLab;
import com.terrainlab.data.SeasonSavedData;
import com.terrainlab.network.SeasonOffsetPacket;
import com.terrainlab.season.Season;
import com.terrainlab.season.SeasonManager;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * /terrainlab season                      shows the season and day (e.g. "Early Autumn, day 4 of 10")
 * /terrainlab season set <season> [day]   jumps to the start of that day of that season (day 1 if omitted)
 * <p>
 * Seasons run on total game time plus the world's seasonal offset, which /time never changes, so this
 * moves the offset instead and re-sends it to every player. The time of day is left alone.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class SeasonCommand {

    private static final int DAYS_PER_SEASON = (int) (Season.STATE_LENGTH_TICKS / 24_000L);

    private SeasonCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("terrainlab")
                .then(Commands.literal("season")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(SeasonCommand::show)
                        .then(Commands.literal("set")
                                .then(Commands.argument("season", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                java.util.Arrays.stream(Season.values()).map(SeasonCommand::id), builder))
                                        .executes(ctx -> set(ctx, 1))
                                        .then(Commands.argument("day", IntegerArgumentType.integer(1, DAYS_PER_SEASON))
                                                .executes(ctx -> set(ctx, IntegerArgumentType.getInteger(ctx, "day"))))))));
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        long offset = SeasonSavedData.get(level).getSeasonalOffset();
        Season season = SeasonManager.getCurrentSeason(level.getGameTime(), offset);
        int day = SeasonManager.getDayInState(level.getGameTime(), offset) + 1;
        ctx.getSource().sendSuccess(() -> Component.literal(describe(season, day)), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, int day) {
        String name = StringArgumentType.getString(ctx, "season");
        Season season = byId(name);
        if (season == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown season: " + name));
            return 0;
        }
        ServerLevel level = ctx.getSource().getServer().overworld();
        SeasonSavedData data = SeasonSavedData.get(level);
        long target = season.ordinal() * Season.STATE_LENGTH_TICKS + (day - 1) * 24_000L;
        data.setSeasonalOffset(Math.floorMod(target - level.getGameTime(), Season.YEAR_LENGTH_TICKS));
        PacketDistributor.sendToAllPlayers(new SeasonOffsetPacket(data.getSeasonalOffset()));
        ctx.getSource().sendSuccess(() -> Component.literal("Season set to " + describe(season, day)), true);
        return 1;
    }

    private static String id(Season season) {
        return season.name().toLowerCase(Locale.ROOT);
    }

    private static Season byId(String id) {
        for (Season season : Season.values()) {
            if (id(season).equals(id.toLowerCase(Locale.ROOT))) {
                return season;
            }
        }
        return null;
    }

    private static String describe(Season season, int day) {
        String[] words = id(season).split("_");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (name.length() > 0) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name + ", day " + day + " of " + DAYS_PER_SEASON;
    }
}
