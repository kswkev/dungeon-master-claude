package dm.model;

import java.io.Serializable;

/**
 * How far DM's sounds carry (ReDMCSB SOUND.C F064 with G060's distances, from
 * ScummVM's PC sound table). A sound made |dx| + |dy| squares from the party,
 * on its map, plays loud under its loud distance, soft under its soft
 * distance, and isn't heard beyond.
 */
public final class Sounds {

    /** A DM sound the party hears, loud or soft. */
    public record Heard(int dmSound, boolean soft) implements Serializable {
    }

    /** G060's loud and soft distances, by DM sound index (0-33). */
    private static final int[] LOUD = {
            3, 0, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 2, 1, 3, 3,
            0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final int[] SOFT = {
            6, 3, 6, 5, 6, 7, 6, 5, 6, 6, 6, 6, 6, 6, 5, 5, 4, 4, 6, 5,
            4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4};

    private Sounds() {
    }

    /** F064: how {@code dmSound} made (dx, dy) squares from the party is heard, or null if it isn't. */
    public static Heard hear(int dmSound, int dx, int dy) {
        if (dmSound < 0 || dmSound >= LOUD.length) {
            return null;
        }
        int distance = Math.abs(dx) + Math.abs(dy);
        if (distance < LOUD[dmSound]) {
            return new Heard(dmSound, false);
        }
        return distance < SOFT[dmSound] ? new Heard(dmSound, true) : null;
    }
}
