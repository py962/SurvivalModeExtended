package com.terrainlab.client;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Latest dialogue line received from a Trail Trader, with the sender's UUID. Persists
 * while that trader's trade screen is open and is cleared when the session ends, so it
 * never shows on another merchant's screen.
 */
public class ClientTrailTraderDialogueState {

    @Nullable
    private static UUID traderId;

    @Nullable
    private static String currentLineKey;

    private ClientTrailTraderDialogueState() {
    }

    @Nullable
    public static UUID getTraderId() {
        return traderId;
    }

    @Nullable
    public static String getCurrentLineKey() {
        return currentLineKey;
    }

    public static void setCurrentLine(
            UUID newTraderId,
            String translationKey) {

        if (translationKey == null || translationKey.isEmpty()) {
            clear();
            return;
        }

        traderId = newTraderId;
        currentLineKey = translationKey;
    }

    /** Clears the dialogue state; called when the Trail Trader trade session ends. */
    public static void clear() {
        traderId = null;
        currentLineKey = null;
    }
}
