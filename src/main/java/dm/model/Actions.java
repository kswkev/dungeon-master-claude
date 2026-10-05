package dm.model;

/**
 * DM's champion actions: the 44 actions a hand item offers in the action
 * menu (G490 names) with their tables from ReDMCSB's MENUS.C (via ScummVM),
 * and the 44 action sets an item's G237 entry points to (F389's table).
 */
public final class Actions {

    private Actions() {
    }

    public static final int NONE = 255;

    public static final int BLOCK = 1;
    public static final int CHOP = 2;
    public static final int BLOW_HORN = 4;
    public static final int FLIP = 5;
    public static final int PUNCH = 6;
    public static final int KICK = 7;
    public static final int WAR_CRY = 8;
    public static final int STAB_9 = 9;
    public static final int CLIMB_DOWN = 10;
    public static final int FREEZE_LIFE = 11;
    public static final int HIT = 12;
    public static final int SWING = 13;
    public static final int STAB_14 = 14;
    public static final int THRUST = 15;
    public static final int JAB = 16;
    public static final int PARRY = 17;
    public static final int HACK = 18;
    public static final int BERZERK = 19;
    public static final int FIREBALL = 20;
    public static final int DISPELL = 21;
    public static final int CONFUSE = 22;
    public static final int LIGHTNING = 23;
    public static final int DISRUPT = 24;
    public static final int MELEE = 25;
    public static final int INVOKE = 27;
    public static final int SLASH = 28;
    public static final int CLEAVE = 29;
    public static final int BASH = 30;
    public static final int STUN = 31;
    public static final int SHOOT = 32;
    public static final int SPELLSHIELD = 33;
    public static final int FIRESHIELD = 34;
    public static final int FLUXCAGE = 35;
    public static final int HEAL = 36;
    public static final int CALM = 37;
    public static final int LIGHT = 38;
    public static final int WINDOW = 39;
    public static final int SPIT = 40;
    public static final int BRANDISH = 41;
    public static final int THROW = 42;
    public static final int FUSE = 43;

    /** G490. */
    private static final String[] NAMES = {
            "N", "BLOCK", "CHOP", "X", "BLOW HORN", "FLIP", "PUNCH", "KICK", "WAR CRY", "STAB", "CLIMB DOWN",
            "FREEZE LIFE", "HIT", "SWING", "STAB", "THRUST", "JAB", "PARRY", "HACK", "BERZERK", "FIREBALL",
            "DISPELL", "CONFUSE", "LIGHTNING", "DISRUPT", "MELEE", "X", "INVOKE", "SLASH", "CLEAVE", "BASH",
            "STUN", "SHOOT", "SPELLSHIELD", "FIRESHIELD", "FLUXCAGE", "HEAL", "CALM", "LIGHT", "WINDOW",
            "SPIT", "BRANDISH", "THROW", "FUSE"};

    /** G496: the skill each action trains (DM's skill numbers, as {@link Champion#skillLevel}). */
    private static final int[] SKILL = {
            0, 7, 6, 0, 14, 12, 9, 9, 7, 9, 8, 14, 9, 4, 5, 5, 5, 7, 4, 4, 16, 17,
            14, 17, 17, 6, 8, 3, 4, 4, 6, 6, 11, 15, 15, 3, 13, 14, 17, 18, 16, 14, 10, 3};

    /** Ticks the champion can't act afterwards (MENUS.C's action disabled ticks). */
    private static final int[] DISABLED_TICKS = {
            0, 6, 8, 0, 6, 3, 1, 5, 3, 5, 35, 20, 4, 6, 10, 16, 2, 18, 8, 30, 42, 31,
            10, 38, 9, 20, 10, 16, 4, 12, 20, 7, 14, 30, 35, 2, 19, 9, 10, 15, 22, 10, 0, 2};

    /** Stamina the action costs, plus 0 or 1 (F407's table). */
    private static final int[] STAMINA = {
            0, 4, 10, 0, 1, 0, 1, 3, 1, 3, 40, 3, 3, 2, 4, 17, 3, 1, 6, 40, 5, 2,
            2, 4, 5, 25, 1, 2, 2, 10, 9, 2, 3, 1, 2, 6, 1, 1, 3, 2, 3, 2, 0, 2};

    /** G497: experience the action earns in its skill. */
    private static final int[] EXPERIENCE = {
            0, 8, 10, 0, 0, 0, 8, 13, 7, 15, 15, 22, 10, 6, 12, 19, 11, 17, 9, 40, 35, 25,
            0, 30, 10, 24, 0, 25, 9, 12, 11, 10, 20, 20, 20, 12, 0, 0, 20, 30, 25, 0, 5, 1};

    /** G495: defense added while the champion is recovering from the action. */
    private static final int[] DEFENSE = {
            0, 36, 0, 0, -4, -10, -10, -5, 4, -20, -15, -10, 16, 5, -15, -17, -5, 29, 10, -10, -7, -7,
            -7, -7, -7, -5, -15, -9, 4, 0, 0, 5, -15, -7, -7, 8, -20, -5, 0, -15, -7, -4, 0, 8};

