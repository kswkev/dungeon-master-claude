package dm.data;

import dm.model.FloorSensor;
import dm.model.Square;

import java.util.ArrayList;
import java.util.List;

/**
 * Decodes the sensor things on floor squares (pressure plates and the like).
 *
 * Sensor record words after the link:
 * <pre>
 *   word 1  bits 0-6 type, bits 7-15 data
 *   word 2  see {@link SensorBits}; bits 12-15 floor ornament ordinal
 *           (1-based into the map's list)
 *   word 3  bits 6-10 target X, bits 11-15 target Y
 * </pre>
 * The layout was checked against the original Level 1 plate at (6,9), which
 * decodes to "party, set, target door (5,9), square pressure plate".
 */
final class FloorSensorFinder {

    private FloorSensorFinder() {
    }

    static List<FloorSensor> find(Square[][] squares, List<List<Thing>>[] squareThings, int[] floorOrnaments) {
        List<FloorSensor> sensors = new ArrayList<>();
        for (int x = 0; x < squares.length; x++) {
            for (int y = 0; y < squares[x].length; y++) {
                if (squares[x][y].looksSolid()) {
                    continue; // wall sensors (switches, mirrors...) are a different kind
                }
                for (Thing t : squareThings[x].get(y)) {
                    if (t.type() == Thing.SENSOR) {
                        sensors.add(decode(x, y, t.words(), floorOrnaments));
                    }
                }
            }
        }
        return sensors;
    }

    static FloorSensor decode(int x, int y, int[] words, int[] floorOrnaments) {
        int type = words[1] & 0x7F;
        int attributes = words[2];
        int target = words[3];
        int ordinal = attributes >>> 12;
        int ornament = ordinal > 0 && ordinal <= floorOrnaments.length ? floorOrnaments[ordinal - 1] : -1;
        return new FloorSensor(x, y, type,
                SensorBits.effect(attributes),
                SensorBits.onceOnly(attributes),
                SensorBits.revert(attributes),
                SensorBits.audible(attributes),
                (target >>> 6) & 0x1F,
                target >>> 11,
                ornament);
    }
}
