package dm.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A champion, built from the text stored beside a Hall of Champions mirror:
 * <pre>
 *   NAME \n TITLE \n \n GENDER \n HEALTH/STAMINA/MANA \n STATS \n SKILLS
 * </pre>
 * Numbers are written as hex digits using the letters A-P (A = 0).
 * Health, stamina and mana are 4 digits each. Stamina is stored x10, as in DM.
 * The stats are 2 digits each, in {@link Stat} order. Skills are 1 digit
 * per hidden skill; the 4 base skills are derived from them.
 */
public final class Champion {

    public enum Stat {
        LUCK, STRENGTH, DEXTERITY, WISDOM, VITALITY, ANTI_MAGIC, ANTI_FIRE;

        public String label() {
            return name().replace('_', '-');
        }
    }

    public static final List<String> BASE_SKILLS = List.of("FIGHTER", "NINJA", "PRIEST", "WIZARD");

    /** Skill titles from level 2 up; level 1 means unskilled. */
    private static final List<String> SKILL_TITLES = List.of(
            "NEOPHYTE", "NOVICE", "APPRENTICE", "JOURNEYMAN", "CRAFTSMAN", "ARTISAN", "ADEPT", "EXPERT",
            "LO MASTER", "UM MASTER", "ON MASTER", "EE MASTER", "PAL MASTER", "MON MASTER", "ARCHMASTER");

    private static final int HIDDEN_SKILL_COUNT = 16;

    private final String name;
    private final String title;
    private final char gender;
    private final int portrait;
    private int health;
    private final int maxHealth;
    private int stamina;
    private final int maxStamina;
    private int mana;
    private final int maxMana;
    private final int[] stats = new int[Stat.values().length];
    private final int[] maxStats = new int[Stat.values().length];
    /** Experience for the 4 base skills followed by the 16 hidden skills. */
    private final long[] experience = new long[BASE_SKILLS.size() + HIDDEN_SKILL_COUNT];
    private final Map<Slot, Item> items = new EnumMap<>(Slot.class);

    private Champion(String name, String title, char gender, int portrait,
                     int maxHealth, int maxStamina, int maxMana) {
        this.name = name;
        this.title = title;
        this.gender = gender;
        this.portrait = portrait;
        this.health = this.maxHealth = maxHealth;
        this.stamina = this.maxStamina = maxStamina;
        this.mana = this.maxMana = maxMana;
    }

    public static Champion parse(String text, int portrait) {
        String[] f = text.split("\n", -1);
        if (f.length < 7) {
            throw new IllegalArgumentException("expected 7 fields in champion text, got " + f.length + ": " + text);
        }
        String vitals = f[4];
        String statDigits = f[5];
        String skillDigits = f[6];
        if (vitals.length() < 12 || statDigits.length() < 14 || skillDigits.length() < HIDDEN_SKILL_COUNT) {
            throw new IllegalArgumentException("champion number fields too short: " + text);
        }
        char gender = f[3].isEmpty() ? 'M' : f[3].charAt(0);
        Champion c = new Champion(f[0].trim(), f[1].trim(), gender, portrait,
                hex(vitals, 0, 4), hex(vitals, 4, 4), hex(vitals, 8, 4));
        for (int s = 0; s < c.stats.length; s++) {
            c.stats[s] = c.maxStats[s] = hex(statDigits, s * 2, 2);
        }
        int base = BASE_SKILLS.size();
        for (int h = 0; h < HIDDEN_SKILL_COUNT; h++) {
            int level = hex(skillDigits, h, 1);
            c.experience[base + h] = level == 0 ? 0 : 125L << level;
        }
        // Each base skill's experience is the sum of its 4 hidden skills.
        for (int b = 0; b < base; b++) {
            long sum = 0;
            for (int h = 0; h < 4; h++) {
                sum += c.experience[base + b * 4 + h];
            }
            c.experience[b] = sum;
        }
        return c;
    }

