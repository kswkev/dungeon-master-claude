package dm.model;

import java.util.Arrays;
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

    private static final Set<Integer> NECK_JUNK = Set.of(2, 3, 37, 38, 39, 40, 41, 48);

    private ItemCatalog() {
    }

    /** Builds the item for a category and type number; unknown numbers get a generic name. */
    public static Item item(Item.Category category, int type) {
        return switch (category) {
            case WEAPON -> new Item(category, type, name(WEAPONS, type), type == 45 ? 1 : 0, null);
            case ARMOUR -> new Item(category, type, name(ARMOUR, type), armourVariant(type),
                    type < ARMOUR_SLOTS.length ? ARMOUR_SLOTS[type] : null);
            case POTION -> new Item(category, type, name(POTIONS, type), 0, null);
            case JUNK -> new Item(category, type, name(JUNK, type), junkVariant(type),
                    NECK_JUNK.contains(type) ? Slot.NECK : null);
            case SCROLL -> new Item(category, type, "SCROLL", 0, null);
            case CONTAINER -> new Item(category, type, "CHEST", 0, null);
        };
    }

    static boolean isMissileWeapon(int type) {
        return MISSILES.contains(type);
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
        return all;
    }
}
