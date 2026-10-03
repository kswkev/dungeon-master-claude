package dm.model;

/**
 * One map square. Keeps the raw byte so later sprints can decode the
 * per-type attribute bits (door state, stairs direction, pit open flag...).
 */
public record Square(int raw) {

    public static final Square SOLID = new Square(0);

    public SquareType type() {
        return SquareType.fromRaw(raw);
    }

    /** Bit 4: the square has a thing list (items, sensors, creatures...). */
    public boolean hasThings() {
        return (raw & 0x10) != 0;
    }

    /** Door squares: bits 0-2 hold the door state (0 open ... 4 closed, 5 destroyed). */
    public int doorState() {
        return raw & 0x07;
    }

    /**
     * Doors and stairs: bit 3 set means the passage runs north-south, so a
     * door's panel (or the stairs' front) is seen when facing north or south.
     */
    public boolean runsNorthSouth() {
        return (raw & 0x08) != 0;
    }

    /** True if a party facing {@code facing} sees this door or staircase head-on. */
    public boolean facesAlong(Direction facing) {
        return runsNorthSouth() == (facing == Direction.NORTH || facing == Direction.SOUTH);
    }

    public boolean isDoorOpen() {
        int state = doorState();
        return state == 0 || state == 5;
    }

    /** Stairs squares: bit 2 set means the stairs lead up. */
    public boolean stairsUp() {
        return (raw & 0x04) != 0;
    }

    /** Pit squares: bit 3 set means the pit is open (a closed pit looks like floor). */
    public boolean pitOpen() {
        return (raw & 0x08) != 0;
    }

    /** Pit squares: bit 2 set means the pit is imaginary: drawn, but nothing falls through it. */
    public boolean pitImaginary() {
        return (raw & 0x04) != 0;
    }

    /** Pit squares: bit 0 set means the pit is invisible: not drawn, but things still fall through it. */
    public boolean pitInvisible() {
        return (raw & 0x01) != 0;
    }

    /** Teleporter squares: bit 3 set means the teleporter is active. */
    public boolean teleporterOpen() {
        return (raw & 0x08) != 0;
    }

    /** Teleporter squares: bit 2 set means the teleporter's shimmering field is drawn. */
    public boolean teleporterVisible() {
        return (raw & 0x04) != 0;
    }

    public boolean isPassable() {
        return switch (type()) {
            case WALL -> false;
            case DOOR -> isDoorOpen();
            default -> true;
        };
    }

    /** Squares the renderer draws as a full wall block. */
    public boolean looksSolid() {
        SquareType t = type();
        return t == SquareType.WALL || t == SquareType.FAKEWALL;
    }
}