    private static int hex(String s, int from, int digits) {
        int v = 0;
        for (int i = from; i < from + digits; i++) {
            int d = s.charAt(i) - 'A';
            if (d < 0 || d > 15) {
                throw new IllegalArgumentException("bad hex letter '" + s.charAt(i) + "' in " + s);
            }
            v = (v << 4) | d;
        }
        return v;
    }

    /** DM's skill level: 1 when unskilled, +1 for each doubling of experience from 500. */
    public int skillLevel(int skill) {
        long exp = experience[skill];
        if (skill >= BASE_SKILLS.size()) {
            exp = (exp + experience[(skill - BASE_SKILLS.size()) / 4]) / 2;
        }
        int level = 1;
        while (exp >= 500) {
            exp >>= 1;
            level++;
        }
        return level;
    }

    /** Title such as "JOURNEYMAN" for a base skill, or null if unskilled. */
    public String skillTitle(int baseSkill) {
        int level = skillLevel(baseSkill);
        return level < 2 ? null : SKILL_TITLES.get(Math.min(level - 2, SKILL_TITLES.size() - 1));
    }

    /**
     * Puts a starting item in the slot DM would choose: worn items on the body,
     * weapons in the action hand then the quiver (missiles) or the ready hand,
     * shields in the ready hand, potions and scrolls in the pouches, and
     * anything left over in the backpack. Returns the slot used, or null if full.
     */
    public Slot addStartingItem(Item item) {
        Slot slot = null;
        if (item.wornOn() != null) {
            slot = freeOf(item.wornOn());
        } else if (item.isShield()) {
            slot = freeOf(Slot.READY_HAND, Slot.ACTION_HAND);
        } else if (item.category() == Item.Category.WEAPON) {
            slot = item.isMissile()
                    ? freeOf(Slot.ACTION_HAND, Slot.QUIVER_1, Slot.QUIVER_2, Slot.QUIVER_3, Slot.QUIVER_4)
                    : freeOf(Slot.ACTION_HAND, Slot.READY_HAND);
        } else if (item.category() == Item.Category.POTION || item.category() == Item.Category.SCROLL) {
            slot = freeOf(Slot.POUCH_1, Slot.POUCH_2);
        }
        if (slot == null) {
            for (Slot s : Slot.values()) {
                if (s.isBackpack() && !items.containsKey(s)) {
                    slot = s;
                    break;
                }
            }
        }
        if (slot != null) {
            items.put(slot, item);
        }
        return slot;
    }

    /** Removes and returns the item in {@code slot}, or null if it's empty. */
    public Item take(Slot slot) {
        return items.remove(slot);
    }

    /**
     * Puts {@code item} in {@code slot} and returns the item it displaced, if
     * any. Throws if the item doesn't fit there ({@link Item#fits}).
     */
    public Item place(Slot slot, Item item) {
        if (!item.fits(slot)) {
            throw new IllegalArgumentException(item.name() + " doesn't fit " + slot);
        }
        return items.put(slot, item);
    }

