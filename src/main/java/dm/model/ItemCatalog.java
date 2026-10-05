package dm.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Names and wear slots for each object type number in DUNGEON.DAT.
 *
 * This is reference data from the game itself rather than the dungeon file.
 * Names must match GRAPHICS.DAT's icon-indexed object name list, which is how
 * the UI finds each item's icon (see {@link Item#nameVariant()}).
 */
public final class ItemCatalog {

    private static final String[] WEAPONS = {
            "EYE OF TIME", "STORMRING", "TORCH", "FLAMITT", "STAFF OF CLAWS", "BOLT BLADE", "FURY",
            "THE FIRESTAFF", "DAGGER", "FALCHION", "SWORD", "RAPIER", "SABRE", "SAMURAI SWORD", "DELTA",
            "DIAMOND EDGE", "VORPAL BLADE", "THE INQUISITOR", "AXE", "HARDCLEAVE", "MACE", "MACE OF ORDER",
            "MORNINGSTAR", "CLUB", "STONE CLUB", "BOW", "CROSSBOW", "ARROW", "SLAYER", "SLING", "ROCK",
            "POISON DART", "THROWING STAR", "STICK", "STAFF", "WAND", "TEOWAND", "YEW STAFF",
            "STAFF OF MANAR", "SNAKE STAFF", "THE CONDUIT", "DRAGON SPIT", "SCEPTRE OF LYF", "HORN OF FEAR",
            "SPEEDBOW", "THE FIRESTAFF"};

    private static final Set<Integer> MISSILES = Set.of(27, 28, 30, 31, 32);

    private static final String[] ARMOUR = {
            "CAPE", "CLOAK OF NIGHT", "BARBARIAN HIDE", "SANDALS", "LEATHER BOOTS", "ROBE", "ROBE",
            "FINE ROBE", "FINE ROBE", "KIRTLE", "SILK SHIRT", "TABARD", "GUNNA", "ELVEN DOUBLET",
            "ELVEN HUKE", "ELVEN BOOTS", "LEATHER JERKIN", "LEATHER PANTS", "SUEDE BOOTS", "BLUE PANTS",
            "TUNIC", "GHI", "GHI TROUSERS", "CALISTA", "CROWN OF NERRA", "BEZERKER HELM", "HELMET",
            "BASINET", "BUCKLER", "HIDE SHIELD", "WOODEN SHIELD", "SMALL SHIELD", "MAIL AKETON", "LEG MAIL",
            "MITHRAL AKETON", "MITHRAL MAIL", "CASQUE 'N COIF", "HOSEN", "ARMET", "TORSO PLATE", "LEG PLATE",
            "FOOT PLATE", "LARGE SHIELD", "HELM OF LYTE", "PLATE OF LYTE", "POLEYN OF LYTE",
            "GREAVE OF LYTE", "SHIELD OF LYTE", "HELM OF DARC", "PLATE OF DARC", "POLEYN OF DARC",
            "GREAVE OF DARC", "SHIELD OF DARC", "DEXHELM", "FLAMEBAIN", "POWERTOWERS", "BOOTS OF SPEED",
            "HALTER"};

    /** Body slot per armour type; null marks a shield (carried in a hand). */
    private static final Slot[] ARMOUR_SLOTS = {
            Slot.NECK, Slot.NECK, Slot.TORSO, Slot.FEET, Slot.FEET, Slot.TORSO, Slot.LEGS,
            Slot.TORSO, Slot.LEGS, Slot.TORSO, Slot.TORSO, Slot.LEGS, Slot.LEGS, Slot.TORSO,
            Slot.LEGS, Slot.FEET, Slot.TORSO, Slot.LEGS, Slot.FEET, Slot.LEGS,
            Slot.TORSO, Slot.TORSO, Slot.LEGS, Slot.HEAD, Slot.HEAD, Slot.HEAD, Slot.HEAD,
            Slot.HEAD, null, null, null, null, Slot.TORSO, Slot.LEGS,
            Slot.TORSO, Slot.LEGS, Slot.HEAD, Slot.FEET, Slot.HEAD, Slot.TORSO, Slot.LEGS,
            Slot.FEET, null, Slot.HEAD, Slot.TORSO, Slot.LEGS,
            Slot.FEET, null, Slot.HEAD, Slot.TORSO, Slot.LEGS,
            Slot.FEET, null, Slot.HEAD, Slot.TORSO, Slot.LEGS, Slot.FEET,
            Slot.TORSO};

