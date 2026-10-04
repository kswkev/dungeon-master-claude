package dm.model;

/**
 * DM's 27 creature types, with what drawing them needs. The values are
 * graphics.dat items 558/559 on the ST (G0219 creature aspects, G0243
 * creature info); the PC keeps them in its program, so they are taken from
 * ScummVM's DM engine.
 */
public enum CreatureType {
    GIANT_SCORPION("GIANT SCORPION", 0x0482, 0x623D, 0, 56, 84, 56, 84, 56, 84, 0x1D, 0x01),
    SWAMP_SLIME("SWAMP SLIME", 0x0480, 0xA625, 4, 32, 66, 0, 0, 32, 69, 0x0B, 0x20),
    GIGGLER("GIGGLER", 0x0510, 0x6198, 6, 24, 48, 24, 48, 0, 0, 0x0B, 0x00),
    WIZARD_EYE("WIZARD EYE", 0x04B4, 0xB225, 10, 32, 61, 0, 0, 32, 61, 0x24, 0x31),
    PAIN_RAT("PAIN RAT", 0x0701, 0xA3B8, 12, 32, 64, 56, 64, 32, 64, 0x14, 0x34),
    RUSTER("RUSTER", 0x0581, 0x539D, 16, 24, 49, 40, 49, 0, 0, 0x18, 0x34),
    SCREAMER("SCREAMER", 0x070C, 0x0020, 19, 32, 60, 0, 0, 32, 60, 0x0D, 0x00),
    ROCKPILE("ROCKPILE", 0x0300, 0x0220, 21, 32, 43, 0, 0, 32, 64, 0x04, 0x00),
    GHOST("GHOST", 0x1864, 0x5225, 23, 32, 83, 0, 0, 32, 93, 0x04, 0x00),
    STONE_GOLEM("STONE GOLEM", 0x0282, 0x71B8, 25, 32, 101, 32, 101, 32, 101, 0x14, 0x00),
    MUMMY("MUMMY", 0x1480, 0x11B8, 29, 32, 82, 32, 82, 32, 83, 0x04, 0x00),
    BLACK_FLAME("BLACK FLAME", 0x18C6, 0x0225, 33, 32, 80, 0, 0, 32, 99, 0x14, 0x00),
    SKELETON("SKELETON", 0x1280, 0x6038, 35, 32, 80, 32, 80, 32, 76, 0x04, 0x00),
    COUATL("COUATL", 0x14A2, 0xB23D, 39, 32, 96, 56, 93, 32, 90, 0x1D, 0x20),
    VEXIRK("VEXIRK", 0x05B8, 0x1638, 43, 32, 49, 16, 49, 32, 56, 0x04, 0x30),
    MAGENTA_WORM("MAGENTA WORM", 0x0381, 0x523D, 47, 32, 59, 56, 43, 32, 67, 0x14, 0x78),
    TROLIN("TROLIN", 0x0680, 0xA038, 51, 32, 83, 32, 74, 32, 74, 0x04, 0x65),
    GIANT_WASP("GIANT WASP", 0x04A0, 0xF23D, 55, 24, 49, 24, 53, 24, 53, 0x24, 0x00),
    ANIMATED_ARMOUR("ANIMATED ARMOUR", 0x0280, 0xA3BD, 59, 32, 89, 32, 89, 32, 89, 0x04, 0x00),
    MATERIALIZER("MATERIALIZER", 0x0060, 0xE23D, 63, 32, 84, 32, 84, 32, 84, 0x0D, 0xA9),
    WATER_ELEMENTAL("WATER ELEMENTAL", 0x10DE, 0x0225, 67, 56, 27, 0, 0, 56, 80, 0x04, 0x65),
    OITU("OITU", 0x0082, 0xA3BD, 69, 56, 77, 56, 81, 56, 77, 0x04, 0xA9),
    DEMON("DEMON", 0x1480, 0x53BD, 73, 32, 87, 32, 89, 32, 89, 0x04, 0xCB),
    LORD_CHAOS("LORD CHAOS", 0x38AA, 0x0038, 77, 32, 96, 32, 94, 32, 96, 0x04, 0x00),
    RED_DRAGON("RED DRAGON", 0x068A, 0x97BD, 81, 64, 94, 72, 94, 64, 94, 0x04, 0xCB),
    LORD_ORDER("LORD ORDER", 0x38AA, 0x0000, 85, 32, 93, 0, 0, 0, 0, 0x04, 0xCB),
    GREY_LORD("GREY LORD", 0x38AA, 0x0000, 86, 32, 93, 0, 0, 0, 0, 0x04, 0xCB);

