package dm.model;

import java.io.Serializable;

/**
 * A sensor on one side of a wall square: switches, buttons, keyholes, coin
 * slots, torch holders and the logic gates behind them, decoded from a
 * DUNGEON.DAT sensor thing.
 *
 * A sensor that fires either acts on its own wall side ("local": rotating
 * the side's sensors, which changes the decoration shown, e.g. a lever going
 * up or down) or sends its effect to a target square: a door, a pit, or the
 * gates on another wall square. See {@link DungeonMap#clickWall}.
 */
public final class WallSensor implements Serializable {

    private static final long serialVersionUID = 1L;

    /** DM's wall sensor types handled here. */
    public static final int TYPE_DISABLED = 0;
    public static final int TYPE_CLICK = 1;
    public static final int TYPE_CLICK_WITH_ANY_ITEM = 2;
    public static final int TYPE_CLICK_WITH_ITEM = 3;
    public static final int TYPE_CLICK_WITH_ITEM_USED_UP = 4;
    public static final int TYPE_AND_OR_GATE = 5;
    /** F248's C006: counts effects down (CLEAR, TOGGLE) and up (SET); at 0 it fires. */
    public static final int TYPE_COUNTDOWN = 6;
    /** F247's launchers: a new object (7 single, 9 double), a spell (8, 10), or the wall's own objects (14, 15). */
    public static final int TYPE_LAUNCHER_OBJECT = 7;
    public static final int TYPE_LAUNCHER_SPELL = 8;
    public static final int TYPE_LAUNCHER_OBJECT_DOUBLE = 9;
    public static final int TYPE_LAUNCHER_SPELL_DOUBLE = 10;
    public static final int TYPE_LAUNCHER_WALL_OBJECT = 14;
    public static final int TYPE_LAUNCHER_WALL_OBJECT_DOUBLE = 15;
    public static final int TYPE_STORAGE_ROTATE = 13;
    /** F275's C016: swaps the held item (the one named) for the object on the wall square. */
    public static final int TYPE_OBJECT_EXCHANGER = 16;
    /** F275's C017: like type 4 (the item is used up), and the sensor removes itself. */
    public static final int TYPE_CLICK_WITH_ITEM_REMOVE_SENSOR = 17;

    /** Local actions: anything but adding experience rotates the side's sensors. */
    public static final int ACTION_ADD_EXPERIENCE = 10;

    private final int x;
    private final int y;
    private final Direction side;
    private final int type;
    private final int data;
    private final FloorSensor.Effect effect;
    private final boolean onceOnly;
    private final boolean revert;
    private final boolean audible;
    private final boolean local;
    private final int localAction;
    private final int targetX;
    private final int targetY;
    private final int targetCell;
    private final int ornament;
    private boolean enabled = true;
    /** AND/OR gate inputs (bits 0-3); starts from the data's low nibble. */
    private int gateValue;

    /**
     * @param data        the type's parameter: for item sensors the required
     *                    item's inventory icon number; for gates the start
     *                    value (bits 0-3) and target value (bits 4-7)
     * @param localAction for local sensors, what to do (see {@link #ACTION_ADD_EXPERIENCE})
     * @param ornament    global wall ornament index this sensor shows, or -1
     */
    public WallSensor(int x, int y, Direction side, int type, int data, FloorSensor.Effect effect,
                      boolean onceOnly, boolean revert, boolean audible, boolean local, int localAction,
                      int targetX, int targetY, int targetCell, int ornament) {
        this.x = x;
        this.y = y;
        this.side = side;
        this.type = type;
        this.data = data;
        this.effect = effect;
        this.onceOnly = onceOnly;
        this.revert = revert;
        this.audible = audible;
        this.local = local;
        this.localAction = localAction;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetCell = targetCell;
        this.ornament = ornament;
        this.gateValue = data & 15;
    }

