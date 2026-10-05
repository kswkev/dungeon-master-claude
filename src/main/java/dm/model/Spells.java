package dm.model;

import java.util.List;

/**
 * DM's spells, ported from ReDMCSB's MENUS.C (F0399 adding a symbol, F0400
 * removing one, F0409 finding the spell, F0412 casting it) with the G0487
 * spell table, cross-checked against ScummVM's {@code MenuMan}.
 *
 * <p>A spell is up to four symbols, one from each row in turn: a power
 * (LO to MON), an element, a form and a class. Each symbol costs mana as it
 * is entered; the others cost more the stronger the power. Symbols are kept
 * as DM keeps them, as characters 96-119 (row × 6 + column), which are also
 * the rune glyphs' places in DM's font.
 */
public final class Spells {

    private Spells() {
    }

    /** The first symbol character (LO); the symbols run on in rows of 6. */
    public static final char FIRST_SYMBOL = 96;
    public static final int ROWS = 4;
    public static final int PER_ROW = 6;

    /** The symbols' names, by row: power, element, form, class/alignment. */
    public static final List<String> NAMES = List.of(
            "LO", "UM", "ON", "EE", "PAL", "MON",
            "YA", "VI", "OH", "FUL", "DES", "ZO",
            "VEN", "EW", "KATH", "IR", "BRO", "GOR",
            "KU", "ROS", "DAIN", "NETA", "RA", "SAR");

    /** G0485: each symbol's base mana cost, by row. */
    private static final int[][] BASE_MANA_COST = {
            {1, 2, 3, 4, 5, 6},
            {2, 3, 4, 5, 6, 7},
            {4, 5, 6, 7, 7, 9},
            {2, 2, 3, 4, 6, 7}};
    /** G0486: the power's multiplier (in eighths) on the other symbols' costs. */
    private static final int[] MANA_COST_MULTIPLIER = {8, 12, 16, 20, 24, 28};

    /**
     * F0399's mana cost of the symbol in column {@code column} of row
     * {@code row}, after the power {@code power} (its character; ignored on
     * row 0).
     */
    public static int manaCost(int row, int column, char power) {
        int cost = BASE_MANA_COST[row][column];
        if (row > 0) {
            cost = (cost * MANA_COST_MULTIPLIER[power - FIRST_SYMBOL]) >> 3;
        }
        return cost;
    }

    /** DM's spell kinds (M67). */
    public static final int KIND_POTION = 1;
    public static final int KIND_PROJECTILE = 2;
    public static final int KIND_OTHER = 3;

    /** Kind "other" spell types (M68). */
    static final int OTHER_LIGHT = 0;
    static final int OTHER_DARKNESS = 1;
    static final int OTHER_THIEVES_EYE = 2;
    static final int OTHER_INVISIBILITY = 3;
    static final int OTHER_PARTY_SHIELD = 4;
    static final int OTHER_MAGIC_TORCH = 5;
    static final int OTHER_FOOTPRINTS = 6;
    static final int OTHER_ZOKATHRA = 7;
    static final int OTHER_FIRESHIELD = 8;

    /**
     * One of G0487's spells: its symbols after the power (packed one per
     * byte, the first in bits 16-23), the base skill level it needs (plus
     * the power's ordinal), the skill it uses, and DM's attributes word
     * (bits 15-10 the ticks it disables the caster, 9-4 the type, 3-0 the
     * kind). For a projectile spell the type is its explosion's.
     */
    public record Spell(int symbols, int baseSkillLevel, int skill, int attributes) {

        public int kind() {
            return attributes & 0xF;
        }

        public int type() {
            return (attributes >> 4) & 0x3F;
        }

        /** The ticks casting it disables the caster's actions (M69). */
        public int duration() {
            return (attributes >> 10) & 0x3F;
        }

