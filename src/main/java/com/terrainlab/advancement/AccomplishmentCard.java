package com.terrainlab.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the client needs to draw one accomplishment card, captured on the server at the
 * moment the player returned from the End. Persisted on the player
 * (ModAttachments.ACCOMPLISHMENT_CARD) so /accomplishments can show the latest card again.
 *
 * @param title        accomplishment id shown as the big title (the highest one newly earned)
 * @param earned       every accomplishment id this player has in this world, highest first
 * @param fresh        the ids newly earned on this End return
 * @param distanceCm   sum of all vanilla *_one_cm movement stats
 * @param dateEpochDay server-local date of the return, as LocalDate.toEpochDay()
 */
public record AccomplishmentCard(
        String title,
        List<String> earned,
        List<String> fresh,
        String playerName,
        String worldName,
        long seed,
        boolean hardcore,
        int difficulty,
        boolean cheats,
        long days,
        long playTicks,
        int deaths,
        int mobKills,
        long distanceCm,
        long dateEpochDay
) {

    public static final AccomplishmentCard EMPTY = new AccomplishmentCard(
            "", List.of(), List.of(), "", "", 0L, false, 0, false, 0L, 0L, 0, 0, 0L, 0L);

    public static final MapCodec<AccomplishmentCard> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("title").forGetter(AccomplishmentCard::title),
            Codec.STRING.listOf().optionalFieldOf("earned", List.of()).forGetter(AccomplishmentCard::earned),
            Codec.STRING.listOf().optionalFieldOf("fresh", List.of()).forGetter(AccomplishmentCard::fresh),
            Codec.STRING.optionalFieldOf("player", "").forGetter(AccomplishmentCard::playerName),
            Codec.STRING.optionalFieldOf("world", "").forGetter(AccomplishmentCard::worldName),
            Codec.LONG.optionalFieldOf("seed", 0L).forGetter(AccomplishmentCard::seed),
            Codec.BOOL.optionalFieldOf("hardcore", false).forGetter(AccomplishmentCard::hardcore),
            Codec.INT.optionalFieldOf("difficulty", 0).forGetter(AccomplishmentCard::difficulty),
            Codec.BOOL.optionalFieldOf("cheats", false).forGetter(AccomplishmentCard::cheats),
            Codec.LONG.optionalFieldOf("days", 0L).forGetter(AccomplishmentCard::days),
            Codec.LONG.optionalFieldOf("play_ticks", 0L).forGetter(AccomplishmentCard::playTicks),
            Codec.INT.optionalFieldOf("deaths", 0).forGetter(AccomplishmentCard::deaths),
            Codec.INT.optionalFieldOf("mob_kills", 0).forGetter(AccomplishmentCard::mobKills),
            Codec.LONG.optionalFieldOf("distance_cm", 0L).forGetter(AccomplishmentCard::distanceCm),
            Codec.LONG.optionalFieldOf("date", 0L).forGetter(AccomplishmentCard::dateEpochDay)
    ).apply(i, AccomplishmentCard::new));

    public static final StreamCodec<FriendlyByteBuf, AccomplishmentCard> STREAM_CODEC = StreamCodec.of(
            (buf, c) -> {
                buf.writeUtf(c.title);
                writeIds(buf, c.earned);
                writeIds(buf, c.fresh);
                buf.writeUtf(c.playerName);
                buf.writeUtf(c.worldName);
                buf.writeLong(c.seed);
                buf.writeBoolean(c.hardcore);
                buf.writeVarInt(c.difficulty);
                buf.writeBoolean(c.cheats);
                buf.writeVarLong(c.days);
                buf.writeVarLong(c.playTicks);
                buf.writeVarInt(c.deaths);
                buf.writeVarInt(c.mobKills);
                buf.writeVarLong(c.distanceCm);
                buf.writeLong(c.dateEpochDay);
            },
            buf -> new AccomplishmentCard(
                    buf.readUtf(), readIds(buf), readIds(buf), buf.readUtf(), buf.readUtf(),
                    buf.readLong(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean(),
                    buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarLong(), buf.readLong()));

    public AccomplishmentCard {
        earned = List.copyOf(earned);
        fresh = List.copyOf(fresh);
    }

    public boolean isEmpty() {
        return title.isEmpty();
    }

    private static void writeIds(FriendlyByteBuf buf, List<String> ids) {
        buf.writeVarInt(ids.size());
        for (String id : ids) {
            buf.writeUtf(id);
        }
    }

    private static List<String> readIds(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> ids = new ArrayList<>(n);
        for (int k = 0; k < n; k++) {
            ids.add(buf.readUtf());
        }
        return ids;
    }
}