    /** How much of a square one creature takes (attribute bits 0-1). */
    public enum Size { QUARTER, HALF, FULL }

    // Graphic info bits (G0243 GraphicInfo).
    private static final int FLIP_NON_ATTACK = 0x0004;
    private static final int SIDE = 0x0008;
    private static final int BACK = 0x0010;
    private static final int ATTACK = 0x0020;
    private static final int SPECIAL_D2_FRONT = 0x0080;
    private static final int SPECIAL_D2_FRONT_IS_FLIPPED = 0x0100;
    private static final int FLIP_ATTACK = 0x0200;
    private static final int FLIP_DURING_ATTACK = 0x0400;

    // Attribute bits (G0243 Attributes).
    private static final int SIDE_ATTACK = 0x0004;
    private static final int PREFER_BACK_ROW = 0x0008;
    private static final int ATTACK_ANY_CHAMPION = 0x0010;
    private static final int LEVITATION = 0x0020;
    private static final int NON_MATERIAL = 0x0040;
    private static final int SEE_INVISIBLE = 0x0800;
    private static final int NIGHT_VISION = 0x1000;
    private static final int ARCHENEMY = 0x2000;

    /** DM's movement ticks for a creature that never moves (Black Flame). */
    public static final int IMMOBILE = 255;

    /**
     * The rest of G0243, per type in enum order: attack sound ordinal,
     * movement ticks, attack ticks, defense, base health, attack, poison
     * attack, dexterity, ranges, properties, resistances, animation ticks,
     * wound probabilities, attack type. The values come from ScummVM's DM engine.
     */
    private static final int[][] INFO = {
            {4, 8, 20, 55, 150, 150, 240, 55, 0x1153, 0x299B, 0x0876, 0x0254, 0xFD40, 4},
            {0, 15, 32, 20, 110, 80, 15, 20, 0x3132, 0x33A9, 0x0E42, 0x0384, 0xFC41, 3},
            {6, 3, 5, 50, 10, 10, 0, 110, 0x1376, 0x710A, 0x0235, 0x0222, 0xFD20, 0},
            {0, 10, 21, 30, 40, 58, 0, 80, 0x320A, 0x96AA, 0x0B3C, 0x0113, 0xF910, 5},
            {1, 9, 8, 45, 101, 90, 0, 65, 0x1554, 0x58FF, 0x0A34, 0x0143, 0xFE93, 4},
            {0, 20, 18, 100, 60, 30, 0, 30, 0x1232, 0x4338, 0x0583, 0x0265, 0xFFD6, 3},
            {3, 120, 10, 5, 165, 5, 0, 5, 0x1111, 0x10F1, 0x0764, 0x02F2, 0xFC84, 6},
            {7, 185, 15, 170, 50, 40, 5, 10, 0x1463, 0x25C4, 0x06E3, 0x01F4, 0xFD93, 4},
            {2, 11, 16, 15, 30, 55, 0, 80, 0x1423, 0x4664, 0x0FC8, 0x0116, 0xFB30, 6},
            {10, 21, 14, 240, 120, 219, 0, 35, 0x1023, 0x3BFF, 0x0FF7, 0x04F3, 0xF920, 3},
            {2, 17, 12, 25, 33, 20, 0, 40, 0x1224, 0x5497, 0x0F15, 0x0483, 0xFB20, 3},
            {0, 255, 8, 45, 80, 105, 0, 60, 0x1314, 0x55A5, 0x0FF9, 0x0114, 0xFD95, 1},
            {11, 7, 7, 22, 20, 22, 0, 80, 0x1013, 0x6596, 0x0F63, 0x0132, 0xFA30, 4},
            {9, 5, 10, 42, 39, 90, 100, 88, 0x1343, 0x5734, 0x0638, 0x0112, 0xFA30, 4},
            {0, 10, 20, 47, 44, 75, 0, 90, 0x4335, 0xD952, 0x035B, 0x0664, 0xFD60, 5},
            {5, 18, 19, 72, 70, 45, 35, 35, 0x1AA1, 0x15AB, 0x0B93, 0x0253, 0xFFC5, 4},
            {10, 13, 8, 28, 20, 25, 0, 41, 0x1343, 0x2148, 0x0321, 0x0332, 0xFC30, 3},
            {0, 1, 16, 180, 8, 28, 20, 150, 0x1432, 0x19FD, 0x0004, 0x0112, 0xF710, 4},
            {11, 14, 6, 140, 60, 105, 0, 70, 0x1005, 0x7AFF, 0x0FFA, 0x0143, 0xFA30, 4},
            {0, 5, 18, 15, 33, 61, 0, 65, 0x3258, 0xAC77, 0x0F56, 0x0117, 0xFC40, 5},
            {8, 25, 25, 75, 144, 66, 0, 50, 0x1381, 0x7679, 0x0EA7, 0x0345, 0xFD93, 3},
            {3, 7, 15, 33, 77, 130, 0, 60, 0x1592, 0x696A, 0x0859, 0x0224, 0xFC30, 4},
            {0, 10, 14, 68, 100, 100, 0, 75, 0x4344, 0xBDF9, 0x0A5D, 0x0124, 0xF920, 3},
            {0, 12, 22, 255, 180, 210, 0, 130, 0x6369, 0xFF37, 0x0FBF, 0x0564, 0xFB52, 5},
            {1, 13, 28, 110, 255, 255, 0, 70, 0x3645, 0xBF7C, 0x06CD, 0x0445, 0xFC30, 4},
            {0, 12, 22, 255, 180, 210, 0, 130, 0x6369, 0xFF37, 0x0FBF, 0x0564, 0xFB52, 5},
            {0, 12, 22, 255, 180, 210, 0, 130, 0x6369, 0xFF37, 0x0FBF, 0x0564, 0xFB52, 5},
    };

