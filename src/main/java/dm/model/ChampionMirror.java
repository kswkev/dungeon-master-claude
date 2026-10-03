package dm.model;

import java.io.Serializable;

/**
 * A Hall of Champions mirror: a portrait on one side of wall square (x, y).
 * {@code side} is the side of the wall it hangs on, i.e. the direction it faces.
 */
public final class ChampionMirror implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int x;
    private final int y;
    private final Direction side;
    private final Champion champion;
    private boolean taken;

    public ChampionMirror(int x, int y, Direction side, Champion champion) {
        this.x = x;
        this.y = y;
        this.side = side;
        this.champion = champion;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public Direction side() {
        return side;
    }

    public Champion champion() {
        return champion;
    }

    /** True once the champion has been resurrected; the mirror then shows empty. */
    public boolean taken() {
        return taken;
    }

    void markTaken() {
        taken = true;
    }

    /** True if a party standing at (px, py) facing {@code facing} is looking straight at this mirror. */
    public boolean facedFrom(int px, int py, Direction facing) {
        return px == x + side.dx && py == y + side.dy && facing == side.opposite();
    }
}
