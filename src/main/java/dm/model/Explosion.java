package dm.model;

import java.io.Serializable;

/**
 * One of DM's explosions (ReDMCSB PROJEXPL.C: F0213 creates it, F0220 runs
 * its event each tick): a burst on a cell of a square, or its centre
 * ({@link Group#CENTRED}), with an attack that sets how hard it hits and how
 * big it is drawn. Most last a single tick; a poison cloud lingers, hurting
 * whatever is in it while it thins, and smoke (left where a creature died)
 * fades by 40 a tick.
 */
public final class Explosion implements Serializable {

    private static final long serialVersionUID = 1L;

    /** DM's explosion types (the explosion thing's index, 0xFF80 + type). */
    public static final int FIREBALL = 0;
    public static final int SLIME = 1;
    public static final int LIGHTNING_BOLT = 2;
    public static final int HARM_NON_MATERIAL = 3;
    public static final int OPEN_DOOR = 4;
    public static final int POISON_BOLT = 6;
    public static final int POISON_CLOUD = 7;
    public static final int SMOKE = 40;
    /** A fluxcage (F224): no burst, it just stands for 100 ticks (DM's event 24 removes it). */
    public static final int FLUXCAGE = 50;
    /** A VI altar's rebirth sparkle (DM's C100, 5 ticks), then its burst (C101, 1 tick, the strong explosion sound). */
    public static final int REBIRTH_1 = 100;
    public static final int REBIRTH_2 = 101;

    private int type;
    private final int x;
    private final int y;
    private final int cell;
    private int attack;
    /** The game tick its next event is due (DM's explosion event 25). */
    long due;

    Explosion(int type, int x, int y, int cell, int attack, long due) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.cell = cell;
        this.attack = attack;
        this.due = due;
    }

    public int type() {
        return type;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    /** The absolute cell (0-3) it bursts on, or {@link Group#CENTRED}. */
    public int cell() {
        return cell;
    }

    public boolean centred() {
        return cell == Group.CENTRED;
    }

    /** DM's explosion attack: how hard it hits and how big it is drawn. */
    public int attack() {
        return attack;
    }

    void setType(int type) {
        this.type = type;
    }

    void setAttack(int attack) {
        this.attack = attack;
    }

    @Override
    public String toString() {
        return "explosion " + type + " at (" + x + "," + y + ") cell " + cell + ", attack " + attack;
    }
}
