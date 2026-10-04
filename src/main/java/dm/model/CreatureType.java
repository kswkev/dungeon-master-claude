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
}