    private static final String[] POTIONS = {
            "MON POTION", "UM POTION", "DES POTION", "VEN POTION", "SAR POTION", "ZO POTION", "ROS POTION",
            "KU POTION", "DANE POTION", "NETA POTION", "BRO POTION", "MA POTION", "YA POTION", "EE POTION",
            "VI POTION", "WATER FLASK", "KATH BOMB", "PEW BOMB", "RA BOMB", "FUL BOMB", "EMPTY FLASK"};

    private static final String[] JUNK = {
            "COMPASS", "WATERSKIN", "JEWEL SYMAL", "ILLUMULET", "ASHES", "BONES", "COPPER COIN",
            "SILVER COIN", "GOLD COIN", "IRON KEY", "KEY OF B", "SOLID KEY", "SQUARE KEY", "TOURQUOISE KEY",
            "CROSS KEY", "ONYX KEY", "SKELETON KEY", "GOLD KEY", "WINGED KEY", "TOPAZ KEY", "SAPPHIRE KEY",
            "EMERALD KEY", "RUBY KEY", "RA KEY", "MASTER KEY", "BOULDER", "BLUE GEM", "ORANGE GEM",
            "GREEN GEM", "APPLE", "CORN", "BREAD", "CHEESE", "SCREAMER SLICE", "WORM ROUND", "DRUMSTICK",
            "DRAGON STEAK", "GEM OF AGES", "EKKHARD CROSS", "MOONSTONE", "THE HELLION", "PENDANT FERAL",
            "MAGICAL BOX", "MAGICAL BOX", "MIRROR OF DAWN", "ROPE", "RABBIT'S FOOT", "CORBAMITE", "CHOKER",
            "LOCK PICKS", "MAGNIFIER", "ZOKATHRA SPELL", "BONES"};

    /** The junk ZO KATH RA makes (DM's C51_JUNK_ZOKATHRA). */
    public static final int ZOKATHRA = 51;

    private static final Set<Integer> NECK_JUNK = Set.of(2, 3, 37, 38, 39, 40, 41, 48);

    /**
     * Small junk that fits a pouch: compass, amulets and jewels, coins, keys,
     * gems, magical boxes, rabbit's foot, corbamite, lock picks and magnifier.
     */
    private static final Set<Integer> POUCH_JUNK = Set.of(
            0, 2, 3, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24,
            26, 27, 28, 37, 38, 39, 40, 41, 42, 43, 46, 47, 48, 49, 50);

    /**
     * The GRAPHICS.DAT picture (498-583) of each object lying in the dungeon,
     * by name. DM keeps this mapping in its program, not its data files, so
     * it was rebuilt by matching the floor pictures against the inventory icons.
     */
    private static final Map<String, Integer> FLOOR_GRAPHICS = new HashMap<>();

    private static void floor(int graphic, String... names) {
        for (String n : names) {
            FLOOR_GRAPHICS.put(n, graphic);
        }
    }