    /** DM's sound for each attack sound ordinal (G0244, PC version). */
    private static final int[] ATTACK_SOUNDS = {3, 7, 14, 15, 19, 21, 29, 30, 31, 4, 16};

    private final String displayName;
    private final int attributes;
    private final int graphicInfo;
    private final int firstGraphic;
    private final int frontWidth;
    private final int frontHeight;
    private final int sideWidth;
    private final int sideHeight;
    private final int attackWidth;
    private final int attackHeight;
    private final int coordinateSetAndTransparent;
    private final int replacementSets;

    CreatureType(String displayName, int attributes, int graphicInfo, int firstGraphic,
                 int frontWidth, int frontHeight, int sideWidth, int sideHeight, int attackWidth, int attackHeight,
                 int coordinateSetAndTransparent, int replacementSets) {
        this.displayName = displayName;
        this.attributes = attributes;
        this.graphicInfo = graphicInfo;
        this.firstGraphic = firstGraphic;
        this.frontWidth = frontWidth;
        this.frontHeight = frontHeight;
        this.sideWidth = sideWidth;
        this.sideHeight = sideHeight;
        this.attackWidth = attackWidth;
        this.attackHeight = attackHeight;
        this.coordinateSetAndTransparent = coordinateSetAndTransparent;
        this.replacementSets = replacementSets;
    }

    /** The type with DUNGEON.DAT's number {@code n}, or null. */
    public static CreatureType of(int n) {
        return n >= 0 && n < values().length ? values()[n] : null;
    }

    public String displayName() {
        return displayName;
    }

    public Size size() {
        return Size.values()[Math.min(attributes & 3, 2)];
    }

    /** The first of this type's pictures, relative to the first creature graphic. */
    public int firstGraphic() {
        return firstGraphic;
    }

    public boolean hasSide() {
        return (graphicInfo & SIDE) != 0;
    }

    public boolean hasBack() {
        return (graphicInfo & BACK) != 0;
    }

    public boolean hasAttack() {
        return (graphicInfo & ATTACK) != 0;
    }

    public boolean flipsWhenIdle() {
        return (graphicInfo & FLIP_NON_ATTACK) != 0;
    }

    public boolean specialD2Front() {
        return (graphicInfo & SPECIAL_D2_FRONT) != 0;
    }

    public boolean specialD2FrontIsFlipped() {
        return (graphicInfo & SPECIAL_D2_FRONT_IS_FLIPPED) != 0;
    }

