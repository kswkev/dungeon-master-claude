package dm.model;

import java.io.Serializable;
import java.util.List;

/**
 * Every level of the dungeon, and the ways between them: stairs, pits and
 * teleporters. Maps sit in dungeon-wide coordinates ({@link DungeonMap#offsetX()}),
 * so stairs and pits lead to the same dungeon-wide square one level up or down.
 */
public final class Dungeon implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<DungeonMap> maps;

    public Dungeon(List<DungeonMap> maps) {
        this.maps = List.copyOf(maps);
        for (DungeonMap m : this.maps) {
            m.setDungeon(this);
        }
    }

    public List<DungeonMap> maps() {
        return maps;
    }

    /** The map at {@code index} in DUNGEON.DAT's order (teleporters name maps this way), or null. */
    public DungeonMap map(int index) {
        return index >= 0 && index < maps.size() ? maps.get(index) : null;
    }

    /** A square on one of the maps. */
    public record Location(DungeonMap map, int x, int y) {
    }

    /**
     * Where something falling through (x, y) lands: the square at the same
     * dungeon-wide position one level down, or null if there is none (or
     * only solid rock). A level can be split over several maps.
     */
    public Location below(DungeonMap map, int x, int y) {
        int level = map.level() + 1;
        for (DungeonMap m : maps) {
            if (m == map || m.level() != level) {
                continue;
            }
            int tx = x + map.offsetX() - m.offsetX();
            int ty = y + map.offsetY() - m.offsetY();
            if (m.inBounds(tx, ty) && !m.get(tx, ty).looksSolid()) {
                return new Location(m, tx, ty);
            }
        }
        return null;
    }

    /** Where the stairs at (sx, sy) lead: the partner stairs one level up or down, or null. */
    public Location stairsPartner(DungeonMap map, int sx, int sy, boolean up) {
        int level = map.level() + (up ? -1 : 1);
        for (DungeonMap m : maps) {
            if (m == map || m.level() != level) {
                continue;
            }
            int tx = sx + map.offsetX() - m.offsetX();
            int ty = sy + map.offsetY() - m.offsetY();
            if (m.get(tx, ty).type() == SquareType.STAIRS && m.stairsExit(tx, ty) != null) {
                return new Location(m, tx, ty);
            }
        }
        return null;
    }
}
