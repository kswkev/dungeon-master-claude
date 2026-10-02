package dm.model;

/** A single dungeon level. Squares are addressed as [x][y]; anything out of bounds is solid. */
public final class DungeonMap {

    private final int level;
    private final Square[][] squares;
    private final int width;
    private final int height;

    public DungeonMap(int level, Square[][] squares) {
        this.level = level;
        this.squares = squares;
        this.width = squares.length;
        this.height = width == 0 ? 0 : squares[0].length;
    }

    /** Builds a map from rows of characters; see {@link #charFor(Square)} for the legend. */
    public static DungeonMap fromAscii(int level, String... rows) {
        int h = rows.length;
        int w = rows[0].length();
        Square[][] sq = new Square[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                sq[x][y] = squareFor(rows[y].charAt(x));
            }
        }
        return new DungeonMap(level, sq);
    }

    public int level() {
        return level;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    public Square get(int x, int y) {
        return inBounds(x, y) ? squares[x][y] : Square.SOLID;
    }

    public boolean isPassable(int x, int y) {
        return inBounds(x, y) && squares[x][y].isPassable();
    }

    public String toAscii(Party party) {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (party != null && party.x() == x && party.y() == y) {
                    sb.append("^>v<".charAt(party.facing().ordinal()));
                } else {
                    sb.append(charFor(squares[x][y]));
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    static char charFor(Square s) {
        return switch (s.type()) {
            case WALL -> '#';
            case CORRIDOR -> '.';
            case PIT -> 'O';
            case STAIRS -> 'S';
            case DOOR -> s.isDoorOpen() ? 'd' : 'D';
            case TELEPORTER -> 'T';
            case FAKEWALL -> 'F';
        };
    }

    static Square squareFor(char c) {
        int element = switch (c) {
            case '.', ' ' -> 1;
            case 'O' -> 2;
            case 'S' -> 3;
            case 'D', 'd' -> 4;
            case 'T' -> 5;
            case 'F' -> 6;
            default -> 0;
        };
        int attrs = switch (c) {
            case 'D' -> 4; // closed door state
            case 'O' -> 8; // open pit
            default -> 0;
        };
        return new Square((element << 5) | attrs);
    }
}