    static {
        floor(498, "CHEST");
        floor(500, "SCROLL");
        floor(501, "MON POTION", "UM POTION", "DES POTION", "SAR POTION", "ZO POTION", "ROS POTION",
                "KU POTION", "DANE POTION", "NETA POTION", "BRO POTION", "MA POTION", "YA POTION", "EE POTION",
                "VI POTION", "WATER FLASK");
        floor(502, "BLUE GEM");
        floor(503, "GOLD COIN", "COPPER COIN");
        floor(504, "DRUMSTICK");
        floor(505, "WATERSKIN", "WATER");
        floor(506, "ELVEN DOUBLET", "ELVEN HUKE");
        floor(507, "LEATHER BOOTS");
        floor(508, "BEZERKER HELM", "HELMET", "BASINET");
        floor(509, "HIDE SHIELD", "WOODEN SHIELD");
        floor(510, "STAFF", "YEW STAFF");
        floor(511, "BOLT BLADE", "FURY", "FALCHION", "SWORD", "RAPIER", "SABRE", "SAMURAI SWORD", "DELTA",
                "DIAMOND EDGE", "VORPAL BLADE", "THE INQUISITOR");
        floor(512, "AXE", "HARDCLEAVE");
        floor(513, "BOW", "SPEEDBOW");
        floor(514, "EYE OF TIME", "STORMRING", "JEWEL SYMAL", "GEM OF AGES", "EKKHARD CROSS");
        floor(515, "ARROW");
        floor(516, "DAGGER");
        floor(517, "IRON KEY", "KEY OF B", "SOLID KEY", "SQUARE KEY", "TOURQUOISE KEY", "CROSS KEY",
                "ONYX KEY", "SKELETON KEY");
        floor(518, "ROBE", "FINE ROBE", "SILK SHIRT", "TABARD");
        floor(519, "MAIL AKETON", "LEG MAIL", "MITHRAL AKETON", "MITHRAL MAIL");
        floor(520, "MACE", "MACE OF ORDER");
        floor(521, "TORSO PLATE", "PLATE OF LYTE", "PLATE OF DARC", "FLAMEBAIN");
        floor(522, "CAPE", "CLOAK OF NIGHT", "BARBARIAN HIDE", "LEATHER JERKIN", "LEATHER PANTS");
        floor(523, "GHI", "GHI TROUSERS");
        floor(524, "BREAD");
        floor(525, "CHEESE");
        floor(526, "APPLE");
        floor(527, "CORN");
        floor(528, "SANDALS", "SUEDE BOOTS");
        floor(529, "SCEPTRE OF LYF");
        floor(530, "WAND", "TEOWAND");
        floor(531, "DRAGON SPIT");
        floor(532, "MORNINGSTAR");
        floor(533, "COMPASS");
        floor(534, "TORCH");
        floor(536, "FLAMITT");
        floor(537, "ZOKATHRA SPELL");
        floor(538, "THE FIRESTAFF");
        floor(539, "ASHES");
        floor(540, "BONES");
        floor(541, "STAFF OF CLAWS", "SNAKE STAFF");
        floor(542, "CLUB");
        floor(543, "STONE CLUB");
        floor(544, "CROSSBOW");
        floor(545, "SLAYER");
        floor(546, "ROCK");
        floor(547, "POISON DART");
        floor(548, "THROWING STAR");
        floor(549, "STICK");
        floor(550, "STAFF OF MANAR", "THE CONDUIT");
        floor(551, "CASQUE 'N COIF", "ARMET", "HELM OF LYTE", "HELM OF DARC", "DEXHELM");
        floor(552, "CALISTA", "CROWN OF NERRA");
        floor(553, "BUCKLER", "SMALL SHIELD", "LARGE SHIELD", "SHIELD OF LYTE", "SHIELD OF DARC");
        floor(554, "SLING");
        floor(555, "HOSEN", "LEG PLATE", "FOOT PLATE", "POLEYN OF LYTE", "GREAVE OF LYTE", "POLEYN OF DARC",
                "GREAVE OF DARC", "POWERTOWERS");
        floor(556, "ELVEN BOOTS");
        floor(557, "ILLUMULET", "MOONSTONE");
        floor(558, "THE HELLION", "PENDANT FERAL");
        floor(559, "ORANGE GEM");
        floor(560, "GREEN GEM");
        floor(561, "GOLD KEY", "WINGED KEY", "TOPAZ KEY", "SAPPHIRE KEY", "EMERALD KEY", "RUBY KEY", "RA KEY",
                "MASTER KEY");
        floor(563, "MIRROR OF DAWN");
        floor(564, "HORN OF FEAR");
        floor(565, "DRAGON STEAK");
        floor(566, "VEN POTION");
        floor(567, "KATH BOMB", "PEW BOMB", "RA BOMB", "FUL BOMB");
        floor(568, "KIRTLE", "GUNNA", "BLUE PANTS", "TUNIC");
        floor(569, "WORM ROUND");
        floor(570, "SCREAMER SLICE");
        floor(571, "ROPE");
        floor(572, "RABBIT'S FOOT");
        floor(573, "CORBAMITE");
        floor(574, "CHOKER");
        floor(575, "BOULDER");
        floor(576, "LOCK PICKS");
        floor(577, "MAGNIFIER");
        floor(578, "MAGICAL BOX");
        floor(579, "EMPTY FLASK");
        floor(580, "BOOTS OF SPEED");
        floor(582, "SILVER COIN");
        floor(583, "HALTER");
    }