        /**
         * Whether this remake can cast it yet: the projectile spells and the
         * light, darkness and shield spells. Potions, invisibility, thieves'
         * eye, magic footprints and ZO KATH RA come later.
         */
        public boolean castable() {
            return switch (kind()) {
                case KIND_POTION, KIND_PROJECTILE -> true;
                case KIND_OTHER -> switch (type()) {
                    case OTHER_LIGHT, OTHER_DARKNESS, OTHER_PARTY_SHIELD, OTHER_MAGIC_TORCH, OTHER_FIRESHIELD -> true;
                    default -> false;
                };
                default -> false;
            };
        }

        /** The spell's symbols after the power, as DM's characters. */
        public String symbolString() {
            StringBuilder sb = new StringBuilder();
            for (int shift = 16; shift >= 0; shift -= 8) {
                int c = (symbols >> shift) & 0xFF;
                if (c != 0) {
                    sb.append((char) c);
                }
            }
            return sb.toString();
        }
    }

    /** G0487, in DM's order. */
    static final List<Spell> TABLE = List.of(
            new Spell(0x00666F00, 2, 15, 0x7843), // YA IR: party shield
            new Spell(0x00667073, 1, 18, 0x4863), // YA BRO ROS: magic footprints
            new Spell(0x00686D77, 3, 17, 0xB433), // OH EW SAR: invisibility
            new Spell(0x00686C00, 3, 19, 0x6C72), // OH VEN: poison cloud
            new Spell(0x00686D76, 3, 18, 0x8423), // OH EW RA: thieves' eye
            new Spell(0x00686E76, 4, 17, 0x7822), // OH KATH RA: lightning bolt
            new Spell(0x00686F76, 4, 17, 0x5803), // OH IR RA: light
            new Spell(0x00690000, 1, 16, 0x3C53), // FUL: magic torch
            new Spell(0x00696F00, 3, 16, 0xA802), // FUL IR: fireball
            new Spell(0x00697072, 4, 13, 0x3C71), // FUL BRO KU: strength potion
            new Spell(0x00697075, 4, 15, 0x7083), // FUL BRO NETA: fire shield
            new Spell(0x006A6D00, 1, 18, 0x5032), // DES EW: harm non-material
            new Spell(0x006A6C00, 1, 19, 0x4062), // DES VEN: poison bolt
            new Spell(0x006A6F77, 1, 15, 0x3013), // DES IR SAR: darkness
            new Spell(0x006B0000, 1, 17, 0x3C42), // ZO: open door
            new Spell(0x00667000, 2, 15, 0x64C1), // YA BRO: shield potion
            new Spell(0x00660000, 2, 13, 0x3CB1), // YA: stamina potion
            new Spell(0x00667074, 4, 13, 0x3C81), // YA BRO DAIN: wisdom potion
            new Spell(0x00667075, 4, 13, 0x3C91), // YA BRO NETA: vitality potion
            new Spell(0x00670000, 1, 13, 0x80E1), // VI: health potion
            new Spell(0x00677000, 1, 13, 0x68A1), // VI BRO: cure poison potion
            new Spell(0x00687073, 4, 13, 0x3C61), // OH BRO ROS: dexterity potion
            new Spell(0x006B7076, 3, 2, 0xFCD1),  // ZO BRO RA: mana potion
            new Spell(0x006B6C00, 2, 19, 0x7831), // ZO VEN: poison potion
            new Spell(0x006B6E76, 0, 3, 0x3C73)); // ZO KATH RA: Zokathra

    /**
     * F0409: the spell {@code symbols} (a power then up to three more) make,
     * or null: a power alone, or symbols that make no spell.
     */
    public static Spell find(String symbols) {
        if (symbols.length() < 2) {
            return null;
        }
        int packed = 0;
        int shift = 24;
        for (int i = 0; i < symbols.length() && shift >= 0; i++, shift -= 8) {
            packed |= symbols.charAt(i) << shift;
        }
        for (Spell s : TABLE) {
            if ((s.symbols() & 0xFF000000) != 0 ? packed == s.symbols() : (packed & 0x00FFFFFF) == s.symbols()) {
                return s;
            }
        }
        return null;
    }

    /** A symbol's name, from its character. */
    public static String name(char symbol) {
        return NAMES.get(symbol - FIRST_SYMBOL);
    }
}
