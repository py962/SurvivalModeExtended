package com.terrainlab.energy;

import com.terrainlab.registry.ModAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForgeMod;

import static com.terrainlab.energy.PlayerEnergyConstants.*;

/**
 * Applies energy-boost effects above REST_CAP (sprint and swim speed, jump height) and the zero-energy penalty.
 *
 * <p>JUMP_STRENGTH is a raw velocity attribute (default 0.42), so the boost uses ADD_VALUE. With gravity
 * 0.08 and drag 0.98 per tick, a strength of 0.507 peaks at BOOST_JUMP_HEIGHT_BLOCKS (1.75), a delta of 0.087.
 * Zero-energy Weakness is refreshed with a short duration each tick so it never clobbers an
 * independent Weakness effect.
 */
public final class PlayerEnergyEffects {

    // Longer than the HUD's 10 s fade-out, so the badge stays solid; cleared once energy returns.
    private static final int WEAKNESS_REFRESH_DURATION_TICKS = 260;
    private static final int WEAKNESS_AMPLIFIER = 0;

    private static final double JUMP_STRENGTH_DELTA = 0.087; // see class javadoc

    private static final AttributeModifier SPRINT_BOOST_MODIFIER = new AttributeModifier(
            Identifier.fromNamespaceAndPath("terrainlab", "energy_sprint_boost"),
            BOOST_SPRINT_SPEED_MODIFIER,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
    );

    private static final AttributeModifier SWIM_BOOST_MODIFIER = new AttributeModifier(
            Identifier.fromNamespaceAndPath("terrainlab", "energy_swim_boost"),
            BOOST_SWIM_SPEED_MODIFIER,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
    );

    private static final AttributeModifier JUMP_BOOST_MODIFIER = new AttributeModifier(
            Identifier.fromNamespaceAndPath("terrainlab", "energy_jump_boost"),
            JUMP_STRENGTH_DELTA,
            AttributeModifier.Operation.ADD_VALUE
    );

    private PlayerEnergyEffects() {
    }

    public static void tick(ServerPlayer player) {
        float energy = player.getData(ModAttachments.PLAYER_ENERGY.get());
        boolean boosted = energy > REST_CAP;
        boolean zeroEnergy = energy <= MIN_ENERGY;

        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            if (boosted && player.isSprinting()) {
                speedAttr.addOrUpdateTransientModifier(SPRINT_BOOST_MODIFIER);
            } else {
                speedAttr.removeModifier(SPRINT_BOOST_MODIFIER);
            }
        }

        AttributeInstance swimAttr = player.getAttribute(NeoForgeMod.SWIM_SPEED);
        if (swimAttr != null) {
            if (boosted && player.isSwimming()) {
                swimAttr.addOrUpdateTransientModifier(SWIM_BOOST_MODIFIER);
            } else {
                swimAttr.removeModifier(SWIM_BOOST_MODIFIER);
            }
        }

        AttributeInstance jumpAttr = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jumpAttr != null) {
            if (boosted) {
                jumpAttr.addOrUpdateTransientModifier(JUMP_BOOST_MODIFIER);
            } else {
                jumpAttr.removeModifier(JUMP_BOOST_MODIFIER);
            }
        }

        // No zero-energy penalties in creative/spectator.
        if (zeroEnergy && !player.isCreative() && !player.isSpectator()) {
            if (player.isSprinting()) {
                player.setSprinting(false);
            }
            player.addEffect(new MobEffectInstance(
                    MobEffects.WEAKNESS,
                    WEAKNESS_REFRESH_DURATION_TICKS,
                    WEAKNESS_AMPLIFIER,
                    false,
                    false,
                    true
            ));
        } else {
            MobEffectInstance weakness = player.getEffect(MobEffects.WEAKNESS);
            if (weakness != null && !weakness.isVisible() && weakness.getAmplifier() == WEAKNESS_AMPLIFIER
                    && weakness.getDuration() <= WEAKNESS_REFRESH_DURATION_TICKS) {
                player.removeEffect(MobEffects.WEAKNESS);
            }
        }
    }
}