    /**
     * The pictures in order: front, then side and back if the type has
     * them, then the attack picture. Returns the offset of {@code view} from
     * {@link #firstGraphic}, or -1 if the type has no such picture.
     */
    public int graphicOffset(View view) {
        return switch (view) {
            case FRONT -> 0;
            case SIDE -> hasSide() ? 1 : -1;
            case BACK -> hasBack() ? (hasSide() ? 2 : 1) : -1;
            case ATTACK -> hasAttack() ? 1 + (hasSide() ? 1 : 0) + (hasBack() ? 1 : 0) : -1;
        };
    }

    /** Which picture of a creature is shown. */
    public enum View { FRONT, SIDE, BACK, ATTACK }

    /** DM's coordinate set for this type: 0 ground, 1 half-square and large creatures, 2 flying. */
    public int coordinateSet() {
        return (coordinateSetAndTransparent >> 4) & 15;
    }

    /** The palette colour that is see-through in this type's pictures. */
    public int transparentColour() {
        return coordinateSetAndTransparent & 15;
    }

    /** Which replacement colour set (1-based, 0 none) a map with this type uses for colour 9. */
    public int replacementSet9() {
        return replacementSets & 15;
    }

    /** Which replacement colour set (1-based, 0 none) a map with this type uses for colour 10. */
    public int replacementSet10() {
        return (replacementSets >> 4) & 15;
    }

    public int frontWidth() {
        return frontWidth;
    }

    public int frontHeight() {
        return frontHeight;
    }

    public int sideWidth() {
        return sideWidth;
    }

    public int sideHeight() {
        return sideHeight;
    }

    public int attackWidth() {
        return attackWidth;
    }

    public int attackHeight() {
        return attackHeight;
    }

    // ---- G0243 creature info: how the creature behaves and fights -----------

    private int info(int field) {
        return INFO[ordinal()][field];
    }

    /** Ticks between moves, {@link #IMMOBILE} for a creature that never moves. */
    public int movementTicks() {
        return info(1);
    }

    /** The fewest ticks between two attacks. */
    public int attackTicks() {
        return info(2);
    }

    public int defense() {
        return info(3);
    }

    public int baseHealth() {
        return info(4);
    }

    public int attack() {
        return info(5);
    }

    public int poisonAttack() {
        return info(6);
    }

    public int dexterity() {
        return info(7);
    }

    public int sightRange() {
        return info(8) & 15;
    }

    public int smellRange() {
        return (info(8) >> 8) & 15;
    }

    /** 1 for melee; more for creatures that cast spells (which they don't yet). */
    public int attackRange() {
        return (info(8) >> 12) & 15;
    }

    /** Experience a champion earns (in parry) for being attacked by this type. */
    public int experience() {
        return (info(9) >> 8) & 15;
    }

    public int fearResistance() {
        return (info(9) >> 4) & 15;
    }

    /** DM's wariness: types of 10 and up won't take a teleporter to a map they aren't allowed on. */
    public int wariness() {
        return (info(9) >> 12) & 15;
    }

    public int poisonResistance() {
        return (info(10) >> 8) & 15;
    }

    /** Animation ticks: bits 0-3 between attacks' looks, 4-7 idle, 8-11 attacking. */
    public int animationTicks() {
        return info(11);
    }

    /** Four 4-bit chances to wound the head, legs, torso and feet (bits 15-12, 11-8, 7-4, 3-0). */
    public int woundProbabilities() {
        return info(12);
    }

    /** DM's attack type: 0 normal, 1 fire, 2 self, 3 blunt, 4 sharp, 5 magic, 6 psychic, 7 lightning. */
    public int attackType() {
        return info(13);
    }

    /** DM's sound index for this type's attack, or -1 for none. */
    public int attackSound() {
        int ordinal = info(0);
        return ordinal == 0 ? -1 : ATTACK_SOUNDS[ordinal - 1];
    }

