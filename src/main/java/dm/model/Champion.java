package dm.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
public final class Champion implements Serializable {

    private static final long serialVersionUID = 1L;

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

    private String name;
    private String title;
    private final char gender;
    private final int portrait;
    private int health;
    private int maxHealth;
    private int stamina;
    private int maxStamina;
    private int mana;
    private int maxMana;
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

    /**
     * DM's F303: the skill level, 1 when unskilled and +1 for each doubling
     * of experience from 500. A hidden skill averages its own and its base
     * skill's experience; temporary experience counts too. Some items held
     * or worn raise it: the Firestaff (+1, +2 complete) for every skill, and
     * on the neck the Pendant Feral (wizard), the Ekkhard Cross (defend), the
     * Gem of Ages or, in the action hand, the Sceptre of Lyf (heal), and the
     * Moonstone (influence).
     */
    public int skillLevel(int skill) {
        if (asleep) {
            return 1; // F303: everyone is unskilled in their sleep
        }
        int level = baseLevel(skill, true);
        Item hand = items.get(Slot.ACTION_HAND);
        boolean weapon = hand != null && hand.category() == Item.Category.WEAPON;
        if (weapon && hand.type() == FIRESTAFF) {
            level++;
        } else if (weapon && hand.type() == FIRESTAFF_COMPLETE) {
            level += 2;
        }
        Item neck = items.get(Slot.NECK);
        int neckJunk = neck != null && neck.category() == Item.Category.JUNK ? neck.type() : -1;
        switch (skill) {
            case WIZARD -> level += neckJunk == PENDANT_FERAL ? 1 : 0;
            case DEFEND -> level += neckJunk == EKKHARD_CROSS ? 1 : 0;
            case HEAL -> level += neckJunk == GEM_OF_AGES || weapon && hand.type() == SCEPTRE_OF_LYF ? 1 : 0;
            case INFLUENCE -> level += neckJunk == MOONSTONE ? 1 : 0;
            default -> { }
        }
        return level;
    }

    /** F303 without the item modifiers, and with or without temporary experience. */
    int baseLevel(int skill, boolean temporary) {
        long exp = experience[skill] + (temporary ? temporaryExperience[skill] : 0);
        if (skill >= BASE_SKILLS.size()) {
            int base = (skill - BASE_SKILLS.size()) / 4;
            exp = (exp + experience[base] + (temporary ? temporaryExperience[base] : 0)) / 2;
        }
        int level = 1;
        while (exp >= 500) {
            exp >>= 1;
            level++;
        }
        return level;
    }

