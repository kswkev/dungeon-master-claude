package dm.data;

import dm.model.DungeonMap;
import dm.model.Item;
import dm.model.Square;
import dm.model.SquareType;

import java.util.List;

/**
 * Puts the dungeon's loose objects onto the map: every weapon, armour,
 * scroll, potion, chest and junk thing, on its thing's cell. On open squares
 * that's a floor cell; on wall squares the cell is a wall side, which is how
 * alcoves and torch holders hold things. A champion mirror's side is skipped:
 * those items are the champion's (see {@link ChampionFinder}).
 */
final class FloorItemFinder {

    private FloorItemFinder() {
    }

    static void place(DungeonMap map, Square[][] squares, List<List<Thing>>[] squareThings) {
        for (int x = 0; x < squares.length; x++) {
            for (int y = 0; y < squares[x].length; y++) {
                List<Thing> things = squareThings[x].get(y);
                boolean wall = squares[x][y].type() == SquareType.WALL;
                for (Thing t : things) {
                    if (wall && WallSensorFinder.isMirrorSide(things, t.cell())) {
                        continue;
                    }
                    Item item = ChampionFinder.toItem(t);
                    if (item != null) {
                        map.addItem(x, y, t.cell(), item);
                    }
                }
            }
        }
    }
}
