package dm.data;

import dm.model.DungeonMap;
import dm.model.Item;
import dm.model.Square;
import dm.model.SquareType;

import java.util.List;

/**
 * Puts the objects lying in the dungeon onto the map: every weapon, armour,
 * scroll, potion, chest and junk thing on a square that isn't a wall, on its
 * thing's cell. Things on wall squares (a champion's belongings, alcove
 * contents) aren't on the floor and are left alone.
 */
final class FloorItemFinder {

    private FloorItemFinder() {
    }

    static void place(DungeonMap map, Square[][] squares, List<List<Thing>>[] squareThings) {
        for (int x = 0; x < squares.length; x++) {
            for (int y = 0; y < squares[x].length; y++) {
                if (squares[x][y].type() == SquareType.WALL) {
                    continue;
                }
                for (Thing t : squareThings[x].get(y)) {
                    Item item = ChampionFinder.toItem(t);
                    if (item != null) {
                        map.addItem(x, y, t.cell(), item);
                    }
                }
            }
        }
    }
}
