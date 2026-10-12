package com.terrainlab.item;

import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * Seven 0-4 balance ratings for one wood shield and the formulas that turn them into item
 * properties and combat behaviour. A rating of 2 (1 for fire) generally matches the vanilla shield.
 *
 * @param id          registry path, e.g. {@code "oak_shield"}
 * @param displayName English display name
 * @param plankItem   thematic wood item (informational; repair uses {@code #minecraft:planks})
 * @param active      knockback resistance and blocking angle while raised
 * @param passive     chance to auto-block melee/projectile hits while held but not raised
 * @param durability  maximum damage
 * @param lightness   raise speed and axe-disable recovery
 * @param magic       ward against effects and magic damage from other entities: 2 = each effect is
 *                    blocked for 1s while raised, 3 = full ward while raised, 4 = full ward while held
 * @param fire        fire/lava/fireball damage reduction while raised
 * @param blast       chance to ignore an explosion's damage and knockback while raised
 */
public record ShieldStats(
        String id,
        String displayName,
        Supplier<Item> plankItem,
        int active,
        int passive,
        int durability,
        int lightness,
        int magic,
        int fire,
        int blast
) {

    /** Durability 2 gives vanilla's 336. */
    public int maxDamage() {
        return 36 + 150 * durability;
    }

    /** Raise delay; lightness 2 gives vanilla's 0.25s. */
    public float blockDelaySeconds() {
        return 0.35f - 0.05f * lightness;
    }

    /** Axe-disable cooldown scale; lightness 2 gives vanilla's 1.0. */
    public float disableCooldownScale() {
        return 1.3f - 0.15f * lightness;
    }

    /** Knockback multiplier while blocking: 1.0 at active 2, 20% per point either side. */
    public float knockbackMultiplier() {
        return Math.max(0f, 1.0f - 0.20f * (active - 2));
    }

    /** Knockback dealt to a melee attacker whose hit a raised shield blocks; 0.2 per active point. */
    public float bashStrength() {
        return 0.2f * Math.max(0, active);
    }

    /**
     * {@code horizontal_blocking_angle} in degrees: the maximum angle between the facing direction and
     * the attack within which damage is fully negated. Active 2 gives vanilla's 90 (front hemisphere).
     */
    public float blockingAngleDegrees() {
        return 90.0f * (0.5f + 0.25f * active);
    }

    /** Chance (0..1) to auto-block a melee or projectile hit while held but not raised. */
    public float autoBlockChance() {
        return 0.25f * passive;
    }

    /** Magic 2: ticks each effect is blocked after it first lands. */
    public static final int MAGIC_2_WARD_TICKS = 20;

    /**
     * Magic 2: ticks without an application attempt before an effect gets a fresh ward window. A
     * lingering cloud re-applies every 5 ticks, so standing in one does not renew the window.
     */
    public static final int MAGIC_2_SOURCE_RESET_TICKS = 20;

    /** Magic 3+: full ward while raised. */
    public boolean magicFullWardWhileRaised() {
        return magic >= 3;
    }

    /** Magic 2: per-effect timed ward while raised. */
    public boolean magicWindowWardWhileRaised() {
        return magic == 2;
    }

    /**
     * @deprecated Magic is not a percentage roll; use {@link #magicFullWardWhileRaised()},
     * {@link #magicWindowWardWhileRaised()} or {@link #magicWardWhileHeld()}.
     */
    @Deprecated
    public float magicAutoBlockChance() {
        return 0.25f * magic;
    }

    /** Magic 4: ward is up whenever the shield is held in either hand. */
    public boolean magicWardWhileHeld() {
        return magic >= 4;
    }

    /** Fraction (0..1) of fire/lava/fireball damage negated while blocking: 0 at fire 1, 1 at fire 4. */
    public float fireImmunityFraction() {
        return Math.max(0f, Math.min(1f, (fire - 1) / 3.0f));
    }

    /**
     * Chance (0..1) while blocking to ignore an explosion's damage and knockback: 0 at blast 2 (vanilla
     * already blocks the damage), 1 at blast 4.
     */
    public float blastExemptionChance() {
        return Math.max(0f, Math.min(1f, (blast - 2) / 2.0f));
    }

    /** Enchantment value, same as the vanilla shield. */
    public static final int ENCHANTMENT_VALUE = 9;

    /** Durability lost per successful block, for every shield. */
    public static final int ITEM_DAMAGE_PER_BLOCK = 1;
}
