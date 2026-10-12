package com.terrainlab.mixin;

import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractBoat.class)
public interface AbstractBoatAccessor {
    @Accessor("inputUp")
    boolean getInputUp();

    @Accessor("inputDown")
    boolean getInputDown();

    @Accessor("inputLeft")
    boolean getInputLeft();

    @Accessor("inputRight")
    boolean getInputRight();

    @Accessor("waterLevel")
    double getWaterLevel();
}