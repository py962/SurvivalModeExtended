package com.terrainlab.village;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflective access to a villager's profession key and level. Resolves the accessor on
 * VillagerData by trying candidate method names and caches the result per class.
 *
 * <p>TODO: replace with direct calls to {@code getVillagerData().profession()} / {@code level()}.
 */
public final class VillagerProfessionAccess {

    private static final String[] PROFESSION_METHOD_CANDIDATES = {
            "profession", "getProfession", "villagerProfession", "getVillagerProfession"
    };
    private static final String[] LEVEL_METHOD_CANDIDATES = {
            "level", "getLevel", "villagerLevel", "getVillagerLevel"
    };

    private static final Map<Class<?>, Method> PROFESSION_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> LEVEL_METHOD_CACHE = new ConcurrentHashMap<>();
    private static volatile boolean loggedOnce = false;
    private static volatile boolean loggedKeyResolutionOnce = false;

    private VillagerProfessionAccess() {
    }

    public static String getProfessionKey(Villager villager) {
        Object villagerData = villager.getVillagerData();
        Object professionObj = invokeCandidate(villagerData, PROFESSION_METHOD_CANDIDATES, PROFESSION_METHOD_CACHE);
        return professionObj == null ? "unknown" : keyFromProfessionObject(professionObj);
    }

    public static int getLevel(Villager villager) {
        Object villagerData = villager.getVillagerData();
        Object levelObj = invokeCandidate(villagerData, LEVEL_METHOD_CANDIDATES, LEVEL_METHOD_CACHE);
        return levelObj instanceof Integer i ? i : 0;
    }

    private static String keyFromProfessionObject(Object professionObj) {
        Object unwrapped = unwrapHolder(professionObj);

        if (unwrapped instanceof VillagerProfession profession) {
            Identifier id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
            if (id != null) {
                if (!loggedKeyResolutionOnce) {
                    loggedKeyResolutionOnce = true;
                    // com.terrainlab.TerrainLab.LOGGER.info(
                    //         "[VILLAGER_PROFESSION_ACCESS] resolved profession key '{}' via BuiltInRegistries.VILLAGER_PROFESSION "
                    //                 + "lookup - spot-check this against what you expect it to be.", id.getPath());
                }
                return id.getPath();
            }
        }

        // Fallback: derive a key from toString() when the registry lookup fails.
        String raw = unwrapped.toString().toLowerCase(Locale.ROOT);
        int lastSep = Math.max(raw.lastIndexOf('/'), raw.lastIndexOf(':'));
        String key = (lastSep >= 0 && lastSep + 1 < raw.length()) ? raw.substring(lastSep + 1) : raw;
        key = key.replaceAll("[^a-z_]", "");
        return key.isEmpty() ? "unknown" : key;
    }

    private static Object unwrapHolder(Object professionObj) {
        try {
            Method valueMethod = professionObj.getClass().getMethod("value");
            Object inner = valueMethod.invoke(professionObj);
            if (inner != null) {
                return inner;
            }
        } catch (ReflectiveOperationException ignored) {
            // Not Holder-shaped; use the object as-is.
        }
        return professionObj;
    }

    private static Object invokeCandidate(Object target, String[] candidates, Map<Class<?>, Method> cache) {
        if (target == null) {
            return null;
        }
        Method cached = cache.get(target.getClass());
        if (cached != null) {
            return safeInvoke(target, cached);
        }
        for (String name : candidates) {
            try {
                Method m = target.getClass().getMethod(name);
                m.setAccessible(true);
                cache.put(target.getClass(), m);
                logResolutionOnce(target.getClass(), m);
                return safeInvoke(target, m);
            } catch (NoSuchMethodException ignored) {
                // try next candidate
            }
        }
        com.terrainlab.TerrainLab.LOGGER.error(
                "[VILLAGER_PROFESSION_ACCESS] none of {} found on {} - trade quality/farmer counting "
                        + "will read as empty/0 until this is fixed. Declared methods on that class: {}",
                Arrays.toString(candidates), target.getClass(), Arrays.toString(target.getClass().getMethods()));
        return null;
    }

    private static Object safeInvoke(Object target, Method m) {
        try {
            return m.invoke(target);
        } catch (ReflectiveOperationException e) {
            com.terrainlab.TerrainLab.LOGGER.warn("[VILLAGER_PROFESSION_ACCESS] invoke failed: {}", e.toString());
            return null;
        }
    }

    private static void logResolutionOnce(Class<?> dataClass, Method resolved) {
        if (!loggedOnce) {
            loggedOnce = true;
            // com.terrainlab.TerrainLab.LOGGER.info(
            //         "[VILLAGER_PROFESSION_ACCESS] resolved '{}' as the accessor on {} - spot-check a "
            //                 + "villager's logged profession key against what you expect it to be.",
            //         resolved.getName(), dataClass);
        }
    }
}