    /** DM's sound index for this type moving (F0514), or -1 for a silent one. */
    public int movementSound() {
        return switch (this) {
            case GIGGLER, STONE_GOLEM, MUMMY, VEXIRK, DEMON -> 24;
            case GIANT_SCORPION, PAIN_RAT, RUSTER, SCREAMER, ROCKPILE, MAGENTA_WORM, OITU -> 26;
            case RED_DRAGON -> 32;
            case SKELETON -> 33;
            case ANIMATED_ARMOUR -> 22;
            case SWAMP_SLIME, WATER_ELEMENTAL -> 27;
            case COUATL, GIANT_WASP -> 23;
            default -> -1;
        };
    }

    /**
     * DM's G245-G253: what one creature of this type always drops when it
     * dies, as {category, type, maybe}; a "maybe" item drops half the time.
     * Animated armour's are cursed in DM (not modelled).
     */
    public int[][] fixedPossessions() {
        final int w = Item.Category.WEAPON.ordinal();
        final int a = Item.Category.ARMOUR.ordinal();
        final int j = Item.Category.JUNK.ordinal();
        return switch (this) {
            case PAIN_RAT -> new int[][] {{j, 35, 0}, {j, 35, 1}};                   // drumsticks
            case SCREAMER -> new int[][] {{j, 33, 0}, {j, 33, 1}};                   // screamer slices
            case ROCKPILE -> new int[][] {{j, 25, 0}, {j, 25, 1}, {w, 30, 1}, {w, 30, 1}}; // boulders, rocks
            case STONE_GOLEM -> new int[][] {{w, 24, 0}};                            // stone club
            case SKELETON -> new int[][] {{w, 9, 0}, {a, 30, 0}};                    // falchion, wooden shield
            case TROLIN -> new int[][] {{w, 23, 0}};                                 // club
            case MAGENTA_WORM -> new int[][] {{j, 34, 0}, {j, 34, 1}, {j, 34, 1}};   // worm rounds
            case ANIMATED_ARMOUR -> new int[][] {{a, 41, 0}, {a, 40, 0}, {a, 39, 0}, {w, 10, 0}, {a, 38, 0},
                    {w, 10, 0}};                                                     // plate armour, armet, swords
            case RED_DRAGON -> new int[][] {{j, 36, 0}, {j, 36, 0}, {j, 36, 0}, {j, 36, 0}, {j, 36, 0},
                    {j, 36, 0}, {j, 36, 0}, {j, 36, 0}, {j, 36, 1}, {j, 36, 1}};     // dragon steaks
            default -> new int[0][];
        };
    }

    /** Whether DM drops fixed possessions for this type (attribute bit 9). */
    public boolean dropsFixedPossessions() {
        return (attributes & 0x0200) != 0;
    }

    /** Can attack (and see) in every direction, not just the way it faces. */
    public boolean sideAttack() {
        return (attributes & SIDE_ATTACK) != 0;
    }

    public boolean prefersBackRow() {
        return (attributes & PREFER_BACK_ROW) != 0;
    }

    public boolean attacksAnyChampion() {
        return (attributes & ATTACK_ANY_CHAMPION) != 0;
    }

    /** Flies: crosses open pits. */
    public boolean levitates() {
        return (attributes & LEVITATION) != 0;
    }

    /** Passes through closed doors, and a closing door can't hurt it. */
    public boolean nonMaterial() {
        return (attributes & NON_MATERIAL) != 0;
    }

    public boolean seesInvisible() {
        return (attributes & SEE_INVISIBLE) != 0;
    }

    /** Sees as far in the dark as in the light. */
    public boolean nightVision() {
        return (attributes & NIGHT_VISION) != 0;
    }

    /** Lord Chaos: can't be hurt and can jump two squares. */
    public boolean archenemy() {
        return (attributes & ARCHENEMY) != 0;
    }

    /** How tall the creature is (attribute bits 7-8): a door that opens upward must be raised past it. */
    public int height() {
        return (attributes >> 7) & 3;
    }

    public boolean flipsToAttack() {
        return (graphicInfo & FLIP_ATTACK) != 0;
    }

    public boolean flipsDuringAttack() {
        return (graphicInfo & FLIP_DURING_ATTACK) != 0;
    }

    /** Random sideways jitter of the picture, 0-3 pixels (graphic info bits 12-13). */
    public int xJitter() {
        return (graphicInfo >> 12) & 3;
    }

    /** Random vertical jitter of the picture, 0-3 pixels (graphic info bits 14-15). */
    public int yJitter() {
        return (graphicInfo >> 14) & 3;
    }
}
