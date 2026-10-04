package dm.model;

import java.io.Serializable;

/**
 * An item in flight (DM's projectile thing, F212). It flies along
 * {@code direction}, half a square per move: from a back cell of a square to
 * the front cell on the same side, then into the next square's back cell
 * (cells are absolute: 0 NW, 1 NE, 2 SE, 3 SW). Every move after the first
 * it may hit a champion or creature on its cell, and it spends
 * {@code stepEnergy} of its kinetic energy and attack; once its energy is
 * spent it drops where it is.
 */
public final class Projectile implements Serializable {

    private static final long serialVersionUID = 2L;

    private final Item item;
    int x;
    int y;
    int cell;
    Direction direction;
    int kineticEnergy;
    int attack;
    final int stepEnergy;
    /** DM's event 48: a champion's or creature's projectile can't hit anything on its first move. */
    boolean ignoreImpacts = true;
    /** The game tick of its next move. */
    long nextMove;

    Projectile(Item item, int x, int y, int cell, Direction direction, int kineticEnergy, int attack, int stepEnergy,
               long nextMove) {
        this.item = item;
        this.x = x;
        this.y = y;
        this.cell = cell & 3;
        this.direction = direction;
        this.kineticEnergy = Math.min(kineticEnergy, 255);
        this.attack = attack;
        this.stepEnergy = stepEnergy;
        this.nextMove = nextMove;
    }

    public Item item() {
        return item;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    /** The absolute cell it is flying through. */
    public int cell() {
        return cell;
    }

    public Direction direction() {
        return direction;
    }

    public int kineticEnergy() {
        return kineticEnergy;
    }

    public int attack() {
        return attack;
    }

    @Override
    public String toString() {
        return item.name() + " at (" + x + "," + y + ") cell " + cell + " flying " + direction
                + ", energy " + kineticEnergy + ", attack " + attack;
    }
}
