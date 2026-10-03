package dm.data;

import dm.model.Direction;
import dm.model.Square;
import dm.model.SquareType;
import dm.model.WallSensor;

import java.util.ArrayList;
import java.util.List;

/**
 * Decodes the sensor things on wall squares (switches, buttons, keyholes,
 * torch holders, gates), keeping their order on each side, which matters:
 * the last one's decoration is the one shown, and DM rotates them.
 *
 * Sensor record words after the link:
 * <pre>
 *   word 1  bits 0-6 type, bits 7-15 data (for item sensors, the required
 *           item's inventory icon number; for gates, the start and target values)
 *   word 2  bit 2 once only, bits 3-4 effect (set/clear/toggle/hold),
 *           bit 5 revert, bit 6 audible, bits 7-10 delay, bit 11 local,
 *           bits 12-15 wall ornament ordinal (1-based into the map's list)
 *   word 3  remote: bits 4-5 target cell, 6-10 target X, 11-15 target Y;
 *           local: bits 4-15 the action (10 = add experience, else rotate)
 * </pre>
 * The thing's cell is the wall side the sensor is on. Champion mirrors
 * (type 127) are handled by {@link ChampionFinder}.
 */
final class WallSensorFinder {

    private static final int SENSOR_CHAMPION_PORTRAIT = 127;

    private WallSensorFinder() {
    }

    static List<WallSensor> find(Square[][] squares, List<List<Thing>>[] squareThings, int[] wallOrnaments) {
        List<WallSensor> sensors = new ArrayList<>();
        for (int x = 0; x < squares.length; x++) {
            for (int y = 0; y < squares[x].length; y++) {
                if (squares[x][y].type() != SquareType.WALL) {
                    continue;
                }
                for (Thing t : squareThings[x].get(y)) {
                    if (t.type() == Thing.SENSOR && (t.words()[1] & 0x7F) != SENSOR_CHAMPION_PORTRAIT) {
                        sensors.add(decode(x, y, Direction.fromIndex(t.cell()), t.words(), wallOrnaments));
                    }
                }
            }
        }
        return sensors;
    }

    static WallSensor decode(int x, int y, Direction side, int[] words, int[] wallOrnaments) {
        int attributes = words[2];
        int target = words[3];
        boolean local = (attributes & 0x0800) != 0;
        return new WallSensor(x, y, side,
                words[1] & 0x7F,
                words[1] >>> 7,
                SensorBits.effect(attributes),
                SensorBits.onceOnly(attributes),
                SensorBits.revert(attributes),
                SensorBits.audible(attributes),
                local,
                local ? target >>> 4 : 0,
                (target >>> 6) & 0x1F,
                target >>> 11,
                (target >>> 4) & 3,
                OrnamentLists.global(wallOrnaments, attributes >>> 12));
    }

    /** Whether a wall side holds a champion mirror, whose items belong to the champion. */
    static boolean isMirrorSide(List<Thing> things, int cell) {
        for (Thing t : things) {
            if (t.type() == Thing.SENSOR && t.cell() == cell && (t.words()[1] & 0x7F) == SENSOR_CHAMPION_PORTRAIT) {
                return true;
            }
        }
        return false;
    }
}