    /**
     * Feeds a gate: SET turns input bit {@code cell} on, CLEAR off, TOGGLE
     * flips it. Returns whether the inputs now equal the target value.
     */
    boolean gateInput(int cell, FloorSensor.Effect input) {
        int bit = 1 << (cell & 3);
        gateValue = switch (input) {
            case SET, HOLD -> gateValue | bit;
            case CLEAR -> gateValue & ~bit;
            case TOGGLE -> gateValue ^ bit;
        };
        return gateSatisfied();
    }

    boolean gateSatisfied() {
        return gateValue == ((data >>> 4) & 15);
    }

    /** Word 2 bits 7-10: the ticks its effect takes to reach its target (DM's sensor Value, F272). */
    private int delay;
    /** Word 3 bits 4-15 (DM's B.B.Multiple): a launcher's kinetic energy (bits 0-7) and step energy (8-11). */
    private int multiple;
    /** A countdown's count, from its data; null until it first counts (and in older saves). */
    private Integer count;

    public int delay() {
        return delay;
    }

    /** Set by the loader: the delay (word 2 bits 7-10) and DM's multiple (word 3 bits 4-15). */
    public void setTiming(int delay, int multiple) {
        this.delay = delay;
        this.multiple = multiple;
    }

    /** A launcher's kinetic energy: M47, the multiple's low byte (local sensors keep it as their action too). */
    int kineticEnergy() {
        return (multiple != 0 ? multiple : localAction) & 0xFF;
    }

    public boolean isLauncher() {
        return type >= TYPE_LAUNCHER_OBJECT && type <= TYPE_LAUNCHER_SPELL_DOUBLE
                || type == TYPE_LAUNCHER_WALL_OBJECT || type == TYPE_LAUNCHER_WALL_OBJECT_DOUBLE;
    }

    /** Whether the launcher fires one projectile (7, 8, 14) rather than two. */
    boolean singleLauncher() {
        return type == TYPE_LAUNCHER_OBJECT || type == TYPE_LAUNCHER_SPELL || type == TYPE_LAUNCHER_WALL_OBJECT;
    }

    /** A countdown's count now. */
    int count() {
        return count == null ? data : count;
    }

    /**
     * F248 for a countdown: while above 0 a SET counts up (to 511) and
     * anything else down. Returns whether it counted (it stays at 0 once there).
     */
    boolean countDown(FloorSensor.Effect effect) {
        int n = count();
        if (n == 0) {
            return false;
        }
        count = effect == FloorSensor.Effect.SET ? Math.min(511, n + 1) : n - 1;
        return true;
    }

    /** Once-only sensors switch off after firing. */
    void used() {
        if (onceOnly) {
            enabled = false;
        }
    }

    /** DM's M44: the sensor stops working for good. */
    void disable() {
        enabled = false;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public Direction side() {
        return side;
    }

    public int type() {
        return type;
    }

    public int data() {
        return data;
    }

    public FloorSensor.Effect effect() {
        return effect;
    }

    public boolean onceOnly() {
        return onceOnly;
    }

    public boolean revert() {
        return revert;
    }

    public boolean audible() {
        return audible;
    }

    public boolean local() {
        return local;
    }

    public int localAction() {
        return localAction;
    }

    public int targetX() {
        return targetX;
    }

    public int targetY() {
        return targetY;
    }

    public int targetCell() {
        return targetCell;
    }

    public int ornament() {
        return ornament;
    }

    public boolean enabled() {
        return enabled;
    }

    @Override
    public String toString() {
        return "WallSensor[" + x + "," + y + " " + side + " type " + type + " data " + data + " " + effect
                + (local ? " local " + localAction : " -> " + targetX + "," + targetY + " cell " + targetCell)
                + (audible ? " audible" : "") + (onceOnly ? " once" : "") + (revert ? " revert" : "")
                + " ornament " + ornament + (enabled ? "" : " disabled") + "]";
    }
}
