package com.terrainlab.client;

public class ClientInternalTempState {

    private static float internalTemp = 20.0F;

    private ClientInternalTempState() {
    }

    public static float getInternalTemp() {
        return internalTemp;
    }

    public static void setInternalTemp(float temp) {
        internalTemp = temp;
    }
}