    private ItemCatalog() {
    }

    /**
     * The GRAPHICS.DAT picture of an item lying in the dungeon, or -1 if
     * unknown. Variants with their own look: the green magical box and the
     * finished Firestaff.
     */
    public static int floorGraphic(Item item) {
        if (item.category() == Item.Category.JUNK && item.type() == 43) {
            return 562; // green magical box
        }
        if (item.category() == Item.Category.WEAPON && item.type() == 45) {
            return 581; // the Firestaff with its gem
        }
        return FLOOR_GRAPHICS.getOrDefault(item.name(), -1);
    }

    /** Builds the item for a category and type number; unknown numbers get a generic name. */
    public static Item item(Item.Category category, int type) {
        return item(category, type, 0);
    }

    /**
     * Builds an item with {@code charges} charges. A waterskin that holds
     * water is named WATER, which is how DM's icon list names the full one.
     */
    public static Item item(Item.Category category, int type, int charges) {
        return switch (category) {
            case WEAPON -> new Item(category, type, name(WEAPONS, type), type == 45 ? 1 : 0, null, charges);
            case ARMOUR -> new Item(category, type, name(ARMOUR, type), armourVariant(type),
                    type < ARMOUR_SLOTS.length ? ARMOUR_SLOTS[type] : null, charges);
            case POTION -> new Item(category, type, name(POTIONS, type), 0, null, charges);
            case JUNK -> new Item(category, type, type == WATERSKIN && charges > 0 ? "WATER" : name(JUNK, type),
                    junkVariant(type), NECK_JUNK.contains(type) ? Slot.NECK : null, charges);
            case SCROLL -> new Item(category, type, "SCROLL", 0, null, charges);
            case CONTAINER -> new Item(category, type, "CHEST", 0, null, charges);
        };
    }

    // ---- DM's item data (graphics.dat item 559 on the ST; the PC keeps it in the program) ----

    /** Junk type numbers and potion types that eating and drinking care about. */
    public static final int WATERSKIN = 1;
    public static final int GREEN_GEM = 28;
    public static final int FIRST_FOOD = 29;   // APPLE
    public static final int LAST_FOOD = 36;    // DRAGON STEAK
    public static final int WATER_FLASK = 15;
    public static final int EMPTY_FLASK = 20;

    /** Food value of APPLE .. DRAGON STEAK (G242_ai_Graphic559_FoodAmounts, as in ScummVM's DM engine). */
    private static final int[] FOOD_AMOUNTS = {500, 600, 650, 820, 550, 350, 990, 1400};

    /** Weights in tenths of a kilogram (G238/G239/G241, from ScummVM's DM engine). */
    private static final int[] WEAPON_WEIGHTS = {
            1, 1, 11, 12, 9, 30, 47, 24, 5, 33, 32, 26, 35, 36, 33, 37, 30, 39, 43, 65, 31, 41, 50, 36, 110,
            10, 28, 2, 2, 19, 10, 3, 1, 8, 26, 1, 2, 35, 29, 21, 33, 8, 18, 8, 30, 36};
    private static final int[] ARMOUR_WEIGHTS = {
            3, 4, 3, 6, 16, 4, 4, 3, 3, 4, 2, 4, 5, 3, 3, 4, 6, 8, 14, 6, 5, 5, 5, 4, 6, 11, 14, 15, 11, 10,
            14, 21, 65, 53, 52, 41, 16, 16, 19, 120, 80, 28, 34, 17, 108, 72, 24, 30, 35, 141, 90, 31, 40, 14,
            57, 81, 3, 2};
    private static final int[] JUNK_WEIGHTS = {
            1, 3, 2, 2, 4, 15, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 81, 2, 3, 2, 4,
            4, 3, 8, 5, 11, 4, 6, 2, 3, 2, 2, 2, 6, 9, 3, 10, 1, 0, 1, 1, 2, 0, 8};

    // ---- combat data (Sprint 16) --------------------------------------------------

