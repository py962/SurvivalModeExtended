package com.terrainlab.item;

import com.terrainlab.energy.PlayerEnergyConstants;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * Eight 0-4 balance ratings for one wood shield and the formulas that turn them into item
 * properties and combat behaviour. A rating of 2 (0 for thorns/autoblock/magic, 1 for fire)
 * generally matches the vanilla shield.
 *
 * @param id          registry path, e.g. {@code "oak_shield"}
 * @param displayName English display name
 * @param plankItem   thematic wood item (informational; repair uses {@code #minecraft:planks})
 * @param knockback   push taken from attacks a raised shield blocks, and shove dealt to blocked
 *                    melee attackers
 * @param thorns      damage dealt to a melee attacker whose hit a raised shield blocks (not on autoblock)
 * @param durability  maximum damage and axe-disable recovery
 * @param lightness   raise speed and extra energy drain while raised
 * @param autoblock   chance to auto-block melee/projectile hits while held but not raised
 * @param magic       ward against potions, clouds, dragon's breath, auras and guardian lasers: 2 = half,
 *                    3 = three-quarters, 4 = all (lasers only while raised, the rest while held)
 * @param fire        fire/lava/fireball damage reduction while raised
 * @param cover       arc a raised shield blocks, for every attack: 90/135/180/270/360 degrees
 */
public record ShieldStats(
        String id,
        String displayName,
        Supplier<Item> plankItem,
        int knockback,
        int thorns,
        int durability,
        int lightness,
        int autoblock,
        int magic,
        int fire,
        int cover
) {

    private static final float[] KNOCKBACK_TAKEN = {1.50f, 1.25f, 1.00f, 0.50f, 0.0f};
    private static final float[] BASH_STRENGTH = {0.0f, 0.0f, 0.0f, 1.2f, 2.4f};
    private static final float[] BLOCK_DELAY_SECONDS = {0.60f, 0.40f, 0.25f, 0.15f, 0.05f};
    private static final float[] COVER_HALF_ANGLE_DEGREES = {45.0f, 67.5f, 90.0f, 135.0f, 180.0f};
    private static final float[] MAGIC_WARD = {0.0f, 0.0f, 0.5f, 0.75f, 1.0f};

    private static int tier(int rating) {
        return Math.max(0, Math.min(4, rating));
    }

    /** Durability 2 gives vanilla's 336. */
    public int maxDamage() {
        return 36 + 150 * durability;
    }

    /** Raise delay: 0.60/0.40/0.25/0.15/0.05s for lightness 0-4; lightness 2 gives vanilla's 0.25s. */
    public float blockDelaySeconds() {
        return BLOCK_DELAY_SECONDS[tier(lightness)];
    }

    /**
     * Extra energy drained per tick while the shield is raised: lightness 0 drains as much as
     * sprinting, lightness 1 half that, lightness 2+ nothing.
     */
    public float raisedEnergyDrainPerTick() {
        return switch (tier(lightness)) {
            case 0 -> PlayerEnergyConstants.SHIELD_RAISED_DRAIN_PER_TICK_LIGHTNESS_0;
            case 1 -> PlayerEnergyConstants.SHIELD_RAISED_DRAIN_PER_TICK_LIGHTNESS_1;
            default -> 0.0f;
        };
    }

    /** Axe-disable cooldown scale; durability 2 gives vanilla's 1.0, stronger shields recover sooner. */
    public float disableCooldownScale() {
        return 1.3f - 0.15f * durability;
    }

    /** Scale on the push from a blocked attack: 150/125/100/50/0% for knockback 0-4. */
    public float knockbackMultiplier() {
        return KNOCKBACK_TAKEN[tier(knockback)];
    }

    /** Knockback dealt to a melee attacker whose hit a raised shield blocks: none up to 2, then 1.2/2.4. */
    public float bashStrength() {
        return BASH_STRENGTH[tier(knockback)];
    }

    /**
     * {@code horizontal_blocking_angle} in degrees: the maximum angle between the facing direction and
     * the attack within which it is blocked. Cover 2 gives vanilla's 90 (front half), 4 all round.
     */
    public float coverHalfAngleDegrees() {
        return COVER_HALF_ANGLE_DEGREES[tier(cover)];
    }

    /** Damage dealt to a melee attacker whose hit a raised shield blocks: 2 per thorns level. */
    public float thornsDamage() {
        return 2 * Math.max(0, thorns);
    }

    /** Chance (0..1) to auto-block a melee or projectile hit while held but not raised. */
    public float autoBlockChance() {
        return 0.25f * autoblock;
    }

    /** Share of a potion, cloud, breath, aura or laser warded: half at magic 2, three-quarters at 3, all at 4. */
    public float magicWardFraction() {
        return MAGIC_WARD[tier(magic)];
    }

    /** Magic 4: potions, clouds, breath and auras are warded whenever the shield is held. */
    public boolean magicWardWhileLowered() {
        return magic >= 4;
    }

    /** Fraction (0..1) of fire/lava/fireball damage negated while blocking: 0 at fire 1, 1 at fire 4. */
    public float fireImmunityFraction() {
        return Math.max(0f, Math.min(1f, (fire - 1) / 3.0f));
    }

    /** Enchantment value, same as the vanilla shield. */
    public static final int ENCHANTMENT_VALUE = 9;

    /** Durability lost per successful block, for every shield. */
    public static final int ITEM_DAMAGE_PER_BLOCK = 1;
}
