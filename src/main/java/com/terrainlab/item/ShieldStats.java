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
 * @param knockback   knockback taken while blocking, shove dealt to blocked melee attackers, and
 *                    blocking angle
 * @param thorns      damage dealt to a melee attacker whose hit a raised shield blocks (not on autoblock)
 * @param durability  maximum damage and axe-disable recovery
 * @param lightness   raise speed and extra energy drain while raised
 * @param autoblock   chance to auto-block melee/projectile hits while held but not raised
 * @param magic       ward against effects and magic damage from other entities: 2 = each effect is
 *                    blocked for 1s while raised, 3 = full ward while raised, 4 = full ward while held
 * @param fire        fire/lava/fireball damage reduction while raised
 * @param blast       directional explosion coverage while raised: 0 never, 1 within 45 degrees of
 *                    facing, 2 within 90 (vanilla, damage only), 3 within 135 and also knockback,
 *                    4 any direction, damage and knockback
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
        int blast
) {

    private static final float[] KNOCKBACK_TAKEN = {1.40f, 1.20f, 1.00f, 0.50f, 0.25f};
    private static final float[] BASH_STRENGTH = {0.0f, 0.2f, 0.4f, 0.8f, 1.6f};
    private static final float[] BLOCK_DELAY_SECONDS = {0.60f, 0.40f, 0.25f, 0.15f, 0.05f};
    private static final float[] BLAST_HALF_ANGLE_DEGREES = {0.0f, 45.0f, 90.0f, 135.0f, 180.0f};

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

    /** Knockback multiplier while blocking: 140/120/100/50/25% for knockback 0-4. */
    public float knockbackMultiplier() {
        return KNOCKBACK_TAKEN[tier(knockback)];
    }

    /** Knockback dealt to a melee attacker whose hit a raised shield blocks: 0/0.2/0.4/0.8/1.6. */
    public float bashStrength() {
        return BASH_STRENGTH[tier(knockback)];
    }

    /**
     * {@code horizontal_blocking_angle} in degrees: the maximum angle between the facing direction and
     * the attack within which damage is fully negated. Knockback 2 gives vanilla's 90 (front hemisphere).
     */
    public float blockingAngleDegrees() {
        return 90.0f * (0.5f + 0.25f * knockback);
    }

    /** Damage dealt to a melee attacker whose hit a raised shield blocks; 0 = none. */
    public float thornsDamage() {
        return Math.max(0, thorns);
    }

    /** Chance (0..1) to auto-block a melee or projectile hit while held but not raised. */
    public float autoBlockChance() {
        return 0.25f * autoblock;
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
     * Maximum angle in degrees between the player's facing and the direction to an explosion's centre
     * for a raised shield to block it: 0 (never), 45, 90 (vanilla), 135, 180 (any direction).
     */
    public float blastCoverageDegrees() {
        return BLAST_HALF_ANGLE_DEGREES[tier(blast)];
    }

    /** Blast 3+: a covered explosion's knockback is blocked as well as its damage. */
    public boolean blastBlocksKnockback() {
        return blast >= 3;
    }

    /** Blast 4: every explosion is blocked, from any direction. */
    public boolean blastBlocksAllDirections() {
        return blast >= 4;
    }

    /** Enchantment value, same as the vanilla shield. */
    public static final int ENCHANTMENT_VALUE = 9;

    /** Durability lost per successful block, for every shield. */
    public static final int ITEM_DAMAGE_PER_BLOCK = 1;
}