    /** A melee action's chance to hit (F402's table). */
    private static final int[] HIT_PROBABILITY = {
            0, 22, 48, 0, 0, 0, 38, 28, 0, 30, 0, 0, 20, 32, 42, 57, 70, 18, 27, 46, 0, 0,
            0, 0, 46, 64, 0, 0, 26, 40, 32, 50, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    /** A melee action's damage factor, in 32nds of the champion's strength (F402's table). */
    private static final int[] DAMAGE_FACTOR = {
            0, 15, 48, 0, 0, 0, 32, 48, 0, 48, 0, 0, 20, 16, 60, 66, 8, 8, 25, 96, 0, 0,
            0, 0, 55, 60, 0, 0, 16, 48, 50, 16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    /**
     * Each action set's three actions and the minimum skill levels of
     * the second and third (bit 7: needs a charge). Set 2 (punch, kick, war
     * cry) is the empty hand's.
     */
    private static final int[][] SETS = {
            {255, 255, 255, 0x00, 0x00}, {27, 43, 35, 0x00, 0x00}, {6, 7, 8, 0x00, 0x00}, {0, 0, 0, 0x00, 0x00},
            {0, 0, 0, 0x00, 0x00}, {13, 255, 255, 0x00, 0x00}, {13, 20, 255, 0x87, 0x00}, {13, 23, 255, 0x83, 0x00},
            {28, 41, 22, 0x02, 0x83}, {16, 2, 23, 0x00, 0x84}, {2, 25, 20, 0x02, 0x86}, {17, 41, 34, 0x03, 0x05},
            {42, 9, 28, 0x00, 0x02}, {13, 17, 2, 0x02, 0x03}, {16, 17, 15, 0x01, 0x05}, {28, 17, 25, 0x01, 0x05},
            {2, 25, 15, 0x05, 0x06}, {9, 2, 29, 0x02, 0x05}, {16, 29, 24, 0x02, 0x04}, {13, 15, 19, 0x05, 0x07},
            {13, 2, 25, 0x00, 0x05}, {2, 29, 19, 0x03, 0x08}, {13, 30, 31, 0x02, 0x04}, {13, 31, 25, 0x03, 0x06},
            {42, 30, 255, 0x00, 0x00}, {0, 0, 0, 0x00, 0x00}, {42, 9, 255, 0x00, 0x00}, {32, 255, 255, 0x00, 0x00},
            {37, 33, 36, 0x82, 0x03}, {37, 33, 34, 0x83, 0x84}, {17, 38, 21, 0x80, 0x83}, {13, 21, 34, 0x83, 0x84},
            {36, 37, 41, 0x02, 0x03}, {13, 23, 39, 0x82, 0x84}, {13, 17, 40, 0x00, 0x83}, {17, 36, 38, 0x03, 0x84},
            {4, 255, 255, 0x00, 0x00}, {5, 255, 255, 0x00, 0x00}, {11, 255, 255, 0x00, 0x00},
            {10, 255, 255, 0x00, 0x00}, {42, 9, 255, 0x00, 0x00}, {1, 12, 255, 0x02, 0x00}, {42, 255, 255, 0x00, 0x00},
            {6, 11, 255, 0x80, 0x00}};

    /** The empty hand's action set: punch, kick, war cry. */
    public static final int EMPTY_HAND_SET = 2;

    public static String name(int action) {
        return action == NONE ? "" : NAMES[action];
    }

    public static int skill(int action) {
        return SKILL[action];
    }

    public static int disabledTicks(int action) {
        return DISABLED_TICKS[action];
    }

    public static int stamina(int action) {
        return STAMINA[action];
    }

    public static int experience(int action) {
        return EXPERIENCE[action];
    }

    public static int defense(int action) {
        return DEFENSE[action];
    }

    public static int hitProbability(int action) {
        return HIT_PROBABILITY[action];
    }

    public static int damageFactor(int action) {
        return DAMAGE_FACTOR[action];
    }

    /** Action set {@code set}'s {@code i}-th action (0-2), or {@link #NONE}. */
    static int setAction(int set, int i) {
        return SETS[set][i];
    }

    /** The minimum skill level (bits 0-6) and needs-a-charge flag (bit 7) of action set {@code set}'s action {@code i} (1-2). */
    static int setProperty(int set, int i) {
        return SETS[set][2 + i];
    }

    /** The actions that are spells or item magic (F0407 spends mana or charges on them). */
    public static boolean isMagic(int action) {
        return switch (action) {
            case FIREBALL, DISPELL, LIGHTNING, INVOKE, SPELLSHIELD, FIRESHIELD, FLUXCAGE, HEAL, LIGHT, WINDOW,
                 SPIT, FUSE, FREEZE_LIFE, CONFUSE -> true;
            default -> false;
        };
    }
}