    private Slot freeOf(Slot... candidates) {
        for (Slot s : candidates) {
            if (!items.containsKey(s)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Lowers health by up to {@code amount}, never below 0, and returns the
     * damage actually taken. At 0 the champion is dead; {@link Party#bury}
     * then drops their things.
     */
    public int takeDamage(int amount) {
        int taken = Math.max(0, Math.min(amount, health));
        health -= taken;
        return taken;
    }

    // ---- upkeep (see Upkeep) -----------------------------------------------

    /** DM's food and water: 1500 + random(256) on joining, 2048 at most, -1024 at worst; below 0 is hungry. */
    public static final int MAX_FOOD = 2048;
    public static final int MIN_FOOD = -1024;

    private int food;
    private int water;

    /** Hunger, as in DM's champion record (not shown as a number in the game). */
    public int food() {
        return food;
    }

    public int water() {
        return water;
    }

    void setFood(int food) {
        this.food = Math.max(MIN_FOOD, Math.min(food, MAX_FOOD));
    }

    void setWater(int water) {
        this.water = Math.max(MIN_FOOD, Math.min(water, MAX_FOOD));
    }

    /** Stamina in DM's own units (10 per point shown). */
    int rawStamina() {
        return stamina;
    }

    int rawMaxStamina() {
        return maxStamina;
    }

    /**
     * DM's F325: lowers stamina by {@code amount} (raises it if negative,
     * up to the maximum). Spending more than is left hurts: half the
     * shortfall, returned as damage still to be applied.
     */
    int decrementStamina(int amount) {
        stamina -= amount;
        if (stamina <= 0) {
            int damage = -stamina >> 1;
            stamina = 0;
            return damage;
        }
        stamina = Math.min(stamina, maxStamina);
        return 0;
    }

    void setMana(int mana) {
        this.mana = mana;
    }

    void addHealth(int amount) {
        health = Math.min(health + amount, maxHealth);
    }

    void addStamina(int amount) {
        stamina = Math.min(stamina + amount, maxStamina);
    }

    void setStat(Stat s, int value) {
        stats[s.ordinal()] = value;
    }

    /** The base skill numbers, for {@link #skillLevel}. */
    public static final int PRIEST = 2;
    public static final int WIZARD = 3;

    /**
     * What the champion carries, in tenths of a kilogram: everything on the
     * body, in hand and in the pack (DM's champion Load, without the leader's
     * hand, which {@link Party#load} adds).
     */
    public int load() {
        int load = 0;
        for (Item item : items.values()) {
            load += item.weight();
        }
        return load;
    }

    /**
     * DM's F309: 8 per point of strength plus 10 kg, less when stamina is
     * below half, a sixteenth more in elven boots, rounded up to a whole
     * kilogram. Wounds aren't modelled yet.
     */
    public int maxLoad() {
        int max = (stat(Stat.STRENGTH) << 3) + 100;
        max = staminaAdjusted(max);
        Item feet = items.get(Slot.FEET);
        if (feet != null && feet.category() == Item.Category.ARMOUR && feet.type() == ELVEN_BOOTS) {
            max += max >> 4;
        }
        max += 9;
        return max - max % 10;
    }

    private static final int ELVEN_BOOTS = 15;

    /** DM's F306: below half stamina a value shrinks toward half of itself. */
    int staminaAdjusted(int value) {
        int half = maxStamina >> 1;
        if (stamina < half) {
            value >>= 1;
            return value + (int) ((long) value * stamina / half);
        }
        return value;
    }

    /**
     * DM's F348 for a potion raising a statistic: the gain halves above 120
     * and again above 150 (then plus 1), and the value stops at 170.
     */
    void raiseStat(Stat s, int delta) {
        int current = stat(s);
        if (current > 120) {
            delta >>= 1;
            if (current > 150) {
                delta >>= 1;
            }
            delta++;
        }
        stats[s.ordinal()] = current + Math.min(delta, 170 - current);
    }

    public String name() {
        return name;
    }

    public String title() {
        return title;
    }

    /** Name and title the way DM writes them, e.g. "HALK THE BARBARIAN" or "ELIJA, LION OF YAITOPYA". */
    public String fullName() {
        if (title.isEmpty()) {
            return name;
        }
        return title.startsWith("THE ") ? name + " " + title : name + ", " + title;
    }

    public char gender() {
        return gender;
    }

    public int portrait() {
        return portrait;
    }

    public int health() {
        return health;
    }

    public int maxHealth() {
        return maxHealth;
    }

    /** Stamina as displayed (DM stores it x10). */
    public int stamina() {
        return stamina / 10;
    }

    public int maxStamina() {
        return maxStamina / 10;
    }

    public int mana() {
        return mana;
    }

    public int maxMana() {
        return maxMana;
    }

    public int stat(Stat s) {
        return stats[s.ordinal()];
    }

    public int maxStat(Stat s) {
        return maxStats[s.ordinal()];
    }

    public Map<Slot, Item> items() {
        return Collections.unmodifiableMap(items);
    }

    /** Swaps an item in place (a torch burning down, a waterskin drunk from) without the slot rules. */
    void replace(Slot slot, Item item) {
        items.put(slot, item);
    }
}
