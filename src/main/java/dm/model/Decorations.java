package dm.model;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Where a map's decorations ("ornaments" in DM) are: one per wall side, one
 * per floor square, and one per door, each as a global ornament index into
 * GRAPHICS.DAT's decoration art, or -1 for none. Inscriptions also keep their text.
 */
public final class Decorations implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int[][][] wall;      // [x][y][side]
    private final String[][][] text;   // [x][y][side], inscriptions only
    private final int[][] floor;
    private final int[][] door;
    private final boolean[][] doorButton;
    /** [x][y][side]: an inscription there that is hidden for now (#55); null in saves from before. */
    private boolean[][][] hidden;

    public Decorations(int width, int height) {
        wall = new int[width][height][4];
        text = new String[width][height][4];
        floor = new int[width][height];
        door = new int[width][height];
        doorButton = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Arrays.fill(wall[x][y], -1);
                floor[x][y] = -1;
                door[x][y] = -1;
            }
        }
    }

    public static Decorations none(int width, int height) {
        return new Decorations(width, height);
    }

    private boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < floor.length && y < floor[0].length;
    }

    /** The unreadable inscription (global wall decoration 0), every text's decoration (#67). */
    public static final int INSCRIPTION = 0;

    /**
     * The decoration on the given side of wall square (x, y), or -1 (also while its inscription is hidden).
     * A side with a text is always the inscription: saves from before #67 kept -1 there on maps whose
     * list lacks it (#73).
     */
    public int wall(int x, int y, Direction side) {
        if (!inside(x, y) || !textVisible(x, y, side)) {
            return -1;
        }
        return text[x][y][side.ordinal()] != null ? INSCRIPTION : wall[x][y][side.ordinal()];
    }

    /** Inscription text on that wall side (lines separated by '\n'), or null (also while it is hidden). */
    public String inscription(int x, int y, Direction side) {
        return inside(x, y) && textVisible(x, y, side) ? text[x][y][side.ordinal()] : null;
    }

    /** Whether that wall side has an inscription, shown or hidden. */
    public boolean hasText(int x, int y, Direction side) {
        return inside(x, y) && text[x][y][side.ordinal()] != null;
    }

    /** Whether that side's inscription (if any) is shown: DM's text visible bit, changed by sensor effects (#55). */
    public boolean textVisible(int x, int y, Direction side) {
        return hidden == null || !inside(x, y) || !hidden[x][y][side.ordinal()];
    }

    public void setTextVisible(int x, int y, Direction side, boolean visible) {
        if (!inside(x, y)) {
            return;
        }
        if (hidden == null) {
            hidden = new boolean[floor.length][floor[0].length][4];
        }
        hidden[x][y][side.ordinal()] = !visible;
    }

    public int floor(int x, int y) {
        return inside(x, y) ? floor[x][y] : -1;
    }

    public int door(int x, int y) {
        return inside(x, y) ? door[x][y] : -1;
    }

    public boolean doorButton(int x, int y) {
        return inside(x, y) && doorButton[x][y];
    }

    public void setWall(int x, int y, Direction side, int ornament, String inscription) {
        wall[x][y][side.ordinal()] = ornament;
        text[x][y][side.ordinal()] = inscription;
    }

    public void setFloor(int x, int y, int ornament) {
        floor[x][y] = ornament;
    }

    public void setDoor(int x, int y, int ornament, boolean button) {
        door[x][y] = ornament;
        doorButton[x][y] = button;
    }
}
