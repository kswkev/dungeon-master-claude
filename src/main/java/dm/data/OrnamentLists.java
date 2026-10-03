package dm.data;

import java.io.IOException;

/**
 * A map's decoration ("ornament") lists. Things in the map refer to
 * decorations by 1-based ordinal into these lists; the lists hold global
 * ornament indexes into GRAPHICS.DAT's decoration art.
 *
 * The lists come straight after the map's squares: creature types, then wall,
 * floor and door ornaments, one byte each. Counts are in the map definition:
 * word B bits 0-3 wall, 4-7 random wall, 8-11 floor, 12-15 random floor;
 * word C bits 4-7 creature types, 0-3 door ornaments. The random decorations
 * are drawn from the first "random" entries of the wall and floor lists.
 */
record OrnamentLists(int[] creatures, int[] wall, int randomWall, int[] floor, int randomFloor, int[] door) {

    static OrnamentLists read(byte[] data, int start, int end, int ornamentCounts, int otherCounts)
            throws IOException {
        int creatures = (otherCounts >>> 4) & 15;
        int walls = ornamentCounts & 15;
        int floors = (ornamentCounts >>> 8) & 15;
        int doors = otherCounts & 15;
        if (start + creatures + walls + floors + doors > end) {
            throw new IOException("ornament lists run past the raw map data");
        }
        int[] creatureTypes = bytes(data, start, creatures);
        int at = start + creatures;
        int[] wall = bytes(data, at, walls);
        int[] floor = bytes(data, at + walls, floors);
        int[] door = bytes(data, at + walls + floors, doors);
        return new OrnamentLists(creatureTypes, wall, Math.min((ornamentCounts >>> 4) & 15, walls),
                floor, Math.min(ornamentCounts >>> 12, floors), door);
    }

    private static int[] bytes(byte[] data, int from, int count) {
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = data[from + i] & 0xFF;
        }
        return out;
    }

    /** Global index for a 1-based ordinal into {@code list}, or -1 for 0 / out of range. */
    static int global(int[] list, int ordinal) {
        return ordinal > 0 && ordinal <= list.length ? list[ordinal - 1] : -1;
    }

    /** The ordinal of the inscription decoration (global index 0) in the wall list, or 0. */
    int inscriptionOrdinal() {
        for (int i = 0; i < wall.length; i++) {
            if (wall[i] == 0) {
                return i + 1;
            }
        }
        return 0;
    }
}
