package dm.data;

import dm.model.Decorations;
import dm.model.Direction;
import dm.model.Square;
import dm.model.SquareType;

import java.util.List;

/**
 * Works out which decoration sits on every wall side, floor square and door
 * of a map, following DM's rules as reconstructed in ReDMCSB (F0169-F0172):
 *
 * <ul>
 *   <li><b>Random decorations.</b> A wall square allows one per side when
 *       bits 3/2/1/0 (north/east/south/west) are set; a corridor allows one
 *       when bit 3 is set. A seeded hash of the square's position picks an
 *       index 0-29; below the map's random count it selects that entry of
 *       the decoration list, otherwise the spot stays plain.</li>
 *   <li><b>Explicit decorations</b> override them: a wall sensor's ornament
 *       ordinal (attribute bits 12-15) on that side, and a visible text,
 *       which becomes an inscription. A champion mirror's side carries the
 *       mirror frame (its sensor's ornament ordinal, else decoration 43).</li>
 *   <li><b>Doors:</b> the door record's bits 1-4 are an ordinal into the
 *       map's door decoration list; bit 6 means the door has a button.</li>
 * </ul>
 */
final class DecorationFinder {

    private static final int MODULO = 30;
    private static final int SENSOR_CHAMPION_PORTRAIT = 127;
    /** The champion mirror's global wall decoration: side view 345, front view {@link GraphicsFile#MIRROR_FRONT}. */
    static final int MIRROR_ORNAMENT = (GraphicsFile.MIRROR_FRONT - 260) / 2;

    private final OrnamentLists lists;
    private final int seed;
    private final int mapIndex;
    private final int[] text;

    DecorationFinder(OrnamentLists lists, int seed, int mapIndex, int[] text) {
        this.lists = lists;
        this.seed = seed;
        this.mapIndex = mapIndex;
        this.text = text;
    }

    /**
     * DM's ornament hash, in 16-bit unsigned arithmetic like the original:
     * ((((v1 * 31417) >> 1) + v2 * 11 + seed) >> 2) % modulo.
     */
    static int randomIndex(int v1, int v2, int seed, int modulo) {
        int t = ((v1 & 0xFFFF) * 31417) & 0xFFFF;
        t >>>= 1;
        t = (t + (v2 & 0xFFFF) * 11 + seed) & 0xFFFF;
        t >>>= 2;
        return t % modulo;
    }

    /** DM's F0170: a 1-based ordinal into the list, or 0 for no decoration. */
    static int randomOrdinal(boolean allowed, int count, int x, int y, int seed, int mapIndex, int width, int height) {
        if (!allowed) {
            return 0;
        }
        int index = randomIndex(2000 + (x << 5) + y, 3000 + (mapIndex << 6) + width + height, seed, MODULO);
        return index < count ? index + 1 : 0;
    }

    Decorations find(Square[][] squares, List<List<Thing>>[] squareThings) {
        int width = squares.length;
        int height = width == 0 ? 0 : squares[0].length;
        Decorations deco = new Decorations(width, height);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Square sq = squares[x][y];
                List<Thing> things = squareThings[x].get(y);
                if (sq.type() == SquareType.WALL || sq.type() == SquareType.FAKEWALL) {
                    wall(deco, sq, things, x, y, width, height);
                } else if (sq.type() == SquareType.CORRIDOR) {
                    int ordinal = randomOrdinal((sq.raw() & 0x08) != 0, lists.randomFloor(), x, y,
                            seed, mapIndex, width, height);
                    deco.setFloor(x, y, OrnamentLists.global(lists.floor(), ordinal));
                } else if (sq.type() == SquareType.DOOR) {
                    for (Thing t : things) {
                        if (t.type() == Thing.DOOR) {
                            int attributes = t.words()[1];
                            deco.setDoor(x, y, OrnamentLists.global(lists.door(), (attributes >>> 1) & 15),
                                    (attributes & 0x40) != 0);
                            break;
                        }
                    }
                }
            }
        }
        return deco;
    }

    private void wall(Decorations deco, Square sq, List<Thing> things, int x, int y, int width, int height) {
        for (Direction side : Direction.values()) {
            // DM's left/front/right bookkeeping reduces to: side s uses row (y+1)*(s+1).
            boolean allowed = (sq.raw() & (0x08 >> side.ordinal())) != 0;
            int ordinal = randomOrdinal(allowed, lists.randomWall(), x, (y + 1) * (side.ordinal() + 1),
                    seed, mapIndex, width, height);
            deco.setWall(x, y, side, OrnamentLists.global(lists.wall(), ordinal), null);
        }
        for (Thing t : things) {
            Direction side = Direction.fromIndex(t.cell());
            if (t.type() == Thing.SENSOR && (t.words()[1] & 0x7F) == SENSOR_CHAMPION_PORTRAIT) {
                // The mirror frame is this side's decoration, so side views show it; the front
                // view, with the champion's portrait, is drawn by the renderer's mirror path.
                int mirror = OrnamentLists.global(lists.wall(), t.words()[2] >>> 12);
                deco.setWall(x, y, side, mirror >= 0 ? mirror : MIRROR_ORNAMENT, null);
            } else if (t.type() == Thing.SENSOR) {
                int ordinal = t.words()[2] >>> 12;
                if (ordinal > 0) {
                    deco.setWall(x, y, side, OrnamentLists.global(lists.wall(), ordinal), null);
                }
            } else if (t.type() == Thing.TEXT) {
                // A hidden text (visible bit 0 clear) is kept too: sensor effects can show it (#55).
                String inscription = TextDecoder.decode(text, t.words()[1] >>> 3);
                deco.setWall(x, y, side, OrnamentLists.global(lists.wall(), lists.inscriptionOrdinal()), inscription);
                deco.setTextVisible(x, y, side, (t.words()[1] & 1) != 0);
            }
        }
    }
}
