package dm.data;

import dm.model.FloorSensor;

/**
 * The attribute word (word 2) shared by floor and wall sensors, as the DM
 * Encyclopaedia documents it:
 * <pre>
 *   bits 0-1  unused (clear on all 660 sensors in the PC file)
 *   bit 2     once only
 *   bits 3-4  effect: 0 set, 1 clear, 2 toggle, 3 hold
 *   bit 5     revert
 *   bit 6     audible
 *   bits 7-10 delay before the effect (not modelled yet)
 *   bit 11    local
 *   bits 12-15 decoration ordinal
 * </pre>
 * Until #20 these were read two bits too low, which turned Level 2's HOLD
 * plate at (25,1) into "set + revert" and the CLEAR plate at (14,14) into a
 * SET that couldn't close its pit.
 */
final class SensorBits {

    private SensorBits() {
    }

    static boolean onceOnly(int attributes) {
        return (attributes & 0x04) != 0;
    }

    static FloorSensor.Effect effect(int attributes) {
        return FloorSensor.Effect.values()[(attributes >>> 3) & 3];
    }

    static boolean revert(int attributes) {
        return (attributes & 0x20) != 0;
    }

    static boolean audible(int attributes) {
        return (attributes & 0x40) != 0;
    }
}
