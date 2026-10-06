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
    /** DM's creature timeline, shared by every map. */
    private final CreatureAI creatures = new CreatureAI(this);

    public Dungeon(List<DungeonMap> maps) {
        this.maps = List.copyOf(maps);
        for (DungeonMap m : this.maps) {
            m.setDungeon(this);
        }
    }

    public List<DungeonMap> maps() {
        return maps;
    }

    CreatureAI creatures() {
        return creatures;
    }

    /** The party in this dungeon (for the clock, the random numbers and what it carries); null until it starts. */
    private Party party;
    /**
     * DM's inventory icon of an item (F032), for the sensors that name one;
     * icons come from GRAPHICS.DAT, so the UI supplies this. Not saved.
     */
    private transient java.util.function.ToIntFunction<Item> iconOf;

    Party party() {
        return party;
    }

    void setParty(Party party) {
        this.party = party;
    }

    public void setIconOf(java.util.function.ToIntFunction<Item> iconOf) {
        this.iconOf = iconOf;
    }

    /** The item's inventory icon, or -1 without one. */
    int iconOf(Item item) {
        return iconOf == null || item == null ? -1 : iconOf.applyAsInt(item);
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

    /**
     * The square at the same dungeon-wide position one level up, or null:
     * DM draws a hole in the ceiling where that square is an open pit.
     */
    public Location above(DungeonMap map, int x, int y) {
        int level = map.level() - 1;
        for (DungeonMap m : maps) {
            if (m == map || m.level() != level) {
                continue;
            }
            int tx = x + map.offsetX() - m.offsetX();
            int ty = y + map.offsetY() - m.offsetY();
            if (m.inBounds(tx, ty)) {
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
