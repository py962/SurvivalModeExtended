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
 * The skins a character can wear: vanilla's nine default player skins, each with wide or slim
 * arms. Stored in the character list by name ("steve", "alex", ...) plus the arm style.
 */
public final class CharacterSkins {

    public static final List<String> NAMES = List.of("steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri");
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
                new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace("entity/player/" + path)),
                null, null, slim ? PlayerModelType.SLIM : PlayerModelType.WIDE, true));
    }

    public static String randomName(RandomSource random) {
        return NAMES.get(random.nextInt(NAMES.size()));
    }
}