    /**
     * G238 weapon info per weapon type: class, strength, kinetic energy and
     * attributes (bits 0-7 the shoot attack). Classes: 0 swing weapons, 2
     * daggers and axes, 10 bow ammunition, 11 sling ammunition, 12 poison
     * darts, 16-31 bows, 32-47 slings, 112+ magic weapons.
     */
    private static final int[][] WEAPON_INFO = {
            {130, 2, 0, 0x2000}, {131, 2, 0, 0x2000}, {0, 8, 2, 0x2000}, {112, 10, 80, 0x2028},
            {129, 16, 7, 0x2000}, {113, 49, 110, 0x0942}, {0, 55, 20, 0x0900}, {255, 25, 10, 0x20FF},
            {2, 10, 19, 0x0200}, {0, 30, 8, 0x0900}, {0, 34, 10, 0x0900}, {0, 38, 10, 0x0900},
            {0, 42, 11, 0x0900}, {0, 46, 12, 0x0900}, {0, 50, 14, 0x0900}, {0, 62, 14, 0x0900},
            {0, 48, 13, 0x0000}, {0, 58, 15, 0x0900}, {2, 49, 33, 0x0300}, {2, 70, 44, 0x0300},
            {0, 32, 10, 0x2000}, {0, 42, 13, 0x2000}, {0, 60, 15, 0x2000}, {0, 19, 10, 0x2700},
            {0, 44, 22, 0x2600}, {20, 1, 50, 0x2032}, {30, 1, 180, 0x2078}, {10, 2, 10, 0x0100},
            {10, 2, 28, 0x0500}, {39, 5, 20, 0x2032}, {11, 6, 18, 0x2000}, {12, 7, 23, 0x0800},
            {1, 3, 19, 0x0A00}, {0, 4, 4, 0x2000}, {129, 12, 4, 0x2000}, {130, 0, 0, 0x2000},
            {140, 1, 20, 0x2000}, {128, 18, 6, 0x2000}, {159, 0, 4, 0x2000}, {131, 0, 3, 0x2000},
            {136, 0, 7, 0x2000}, {132, 3, 1, 0x2000}, {131, 9, 4, 0x2000}, {192, 1, 1, 0x2000},
            {26, 1, 220, 0x207D}, {255, 100, 50, 0x20FF}};

    public static final int CLASS_SWING_WEAPON = 0;
    public static final int CLASS_DAGGER_AND_AXES = 2;
    public static final int CLASS_BOW_AMMUNITION = 10;
    public static final int CLASS_SLING_AMMUNITION = 11;
    public static final int CLASS_POISON_DART = 12;
    public static final int CLASS_FIRST_BOW = 16;
    public static final int CLASS_LAST_BOW = 31;
    public static final int CLASS_FIRST_SLING = 32;
    public static final int CLASS_LAST_SLING = 47;
    public static final int CLASS_FIRST_MAGIC_WEAPON = 112;

    /** The weapon types DM's damage code singles out. */
    public static final int DIAMOND_EDGE = 15;
    public static final int VORPAL_BLADE = 16;
    public static final int HARDCLEAVE = 19;

    private static int[] weaponInfo(Item item) {
        return item != null && item.category() == Item.Category.WEAPON && item.type() < WEAPON_INFO.length
                ? WEAPON_INFO[item.type()] : null;
    }

    /** The weapon's class, or -1 if {@code item} isn't a weapon. */
    public static int weaponClass(Item item) {
        int[] info = weaponInfo(item);
        return info == null ? -1 : info[0];
    }

    /** The weapon's strength (added to the wielder's), or 0. */
    public static int weaponStrength(Item item) {
        int[] info = weaponInfo(item);
        return info == null ? 0 : info[1];
    }

    /** The weapon's kinetic energy (thrown or shot), or 0. */
    public static int weaponKineticEnergy(Item item) {
        int[] info = weaponInfo(item);
        return info == null ? 0 : info[2];
    }

    /** A bow's or sling's shoot attack (attribute bits 0-7), or 0. */
    public static int shootAttack(Item item) {
        int[] info = weaponInfo(item);
        return info == null ? 0 : info[3] & 0xFF;
    }

