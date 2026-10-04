package dm.model;

import java.util.Random;

/**
 * The game menu's difficulty (not in DM). Each level is a set of
 * percentages: what the creatures' blows do, what the champions' blows and
 * projectiles do, the experience every skill earns, and how fast food and
 * water run down. NORMAL is DM unchanged.
 */
public enum Difficulty {
    EASY(80, 120, 120, 80),
    NORMAL(100, 100, 100, 100),
    HARD(120, 80, 80, 120);

    private final int creatureDamage;
    private final int partyDamage;
    private final int experience;
    private final int hunger;

    Difficulty(int creatureDamage, int partyDamage, int experience, int hunger) {
        this.creatureDamage = creatureDamage;
        this.partyDamage = partyDamage;
        this.experience = experience;
        this.hunger = hunger;
    }

    /** A creature's attack on a champion (and its poison), scaled. */
    int creatureDamage(int value, Random random) {
        return scale(value, creatureDamage, random);
    }

    /** A champion's blow or projectile on a creature, scaled. */
    int partyDamage(int value, Random random) {
        return scale(value, partyDamage, random);
    }

    int experience(int value, Random random) {
        return scale(value, experience, random);
    }

    /** Food or water used up, scaled. */
    int hunger(int value, Random random) {
        return scale(value, hunger, random);
    }

    /**
     * {@code value} × {@code percent} / 100, the fraction rounded up as often
     * as it is worth (so 20% of 3 is 1 three times in five), keeping small
     * numbers right on average. 100% draws no random number, so NORMAL plays
     * exactly as DM does.
     */
    static int scale(int value, int percent, Random random) {
        if (percent == 100 || value <= 0) {
            return value;
        }
        long scaled = (long) value * percent;
        int whole = (int) (scaled / 100);
        return random.nextInt(100) < scaled % 100 ? whole + 1 : whole;
    }
}
