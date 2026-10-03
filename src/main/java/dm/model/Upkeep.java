package dm.model;

/**
 * What time does to a champion, and eating and drinking, ported from
 * ReDMCSB (the DM 1.2+ rules, which the PC version follows):
 * <ul>
 *   <li>{@link #applyTimeEffects}: F331, run every {@link #PERIOD} game ticks;</li>
 *   <li>{@link #stepCost}: F366, paid by every living champion on every move attempt;</li>
 *   <li>{@link #consume}: F349, putting the held item in a champion's mouth.</li>
 * </ul>
 * Sleeping, wounds, poison and temporary experience aren't modelled yet, so
 * those branches of DM's code are left out.
 */
public final class Upkeep {

    /** F331 runs when the game time is a multiple of 64 (16 while sleeping). */
    public static final int PERIOD = 64;
    /** The Ekkhard Cross worn on the neck speeds healing. */
    private static final int EKKHARD_CROSS = 38;

    private Upkeep() {
    }

    /**
     * F331 for one living champion at game time {@code time}, the party
     * having last moved at {@code lastMove}. Returns the damage still to be
     * applied (stamina spent below zero hurts).
     */
    static int applyTimeEffects(Champion c, long time, long lastMove) {
        int t = (int) time;
        int criteria = (((t & 0x80) + ((t & 0x100) >> 2)) + ((t & 0x40) << 2)) >> 2;
        int damage = 0;

        // Mana: regained when a 0-63 time pattern falls below wisdom + priest and wizard levels, paid in stamina.
        int magic = c.skillLevel(Champion.WIZARD) + c.skillLevel(Champion.PRIEST);
        if (c.mana() < c.maxMana() && criteria < c.stat(Champion.Stat.WISDOM) + magic) {
            int gain = c.maxMana() / 40 + 1;
            damage += c.decrementStamina(gain * Math.max(7, 16 - magic));
            c.setMana(c.mana() + Math.min(gain, c.maxMana() - c.mana()));
        } else if (c.mana() > c.maxMana()) {
            c.setMana(c.mana() - 1);
        }

        // Stamina: fed and watered champions recover it, eating and drinking as they do; starving ones lose it.
        int stamina = c.rawStamina();
        int max = c.rawMaxStamina();
        int cycles = 4;
        int magnitude = max;
        while (stamina < (magnitude >>= 1)) {
            cycles += 2;
        }
        int loss = 0;
        int amount = Math.max(1, Math.min((max >> 8) - 1, 6));
        long rested = time - lastMove;
        if (rested > 80) {
            amount++;
            if (rested > 250) {
                amount++;
            }
        }
        int food = c.food();
        int water = c.water();
        do {
            boolean aboveHalf = cycles <= 4;
            if (food < -512) {
                if (aboveHalf) {
                    loss += amount;
                    food -= 2;
                }
            } else {
                if (food >= 0) {
                    loss -= amount;
                }
                food -= aboveHalf ? 2 : cycles >> 1;
            }
            if (water < -512) {
                if (aboveHalf) {
                    loss += amount;
                    water -= 1;
                }
            } else {
                if (water >= 0) {
                    loss -= amount;
                }
                water -= aboveHalf ? 1 : cycles >> 2;
            }
            cycles--;
        } while (cycles > 0 && stamina - loss < max);
        damage += c.decrementStamina(loss);
        c.setFood(food);
        c.setWater(water);

        // Health: regained while stamina is at least a quarter, as often as vitality allows.
        if (c.health() < c.maxHealth() && c.rawStamina() >= max >> 2
                && criteria < c.stat(Champion.Stat.VITALITY) + 12) {
            int gain = (c.maxHealth() >> 7) + 1;
            Item neck = c.items().get(Slot.NECK);
            if (neck != null && neck.category() == Item.Category.JUNK && neck.type() == EKKHARD_CROSS) {
                gain += (gain >> 1) + 1;
            }
            c.addHealth(gain);
        }

        // Statistics drift back toward their maximum every 256 ticks.
        if ((t & 255) == 0) {
            for (Champion.Stat s : Champion.Stat.values()) {
                int current = c.stat(s);
                int maximum = c.maxStat(s);
                if (current < maximum) {
                    c.setStat(s, current + 1);
                } else if (current > maximum && maximum > 0) {
                    c.setStat(s, current - current / maximum);
                }
            }
        }
        return damage;
    }

    /** F366: the stamina a move attempt costs, more for a heavily laden champion. */
    static int stepCost(int load, int maxLoad) {
        return load * 3 / maxLoad + 1;
    }

    /**
     * F349: {@code c} eats or drinks {@code item}. Returns what the hand holds
     * afterwards: null when food is eaten, a waterskin with one draught less,
     * or an empty flask after a potion. Returns {@code item} itself, untouched,
     * if it can't go in the mouth (or is an empty waterskin).
     */
    public static Item consume(Champion c, Item item) {
        if (!ItemCatalog.isConsumable(item)) {
            return item;
        }
        Item left;
        if (item.category() == Item.Category.JUNK && item.type() == ItemCatalog.WATERSKIN) {
            if (item.charges() == 0) {
                return item;
            }
            c.setWater(c.water() + 800);
            left = item.withCharges(item.charges() - 1);
        } else if (item.category() == Item.Category.POTION) {
            drinkPotion(c, item);
            left = ItemCatalog.item(Item.Category.POTION, ItemCatalog.EMPTY_FLASK);
        } else {
            c.setFood(c.food() + ItemCatalog.foodValue(item));
            left = null;
        }
        return left;
    }

    /** The potion effects that need nothing not yet modelled (YA's shield and antivenin's cure do nothing yet). */
    private static void drinkPotion(Champion c, Item potion) {
        int power = potion.charges();
        int counter = ((511 - power) / (32 + (power + 1) / 8)) >> 1;
        int adjusted = power / 25 + 8;
        switch (potion.name()) {
            case "ROS POTION" -> c.raiseStat(Champion.Stat.DEXTERITY, adjusted);
            case "KU POTION" -> c.raiseStat(Champion.Stat.STRENGTH, power / 35 + 5);
            case "DANE POTION" -> c.raiseStat(Champion.Stat.WISDOM, adjusted);
            case "NETA POTION" -> c.raiseStat(Champion.Stat.VITALITY, adjusted);
            case "MON POTION" -> c.addStamina(c.rawMaxStamina() / counter);
            case "VI POTION" -> c.addHealth(c.maxHealth() / counter);
            case "EE POTION" -> {
                int mana = Math.min(900, c.mana() + adjusted + (adjusted - 8));
                if (mana > c.maxMana()) {
                    mana -= (mana - Math.max(c.mana(), c.maxMana())) >> 1;
                }
                c.setMana(mana);
            }
            case "WATER FLASK" -> c.setWater(c.water() + 1600);
            default -> { }
        }
    }
}
