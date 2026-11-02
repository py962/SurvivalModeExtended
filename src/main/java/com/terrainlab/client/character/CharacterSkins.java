package com.terrainlab.client.character;

import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The skins a character can wear: vanilla's nine default player skins plus the mod's own
 * (woodsman), each with wide or slim arms. Stored in the character list by name ("steve", "alex",
 * "woodsman", ...) plus the arm style.
 */
public final class CharacterSkins {

    /** Vanilla's default skins (textures in the minecraft namespace); also the pool for random picks. */
    private static final List<String> VANILLA = List.of("steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri");
    /** The mod's own skins (textures under terrainlab:textures/entity/player/wide|slim/). */
    private static final List<String> MOD = List.of("woodsman");
    public static final List<String> NAMES = java.util.stream.Stream.concat(VANILLA.stream(), MOD.stream()).toList();
    /** Picker value for "the account's own skin" (custom or default); stored as no skin (null). */
    public static final String ACCOUNT = "account";
    /** Picker order: the account's own skin first, then the default skins. */
    public static final List<String> CHOICES = java.util.stream.Stream.concat(
            java.util.stream.Stream.of(ACCOUNT), NAMES.stream()).toList();
    private static final Map<String, PlayerSkin> CACHE = new HashMap<>();

    private CharacterSkins() {
    }

    public static boolean isValid(String name) {
        return name != null && NAMES.contains(name);
    }

    /** The skin for a stored choice, or null for an unknown name (the account skin is used then). */
    public static PlayerSkin skin(String name, boolean slim) {
        if (!isValid(name)) return null;
        return CACHE.computeIfAbsent((slim ? "slim/" : "wide/") + name, path -> new PlayerSkin(
                new ClientAsset.ResourceTexture(MOD.contains(name)
                        ? Identifier.fromNamespaceAndPath(com.terrainlab.TerrainLab.MODID, "entity/player/" + path)
                        : Identifier.withDefaultNamespace("entity/player/" + path)),
                null, null, slim ? PlayerModelType.SLIM : PlayerModelType.WIDE, true));
    }

    /** The account's own skin, as the game would draw it; the default skin until it has loaded. */
    public static java.util.function.Supplier<PlayerSkin> accountSkin() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        return mc.getSkinManager().createLookup(mc.getGameProfile(), false);
    }

    public static String randomName(RandomSource random) {
        return VANILLA.get(random.nextInt(VANILLA.size()));
    }
}