    private static final int FIRESTAFF = 7;
    private static final int FIRESTAFF_COMPLETE = 45;
    private static final int SCEPTRE_OF_LYF = 42;
    private static final int GEM_OF_AGES = 37;
    private static final int EKKHARD_CROSS = 38;
    private static final int MOONSTONE = 39;
    private static final int PENDANT_FERAL = 41;

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
        if (godMode) {
            return 0;
        }
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
        if (godMode && food < this.food) {
            return;
        }
        this.food = Math.max(MIN_FOOD, Math.min(food, MAX_FOOD));
    }

    void setWater(int water) {
        if (godMode && water < this.water) {
            return;
        }
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
        if (godMode && amount > 0) {
            return 0;
        }
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
        if (godMode && mana < this.mana) {
            return;
        }
        this.mana = mana;
    }

    void addHealth(int amount) {
        health = Math.min(health + amount, maxHealth);
    }

    void addStamina(int amount) {
        stamina = Math.min(stamina + amount, maxStamina);
    }

    /** The options' deep sleep: health, stamina and mana back to their maximum (mana a potion raised above it stays). */
    void refresh() {
        health = Math.max(health, maxHealth);
        stamina = Math.max(stamina, maxStamina);
        mana = Math.max(mana, maxMana);
    }

    void setStat(Stat s, int value) {
        stats[s.ordinal()] = value;
    }

    /** The skill numbers, for {@link #skillLevel}: 4 base skills, then 4 hidden ones under each. */
    public static final int FIGHTER = 0;
    public static final int NINJA = 1;
    public static final int PRIEST = 2;
    public static final int WIZARD = 3;
    public static final int SWING = 4;
    public static final int THRUST = 5;
    public static final int CLUB = 6;
    public static final int FIGHT = 9;
    public static final int THROW = 10;
    public static final int SHOOT = 11;
    public static final int HEAL = 13;
    public static final int INFLUENCE = 14;
    public static final int DEFEND = 15;
    /** DM's hidden parry skill (a fighter skill): parrying lessens creatures' blows. */
    public static final int PARRY = 7;

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

    // ---- combat (Sprint 16) ------------------------------------------------------

    /** DM's temporary experience per skill: earned with experience, fading by 1 every 64 ticks. */
    private final int[] temporaryExperience = new int[BASE_SKILLS.size() + HIDDEN_SKILL_COUNT];

    /** The game menu's god mode (not in DM): health, stamina, mana, food and water never go down, and no wounds. Set by {@link Party#setGodMode}. */
    private boolean godMode;

    void setGodMode(boolean on) {
        godMode = on;
    }

    /** Whether the party is asleep (DM's G300, which F303, F310 and F313 read); set by {@link Party#sleep}. */
    private boolean asleep;

    void setAsleep(boolean asleep) {
        this.asleep = asleep;
    }

    boolean asleep() {
        return asleep;
    }

    /** The longest name and title DM's rename panel takes (F281). */
    public static final int MAX_NAME = 7;
    public static final int MAX_TITLE = 19;

    /**
     * DM's reincarnation (F280 with F281): the champion takes a new name and
     * title, forgets every skill (experience and temporary experience 0, so
     * every level is 1), and gains 12 statistic points, each going to a
     * random statistic (luck included), current and maximum alike.
     */
    void reincarnate(String newName, String newTitle, Random random) {
        name = newName;
        title = newTitle;
        Arrays.fill(experience, 0);
        Arrays.fill(temporaryExperience, 0);
        for (int i = 0; i < 12; i++) {
            int s = random.nextInt(stats.length);
            stats[s]++;
            maxStats[s]++;
        }
    }

    long experience(int skill) {
        return experience[skill];
    }

    void addExperience(int skill, long amount) {
        experience[skill] += amount;
    }

    int temporaryExperience(int skill) {
        return temporaryExperience[skill];
    }

    void addTemporaryExperience(int skill, int amount) {
        temporaryExperience[skill] += amount;
    }

    /** F331: temporary experience fades by 1 in every skill. */
    void fadeTemporaryExperience() {
        for (int s = 0; s < temporaryExperience.length; s++) {
            if (temporaryExperience[s] > 0) {
                temporaryExperience[s]--;
            }
        }
    }

    void raiseMaxStat(Stat s, int amount) {
        maxStats[s.ordinal()] += amount;
    }

    void raiseMaxHealth(int amount) {
        maxHealth = Math.min(999, maxHealth + amount);
    }

    /** In DM's units (10 per point shown). */
    void raiseMaxStamina(int amount) {
        maxStamina = Math.min(9999, maxStamina + amount);
    }

    void raiseMaxMana(int amount) {
        maxMana = Math.min(900, maxMana + amount);
    }

    /** The way the champion faces (DM's champion direction): the party's, unless they turned to an attacker. */
    private Direction facing = Direction.NORTH;

    public Direction facing() {
        return facing;
    }

    void face(Direction d) {
        facing = d;
    }

    /** Defense from the action the champion is recovering from (DM's action defense). */
    private int actionDefense;
    /** The action last performed, until the champion can act again (DM's action index), or {@link Actions#NONE}. */
    private int actionIndex = Actions.NONE;
    /** The game tick the champion can act again, or -1 if they can act now. */
    private long enabledAt = -1;

    public int actionDefense() {
        return actionDefense;
    }

    void addActionDefense(int amount) {
        actionDefense += amount;
    }

    int actionIndex() {
        return actionIndex;
    }

    void setActionIndex(int action) {
        actionIndex = action;
    }

    /** Whether the champion is still recovering from an action (DM's "disable action"). */
    public boolean actionDisabled() {
        return enabledAt >= 0;
    }

    long enabledAt() {
        return enabledAt;
    }

    void setEnabledAt(long tick) {
        enabledAt = tick;
    }

    /** After a throw, the action hand takes the next weapon from the quiver when the champion can act again (F259). */
    private boolean refillActionHand;

    boolean refillActionHand() {
        return refillActionHand;
    }

    void setRefillActionHand(boolean refill) {
        refillActionHand = refill;
    }

    // ---- wounds and poison (Sprint 15) ----------------------------------------

    /** DM's wound bits, one per body part, in DM's slot order. */
    public static final int WOUND_READY_HAND = 0x01;
    public static final int WOUND_ACTION_HAND = 0x02;
    public static final int WOUND_HEAD = 0x04;
    public static final int WOUND_TORSO = 0x08;
    public static final int WOUND_LEGS = 0x10;
    public static final int WOUND_FEET = 0x20;

    /** The body parts DM can wound, in the order of its wound bits (DM's slots 0-5). */
    public static final List<Slot> WOUND_SLOTS = List.of(
            Slot.READY_HAND, Slot.ACTION_HAND, Slot.HEAD, Slot.TORSO, Slot.LEGS, Slot.FEET);

    private int wounds;

    /** DM's wound bits ({@link #WOUND_HEAD} and so on). */
    public int wounds() {
        return wounds;
    }

    /** Whether the body part {@code slot} (hands, head, torso, legs or feet) is wounded. */
    public boolean isWounded(Slot slot) {
        int bit = WOUND_SLOTS.indexOf(slot);
        return bit >= 0 && (wounds & (1 << bit)) != 0;
    }

    void addWounds(int bits) {
        if (godMode) {
            return;
        }
        wounds |= bits & 0x3F;
    }

    void setWounds(int bits) {
        wounds = bits & 0x3F;
    }

    /**
     * One link of a poisoning (DM's poison event): at {@code due} the
     * champion loses attack / 64 health (at least 1), and unless that was
     * the last, the next link follows 36 ticks later with attack - 1.
     */
    record Poison(int attack, long due) implements Serializable {
    }

    private final ArrayList<Poison> poisons = new ArrayList<>();

    /** Whether poison is still working on the champion (DM's poison event count). */
    public boolean poisoned() {
        return !poisons.isEmpty();
    }

    ArrayList<Poison> poisons() {
        return poisons;
    }

    /** Antivenin (DM's F323): every poisoning stops. */
    void unpoison() {
        poisons.clear();
    }

    /** DM's minimum for each statistic: 10 for luck, 30 for the rest. */
    static int minStat(Stat s) {
        return s == Stat.LUCK ? 10 : 30;
    }

    /**
     * DM's F311: luck decides. Half the time the champion is lucky outright
     * when a roll of 100 beats {@code percentage}; otherwise a roll of their
     * luck must beat it, and luck then drops 2 on success or rises 2 on failure.
     */
    boolean isLucky(int percentage, Random random) {
        if (random.nextInt(2) != 0 && random.nextInt(100) > percentage) {
            return true;
        }
        int luck = stat(Stat.LUCK);
        boolean lucky = luck > 0 && random.nextInt(luck) > percentage;
        stats[Stat.LUCK.ordinal()] = Math.max(minStat(Stat.LUCK), Math.min(luck + (lucky ? -2 : 2),
                maxStat(Stat.LUCK)));
        return lucky;
    }

    /** DM's F310 (party awake): dexterity plus a little luck, less when heavily laden, 1-100. */
    int dexterity(int load, Random random) {
        int dexterity = random.nextInt(8) + stat(Stat.DEXTERITY);
        dexterity -= (int) ((long) (dexterity >> 1) * load / maxLoad());
        if (asleep) {
            dexterity >>= 1;
        }
        int low = 1 + random.nextInt(8);
        int high = 100 - random.nextInt(8);
        return Math.max(low, Math.min(dexterity >> 1, high));
    }

    /**
     * DM's F312: the strength behind what {@code hand} holds, 0-100: strength
     * plus a little luck, adjusted by the item's weight against what the
     * champion can carry, plus a weapon's own strength and twice the skill
     * that wields it (swing for swords and axes, throw for other hand
     * weapons, shoot for bows and slings). Less when tired, halved by a
     * wounded hand.
     */
    int strength(Slot hand, Random random) {
        int strength = random.nextInt(16) + stat(Stat.STRENGTH);
        Item item = items.get(hand);
        int weight = item == null ? 0 : item.weight();
        int sixteenth = maxLoad() >> 4;
        if (weight <= sixteenth) {
            strength += weight - 12;
        } else {
            int threshold = sixteenth + ((sixteenth - 12) >> 1);
            strength += weight <= threshold ? (weight - sixteenth) >> 1 : -((weight - threshold) << 1);
        }
        int weaponClass = ItemCatalog.weaponClass(item);
        if (weaponClass >= 0) {
            strength += ItemCatalog.weaponStrength(item);
            int level = 0;
            if (weaponClass == ItemCatalog.CLASS_SWING_WEAPON || weaponClass == ItemCatalog.CLASS_DAGGER_AND_AXES) {
                level = skillLevel(SWING);
            }
            if (weaponClass != ItemCatalog.CLASS_SWING_WEAPON && weaponClass < ItemCatalog.CLASS_FIRST_BOW) {
                level += skillLevel(THROW);
            }
            if (weaponClass >= ItemCatalog.CLASS_FIRST_BOW && weaponClass < ItemCatalog.CLASS_FIRST_MAGIC_WEAPON) {
                level += skillLevel(SHOOT);
            }
            strength += level << 1;
        }
        strength = staminaAdjusted(strength);
        if (isWounded(hand)) {
            strength >>= 1;
        }
        return Math.max(0, Math.min(strength >> 1, 100));
    }

    /** The hardest blow a creature dealt since the last game tick, and the way the champion turns to face it. */
    private int maxDamageReceived;
    private Direction maxDamageDirection = Direction.NORTH;

    /** F207: a creature's blow of {@code damage} from {@code from}; the champion turns to the hardest one. */
    void receivedBlow(int damage, Direction from) {
        if (damage > maxDamageReceived) {
            maxDamageReceived = damage;
            maxDamageDirection = from;
        }
    }

    int maxDamageReceived() {
        return maxDamageReceived;
    }

    Direction maxDamageDirection() {
        return maxDamageDirection;
    }

    void clearMaxDamageReceived() {
        maxDamageReceived = 0;
    }

    /**
     * DM's F307: an attack lessened by statistic {@code s} (vitality against
     * poison): scaled by (170 - s) / 128, or an eighth when s is above 154.
     * (On the Atari ST a compiler bug, ReDMCSB's BUG0_41, read s as 0; this
     * follows the code as written, like ScummVM's PC engine.)
     */
    int statisticAdjustedAttack(Stat s, int attack) {
        int factor = 170 - stat(s);
        return factor < 16 ? attack >> 3 : attack * factor >> 7;
    }
}
