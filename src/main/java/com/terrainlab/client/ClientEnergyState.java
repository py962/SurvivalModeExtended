package com.terrainlab.client;

public class ClientEnergyState {

    private static float energy = 1.0F;

    private ClientEnergyState() {
    }

    public static float getEnergy() {
        return energy;
    }

    public static void setEnergy(float value) {
        energy = value;
    }
}
