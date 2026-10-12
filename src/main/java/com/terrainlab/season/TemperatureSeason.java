package com.terrainlab.season;

/**
 * The four temperature states that calendar {@link Season}s map onto. Early Spring and Late Autumn share
 * NEAR_WINTER; Late Spring and Early Autumn share NEAR_SUMMER.
 */
public enum TemperatureSeason {
    MIDWINTER,
    NEAR_WINTER,
    NEAR_SUMMER,
    MIDSUMMER
}
