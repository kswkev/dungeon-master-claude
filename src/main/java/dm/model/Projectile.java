package dm.model;

/**
 * A thrown item in flight. It travels along {@code direction} one square per
 * game tick and keeps to its side of the corridor: {@code cell} is the
 * absolute cell (0 NW, 1 NE, 2 SE, 3 SW) it lands in when it stops.
 *
 * @param range squares it may still travel
 */
public record Projectile(Item item, int x, int y, Direction direction, int cell, int range) {

    Projectile advance() {
        return new Projectile(item, x + direction.dx, y + direction.dy, direction, cell, range - 1);
    }
}