    /**
     * G237's action set index for each object info index (scroll 0,
     * container 1, potions 2-22, weapons 23-68, armour 69-126, junk 127-179).
     * 0 means the item has no actions.
     */
    private static final int[] ACTION_SETS = {
            0, 0, 0, 0, 0, 42, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 42, 0, 43, 7, 5, 6, 8, 9, 10, 11, 12, 13, 13, 14, 15, 15, 16, 17, 18,
            19, 20, 21, 22, 22, 23, 24, 24, 27, 27, 26, 26, 27, 42, 40, 42, 5, 5, 28, 29,
            30, 31, 32, 33, 5, 35, 36, 27, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 41, 41, 41,
            41, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 41, 0, 0, 0, 0, 41, 0, 0, 0,
            0, 41, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 37, 37, 37, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 38, 38, 0, 39, 0, 0, 0, 0, 0, 0, 0,
    };

    /** DM's object info index (F141): where an item's row sits in G237. */
    static int objectInfoIndex(Item item) {
        return switch (item.category()) {
            case SCROLL -> 0;
            case CONTAINER -> 1;
            case POTION -> 2 + item.type();
            case WEAPON -> 23 + item.type();
            case ARMOUR -> 69 + item.type();
            case JUNK -> 127 + item.type();
        };
    }

    /**
     * G237's "pouch, passes through doors" flag (allowed-slots bit 8), by
     * object info index: what a thrown item needs to fly through a portcullis.
     */
    private static final Set<Integer> PASSES_DOORS = Set.of(
            0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 31, 50, 51,
            53, 54, 55, 58, 59, 127, 128, 129, 130, 131, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143,
            144, 145, 146, 147, 148, 149, 150, 151, 153, 154, 155, 156, 157, 158, 159, 162, 163, 164, 165, 166,
            167, 168, 169, 170, 171, 173, 174, 175, 176, 177);

    private static final int FIRST_KEY = 9;
    private static final int LAST_KEY = 24;
    private static final int COPPER_COIN = 6;

    /** The junk that opens something: the copper, silver and gold coins, then every key. */
    public static List<Item> keysAndCoins() {
        List<Item> out = new ArrayList<>();
        for (int t = COPPER_COIN; t <= LAST_KEY; t++) {
            out.add(item(Item.Category.JUNK, t));
        }
        return out;
    }

    /**
     * Whether a thrown {@code item} is small enough to pass through a
     * portcullis (F217). Since DM 1.1 keys never do, so a key needed beyond
     * a door can't be lost through it.
     */
    public static boolean passesThroughDoors(Item item) {
        boolean key = item.category() == Item.Category.JUNK && item.type() >= FIRST_KEY && item.type() <= LAST_KEY;
        return !key && PASSES_DOORS.contains(objectInfoIndex(item));
    }

    /** The action set of {@code item} in the action hand (G237), or 0 if it has none. */
    public static int actionSet(Item item) {
        int i = objectInfoIndex(item);
        return i >= 0 && i < ACTION_SETS.length ? ACTION_SETS[i] : 0;
    }

    /** Armour defense and attributes per armour type (G239, from ScummVM's DM engine): bits 0-2 sharp defense. */
    private static final int[] ARMOUR_DEFENSE = {
            5, 10, 4, 5, 25, 5, 5, 7, 7, 6, 4, 5, 7, 11, 13, 13, 17, 20, 20, 12, 9, 8, 9, 1, 5, 12, 17, 20,
            22, 16, 20, 35, 35, 35, 70, 55, 25, 30, 40, 65, 56, 37, 56, 62, 125, 90, 50, 85, 76, 160, 101, 60,
            100, 54, 60, 88, 16, 3};
    private static final int[] ARMOUR_ATTRIBUTES = {
            1, 1, 1, 2, 4, 0, 0, 1, 1, 1, 0, 1, 1, 2, 2, 2, 3, 3, 3, 2, 1, 1, 1, 4, 4, 5, 5, 5,
            0x85, 0x82, 0x83, 0x84, 5, 5, 7, 7, 6, 6, 7, 4, 4, 5, 0x84, 5, 4, 4, 5, 0x84, 4, 4, 4, 5,
            0x84, 6, 7, 4, 2, 3};

