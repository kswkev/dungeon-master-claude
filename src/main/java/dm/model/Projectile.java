package dm.model;

import java.io.Serializable;

/**
 * Something in flight (DM's projectile thing, F212): an item, or a spell
 * (an explosion type, which bursts where it hits). It flies along
 * {@code direction}, half a square per move: from a back cell of a square to
 * the front cell on the same side, then into the next square's back cell
 * (cells are absolute: 0 NW, 1 NE, 2 SE, 3 SW). Every move after the first
 * it may hit a champion or creature on its cell, and it spends
 * {@code stepEnergy} of its kinetic energy and attack; once its energy is
 * spent it drops (or, for a spell, fizzles out) where it is.
 */
public final class Projectile implements Serializable {

    private static final long serialVersionUID = 2L;

    private final Item item;
    /** The {@link Explosion} type of a spell in flight; -1 for an item (field missing, so 0, in pre-Sprint 19 saves, which only had items). */
    private final int spell;
    /** Whether {@link #spell} is set: false in saves from before Sprint 19. */
    private final boolean isSpell;
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
        this(item, -1, x, y, cell, direction, kineticEnergy, attack, stepEnergy, nextMove);
    }

    /** A spell in flight: explosion type {@code spell}. */
    Projectile(int spell, int x, int y, int cell, Direction direction, int kineticEnergy, int attack, int stepEnergy,
               long nextMove) {
        this(null, spell, x, y, cell, direction, kineticEnergy, attack, stepEnergy, nextMove);
    }

    private Projectile(Item item, int spell, int x, int y, int cell, Direction direction, int kineticEnergy, int attack,
                       int stepEnergy, long nextMove) {
        this.item = item;
        this.spell = spell;
        this.isSpell = spell >= 0;
        this.x = x;
        this.y = y;
        this.cell = cell & 3;
        this.direction = direction;
        this.kineticEnergy = Math.min(kineticEnergy, 255);
        this.attack = attack;
        this.stepEnergy = stepEnergy;
        this.nextMove = nextMove;
    }

    /** The item in flight, or null for a spell. */
    public Item item() {
        return item;
    }

    /** Whether this is a spell rather than an item. */
    public boolean isSpell() {
        return isSpell;
    }

    /** The spell's {@link Explosion} type; -1 for an item. */
    public int spell() {
        return isSpell ? spell : -1;
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
        return (isSpell ? "spell " + spell : item.name()) + " at (" + x + "," + y + ") cell " + cell + " flying "
                + direction + ", energy " + kineticEnergy + ", attack " + attack;
    }
}
