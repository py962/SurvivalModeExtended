package com.terrainlab.forestfloor;

import net.minecraft.util.StringRepresentable;

/** Which seasonal tree a pile of fallen leaves came from; decides its tint. */
public enum LeafSource implements StringRepresentable {
    OAK("oak", 0xFFC07838),      // russet
    BIRCH("birch", 0xFFF2CC3A),  // gold
    CHERRY("cherry", 0xFFB0203A); // crimson (no poplars before 26.3)

    private final String name;
    public final int color;

    LeafSource(String name, int color) {
        this.name = name;
        this.color = color;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
