package com.terrainlab.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.terrainlab.TerrainLab;
import com.terrainlab.data.SeasonSavedData;
import com.terrainlab.network.SeasonOffsetPacket;
import com.terrainlab.season.Season;
import com.terrainlab.season.SeasonBridge;
import com.terrainlab.season.SeasonManager;
import java.util.Locale;
import java.util.stream.IntStream;
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
 * /terrainlab season length               shows the year length
 * /terrainlab season length set <days>    sets the year length: 60, 120, 180, 240, 300 or 360 days
 * <p>
 * Seasons run on total game time plus the world's seasonal offset, which /time never changes, so this
 * moves the offset instead and re-sends it to every player. The time of day is left alone. Changing the
 * year length rescales the offset so the world stays at the same season and the same fraction of it.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class SeasonCommand {

    /** Largest possible day of a season (the longest year); the limit of the current year is checked on use. */
    private static final int MAX_DAY_ARGUMENT = Season.MAX_YEAR_LENGTH_DAYS / Season.STATE_COUNT;

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
                                        .then(Commands.argument("day", IntegerArgumentType.integer(1, MAX_DAY_ARGUMENT))
                                                .executes(ctx -> set(ctx, IntegerArgumentType.getInteger(ctx, "day"))))))
                        .then(Commands.literal("length")
                                .executes(SeasonCommand::showLength)
                                .then(Commands.literal("set")
                                        .then(Commands.argument("days", IntegerArgumentType.integer(Season.MIN_YEAR_LENGTH_DAYS, Season.MAX_YEAR_LENGTH_DAYS))
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                        IntStream.rangeClosed(1, Season.MAX_YEAR_LENGTH_DAYS / Season.YEAR_LENGTH_STEP_DAYS)
                                                                .mapToObj(i -> String.valueOf(i * Season.YEAR_LENGTH_STEP_DAYS)), builder))
                                                .executes(ctx -> setLength(ctx, IntegerArgumentType.getInteger(ctx, "days"))))))));
    }

    private static int daysPerSeason() {
        return (int) (Season.stateLengthTicks() / Season.TICKS_PER_DAY);
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        long offset = SeasonSavedData.get(level).getSeasonalOffset();
        Season season = SeasonManager.getCurrentSeason(level.getGameTime(), offset);
        int day = SeasonManager.getDayInState(level.getGameTime(), offset) + 1;
        ctx.getSource().sendSuccess(() -> Component.literal(describe(season, day)), false);
        return 1;
    }

    private static int showLength(CommandContext<CommandSourceStack> ctx) {
        int days = SeasonSavedData.get(ctx.getSource().getServer().overworld()).getYearLengthDays();
        ctx.getSource().sendSuccess(() -> Component.literal(describeLength(days)), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, int day) {
        String name = StringArgumentType.getString(ctx, "season");
        Season season = byId(name);
        if (season == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown season: " + name));
            return 0;
        }
        if (day > daysPerSeason()) {
            ctx.getSource().sendFailure(Component.literal("Each season has " + daysPerSeason() + " days in this world"));
            return 0;
        }
        ServerLevel level = ctx.getSource().getServer().overworld();
        SeasonSavedData data = SeasonSavedData.get(level);
        long target = season.ordinal() * Season.stateLengthTicks() + (day - 1) * Season.TICKS_PER_DAY;
        data.setSeasonalOffset(Math.floorMod(target - level.getGameTime(), Season.yearLengthTicks()));
        PacketDistributor.sendToAllPlayers(new SeasonOffsetPacket(data.getSeasonalOffset(), data.getYearLengthDays()));
        ctx.getSource().sendSuccess(() -> Component.literal("Season set to " + describe(season, day)), true);
        return 1;
    }

    private static int setLength(CommandContext<CommandSourceStack> ctx, int days) {
        if (!Season.isValidYearLength(days)) {
            ctx.getSource().sendFailure(Component.literal("The year length must be 60, 120, 180, 240, 300 or 360 days"));
            return 0;
        }
        ServerLevel level = ctx.getSource().getServer().overworld();
        SeasonSavedData data = SeasonSavedData.get(level);
        if (days == data.getYearLengthDays()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Already " + describeLength(days)), false);
            return 1;
        }
        // Make sure the clock still reads the stored length, then keep the same point in the year: states are
        // all one length, so the same fraction of the year is the same season and the same fraction of it.
        Season.setYearLengthDays(data.getYearLengthDays());
        long gameTime = level.getGameTime();
        long oldYear = Season.yearLengthTicks();
        long oldYearTick = SeasonManager.yearRelativeTick(gameTime, data.getSeasonalOffset());

        Season.setYearLengthDays(days);
        data.setYearLengthDays(days);
        long newYear = Season.yearLengthTicks();
        long newYearTick = oldYearTick * newYear / oldYear;
        data.setSeasonalOffset(Math.floorMod(newYearTick - gameTime, newYear));
        SeasonBridge.invalidateServerOffset();
        PacketDistributor.sendToAllPlayers(new SeasonOffsetPacket(data.getSeasonalOffset(), days));

        Season season = SeasonManager.getCurrentSeason(gameTime, data.getSeasonalOffset());
        int day = SeasonManager.getDayInState(gameTime, data.getSeasonalOffset()) + 1;
        ctx.getSource().sendSuccess(() -> Component.literal("Year length set: " + describeLength(days) + ". Now " + describe(season, day)), true);
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

    private static String describeLength(int days) {
        return days + " days (" + days / Season.STATE_COUNT + " days per season)";
    }

    private static String describe(Season season, int day) {
        String[] words = id(season).split("_");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (name.length() > 0) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name + ", day " + day + " of " + daysPerSeason();
    }
}
