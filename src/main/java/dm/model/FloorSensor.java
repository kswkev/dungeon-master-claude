package dm.model;

import java.io.Serializable;

/**
 * A sensor on a floor square, such as a pressure plate, decoded from a
 * DUNGEON.DAT sensor thing.
 *
 * The sensor is pressed while something it reacts to is on its square: the
 * party, and for type 1 also any item. When it becomes pressed it applies its
 * effect to its target; when it is released, a HOLD sensor undoes it, and so
 * does any sensor with the revert flag. See {@link DungeonMap#partyMoved}.
 */
public final class FloorSensor implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Effect { SET, CLEAR, TOGGLE, HOLD }

    /** DM floor sensor types that this game handles. */
    public static final int TYPE_ANY = 1;
    public static final int TYPE_PARTY_OR_CREATURE = 2;
    public static final int TYPE_PARTY = 3;
    /** Creates a creature group on its square when an effect reaches it (see {@link DungeonMap}). */
    public static final int TYPE_GENERATOR = 6;
    public static final int TYPE_CREATURE = 7;

    private final int x;
    private final int y;
    private final int type;
    private final Effect effect;
    private final boolean onceOnly;
    private final boolean revert;
    private final boolean audible;
    private final int targetX;
    private final int targetY;
    private final int ornament;
    /** A generator's creature type, count (DM's value) and action word (health multiplier, re-arm delay). */
    private final int data;
    private final int value;
    private final int action;
    private boolean enabled = true;
    /** When a disabled generator comes back (DM's event 65), or -1. */
    private long enableAt = -1;
    private boolean pressed;

    /**
     * @param ornament global floor ornament index drawn on the square (e.g. 1 = square pressure plate), or -1
     */
    public FloorSensor(int x, int y, int type, Effect effect, boolean onceOnly, boolean revert, boolean audible,
                       int targetX, int targetY, int ornament) {
        this(x, y, type, effect, onceOnly, revert, audible, targetX, targetY, ornament, 0, 0, 0);
    }

    /**
     * @param data   word 1 bits 7-15: for a generator, the creature type
     * @param value  word 2 bits 7-10: for a generator, the creature count (bit 3: random up to bits 0-2)
     * @param action word 3, raw: for a generator, bits 4-7 the health multiplier (0: the map's
     *               difficulty) and bits 8-15 the ticks before it works again (over 127: (n - 126) x 64)
     */
    public FloorSensor(int x, int y, int type, Effect effect, boolean onceOnly, boolean revert, boolean audible,
                       int targetX, int targetY, int ornament, int data, int value, int action) {
        this.x = x;
        this.y = y;
        this.type = type;
        this.effect = effect;
        this.onceOnly = onceOnly;
        this.revert = revert;
        this.audible = audible;
        this.targetX = targetX;
        this.targetY = targetY;
        this.ornament = ornament;
        this.data = data;
        this.value = value;
        this.action = action;
    }

    public int data() {
        return data;
    }

    public int value() {
        return value;
    }

    public int action() {
        return action;
    }

    /** A generator that has just made its creatures rests until {@code time}. */
    void disableUntil(long time) {
        enabled = false;
        enableAt = time;
    }

    /** Brings a resting generator back once its time has come. Returns whether it did. */
    boolean reenable(long now) {
        if (!enabled && enableAt >= 0 && now >= enableAt) {
            enabled = true;
            enableAt = -1;
            return true;
        }
        return false;
    }

    /** Once-only sensors (and generators) switch off for good. */
    void disable() {
        enabled = false;
    }

    /**
     * Whether the party standing here sets the sensor off. Type 1/2 sensors react
     * to any party. Party sensors (type 3) need at least one champion: in DM an
     * empty party is just the ghost Theron, who can't press them. Other types
     * (objects, creatures, items carried) aren't modelled yet.
     */
    public boolean triggeredBy(Party party) {
        if (!enabled) {
            return false;
        }
        return switch (type) {
            case TYPE_ANY, TYPE_PARTY_OR_CREATURE -> true;
            case TYPE_PARTY -> !party.members().isEmpty();
            default -> false;
        };
    }

    /**
     * Whether items lying on the square press the sensor: only DM's type 1
     * ("anything"). Type 2 reacts to the party and creatures, type 3 to the
     * party alone.
     */
    public boolean acceptsItems() {
        return enabled && type == TYPE_ANY;
    }

    /**
     * Whether a (walking) creature on the square presses the sensor: DM's
     * type 1 ("anything"), type 2 (party or creature) and type 7 (creature).
     */
    public boolean acceptsCreatures() {
        return enabled && (type == TYPE_ANY || type == TYPE_PARTY_OR_CREATURE || type == TYPE_CREATURE);
    }

    /** Whether the sensor is held down (party or items on it), as of the last change on its square. */
    public boolean pressed() {
        return pressed;
    }

    void setPressed(boolean pressed) {
        this.pressed = pressed;
    }

    /** Once-only sensors switch off after their first use. */
    void used() {
        if (onceOnly) {
            enabled = false;
        }
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int type() {
        return type;
    }

    public Effect effect() {
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

    public int targetX() {
        return targetX;
    }

    public int targetY() {
        return targetY;
    }

    public int ornament() {
        return ornament;
    }

    public boolean enabled() {
        return enabled;
    }
}