    /**
     * DM's F143: the defense {@code item} gives as armour, or 0 if it isn't
     * armour. Against sharp attacks it is scaled by (sharp defense + 4) / 8.
     */
    public static int armourDefense(Item item, boolean sharp) {
        if (item == null || item.category() != Item.Category.ARMOUR || item.type() >= ARMOUR_DEFENSE.length) {
            return 0;
        }
        int defense = ARMOUR_DEFENSE[item.type()];
        return sharp ? defense * ((ARMOUR_ATTRIBUTES[item.type()] & 7) + 4) >> 3 : defense;
    }

    /** DM's F140: a full waterskin weighs 2 more per draught; a chest's contents aren't modelled yet. */
    static int weight(Item item) {
        int t = item.type();
        return switch (item.category()) {
            case WEAPON -> t < WEAPON_WEIGHTS.length ? WEAPON_WEIGHTS[t] : 0;
            case ARMOUR -> t < ARMOUR_WEIGHTS.length ? ARMOUR_WEIGHTS[t] : 0;
            case JUNK -> (t < JUNK_WEIGHTS.length ? JUNK_WEIGHTS[t] : 0) + (t == WATERSKIN ? item.charges() << 1 : 0);
            case POTION -> t == EMPTY_FLASK ? 1 : 3;
            case SCROLL -> 1;
            case CONTAINER -> 50;
        };
    }

    private static final int JEWEL_SYMAL = 2;
    private static final int ILLUMULET = 3;

    /**
     * The item as DM draws it in {@code slot}: a torch in a hand is lit, its
     * flame shrinking with its charges (icons 4-7), and a Jewel Symal or
     * Illumulet worn on the neck shows its "equipped" icon (DM's F033).
     */
    public static Item shownIn(Item item, Slot slot) {
        int variant = item.nameVariant();
        if (Light.isTorch(item) && (slot == Slot.READY_HAND || slot == Slot.ACTION_HAND)) {
            variant = Light.litTorchVariant(item);
        } else if (slot == Slot.NECK && item.category() == Item.Category.JUNK
                && (item.type() == JEWEL_SYMAL || item.type() == ILLUMULET)) {
            variant = 1;
        }
        return variant == item.nameVariant() ? item
                : new Item(item.category(), item.type(), item.name(), variant, item.wornOn(), item.charges());
    }

    /** How much food eating {@code item} gives, or 0 if it isn't food. */
    public static int foodValue(Item item) {
        int t = item.type();
        return item.category() == Item.Category.JUNK && t >= FIRST_FOOD && t <= LAST_FOOD
                ? FOOD_AMOUNTS[t - FIRST_FOOD] : 0;
    }

    /** Whether DM lets the item be put in the mouth: food, waterskins and potions. */
    public static boolean isConsumable(Item item) {
        return item.category() == Item.Category.POTION
                || item.category() == Item.Category.JUNK && (item.type() == WATERSKIN || foodValue(item) > 0);
    }

    static boolean isMissileWeapon(int type) {
        return MISSILES.contains(type);
    }

    /** Potions, scrolls and the small junk in {@link #POUCH_JUNK}. */
    static boolean fitsPouch(Item item) {
        return switch (item.category()) {
            case POTION, SCROLL -> true;
            case JUNK -> POUCH_JUNK.contains(item.type());
            default -> false;
        };
    }

    static boolean isShield(int type) {
        return type < ARMOUR_SLOTS.length && ARMOUR_SLOTS[type] == null;
    }

    /** The legs versions of ROBE and FINE ROBE are the second icon with that name. */
    private static int armourVariant(int type) {
        return type == 6 || type == 8 ? 1 : 0;
    }

    /** The second MAGICAL BOX (green) and the second BONES have their own icons. */
    private static int junkVariant(int type) {
        return type == 43 || type == 52 ? 1 : 0;
    }

    private static String name(String[] table, int type) {
        return type >= 0 && type < table.length ? table[type] : "UNKNOWN";
    }

    /** Every name the catalog can produce, for checking against GRAPHICS.DAT. */
    public static Set<String> allNames() {
        Set<String> all = new TreeSet<>();
        for (String[] t : new String[][] {WEAPONS, ARMOUR, POTIONS, JUNK}) {
            all.addAll(Arrays.asList(t));
        }
        all.add("SCROLL");
        all.add("CHEST");
        all.add("WATER");
        return all;
    }
}
