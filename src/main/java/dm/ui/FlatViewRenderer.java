package dm.ui;

import dm.data.GraphicsFile;
import dm.model.ChampionMirror;
import dm.model.Direction;
import dm.model.DungeonMap;
import dm.model.Explosion;
import dm.model.Party;
import dm.model.Projectile;
import dm.model.Square;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;

/**
 * Fallback first-person view for when GRAPHICS.DAT is missing: a
 * 3-squares-deep cone of the map, drawn back to front with flat-shaded
 * polygons. {@link TexturedViewRenderer} draws the original art instead.
 *
 * View space: the party stands at the centre of square (depth 0, lateral 0),
 * looking down +z. A square at (depth d, lateral l) spans z in [d-0.5, d+0.5]
 * and x in [l-0.5, l+0.5]. Walls span y in [-0.5, 0.5] (floor to ceiling).
 */
public final class FlatViewRenderer implements ViewRenderer {

    private static final int MAX_DEPTH = 3;
    /** How many squares either side are visible at each depth. */
    private static final int[] LATERAL_REACH = {1, 1, 2, 3};
    /**
     * The eye sits this far behind the centre of its square. That gives DM's
     * gentle ~1.6x shrink per square instead of a steep pinhole falloff, and
     * keeps every visible z in front of the eye.
     */
    private static final double EYE_BACK = 1.17;
    private static final double FOCAL_X = 250;
    private static final double FOCAL_Y = 167;

    private static final Color WALL = new Color(132, 128, 118);
    private static final Color MORTAR = new Color(70, 66, 60);
    private static final Color DOOR_WOOD = new Color(120, 78, 40);
    private static final Color DOOR_FRAME = new Color(96, 96, 104);
    private static final Color TELEPORTER = new Color(80, 140, 255, 90);

    /** Mirror graphic size, and where the 32x29 portrait sits inside it. */
    private static final int MIRROR_W = 48;
    private static final int MIRROR_H = 43;
    private static final int GLASS_X = 8;
    private static final int GLASS_Y = 6;
    private static final int PORTRAIT_W = 32;
    private static final int PORTRAIT_H = 29;

    private final double cx = VIEWPORT.x + VIEWPORT.width / 2.0;
    private final double cy = VIEWPORT.y + VIEWPORT.height / 2.0;

    private final Art art;
    private DungeonMap map;
    private Direction fwd;
    private Rectangle portraitHit;
    private Rectangle wallHit;
    private Rectangle doorButtonHit;

    public FlatViewRenderer(Art art) {
        this.art = art;
    }

    @Override
    public Rectangle portraitHit() {
        return portraitHit;
    }

    @Override
    public Rectangle wallHit() {
        return wallHit;
    }

    @Override
    public Rectangle doorButtonHit() {
        return doorButtonHit;
    }

    /** Without DM's art the piles aren't measured: DM's drop boxes (G0462) stand in for them. */
    @Override
    public Rectangle pileHit(int viewCell) {
        return GameScreen.PILE_BOXES[viewCell];
    }

    @Override
    public void draw(Graphics2D g, Party party) {
        Shape oldClip = g.getClip();
        g.clipRect(VIEWPORT.x, VIEWPORT.y, VIEWPORT.width, VIEWPORT.height);

        drawFloorAndCeiling(g);

        map = party.map();
        fwd = party.facing();
        portraitHit = null;
        wallHit = null;
        doorButtonHit = null;
        Direction right = fwd.turnRight();
        for (int d = MAX_DEPTH; d >= 0; d--) {
            int reach = LATERAL_REACH[d];
            // Outermost squares first so nearer-the-centre faces overdraw them.
            for (int a = reach; a >= 0; a--) {
                for (int l : a == 0 ? new int[] {0} : new int[] {-a, a}) {
                    int mx = party.x() + fwd.dx * d + right.dx * l;
                    int my = party.y() + fwd.dy * d + right.dy * l;
                    drawSquare(g, map.get(mx, my), d, l, mx, my);
                }
            }
        }

        g.setClip(oldClip);
    }

    private void drawFloorAndCeiling(Graphics2D g) {
        int top = VIEWPORT.y;
        int mid = (int) cy;
        int bottom = VIEWPORT.y + VIEWPORT.height;
        g.setPaint(new GradientPaint(0, top, new Color(58, 56, 54), 0, mid, Color.BLACK));
        g.fillRect(VIEWPORT.x, top, VIEWPORT.width, mid - top);
        g.setPaint(new GradientPaint(0, mid, Color.BLACK, 0, bottom, new Color(84, 72, 58)));
        g.fillRect(VIEWPORT.x, mid, VIEWPORT.width, bottom - mid);
    }

    private void drawSquare(Graphics2D g, Square sq, int d, int l, int mx, int my) {
        if (sq.looksSolid()) {
            drawWallBlock(g, d, l);
            if (d > 0 && l == 0) {
                drawDecoration(g, d, mx, my);
            }
            ChampionMirror mirror = d > 0 ? map.mirrorAt(mx, my, fwd.opposite()) : null;
            if (mirror != null) {
                drawMirror(g, d, l, mirror);
            }
            return;
        }
        if (d == 0 && l == 0) {
            drawItems(g, d, l, mx, my); // only the cells ahead of the party show
            return;
        }
        if (map.floorOrnament(mx, my) >= 0) {
            drawPlate(g, d, l);
        }
        switch (sq.type()) {
            case PIT -> {
                if (map.isPitOpen(mx, my) && !sq.pitInvisible()) {
                    drawPit(g, d, l);
                }
            }
            case STAIRS -> drawStairs(g, d, l, sq.stairsUp());
            case DOOR -> drawDoor(g, d, l, map.isPassable(mx, my), map.decorations().doorButton(mx, my));
            case TELEPORTER -> {
                if (sq.teleporterVisible() && map.isTeleporterOpen(mx, my)) {
                    drawTeleporter(g, d, l, mx, my);
                }
            }
            default -> { }
        }
        drawItems(g, d, l, mx, my);
    }

    /**
     * Objects as small markers on their cells, far cells first: a lying item
     * is a pebble on the floor, a thrown one a dot at eye level.
     */
    private void drawItems(Graphics2D g, int d, int l, int mx, int my) {
        for (int viewCell : new int[] {0, 1, 3, 2}) {
            boolean near = viewCell >= 2;
            if (d == 0 && near) {
                continue;
            }
            double x = l + (viewCell == 0 || viewCell == 3 ? -0.25 : 0.25);
            double z = d + (near ? -0.25 : 0.25);
            int r = Math.max(1, (int) Math.round(6 / (z + EYE_BACK)));
            if (!map.itemsAt(mx, my, fwd.cellOf(viewCell)).isEmpty()) {
                g.setColor(shade(new Color(200, 170, 90), z, 1));
                g.fillOval(sx(x, z) - r, sy(-0.5, z) - r, 2 * r, r + 1);
            }
            for (Projectile p : map.projectiles()) {
                if (p.x() == mx && p.y() == my && fwd.viewCellOf(p.cell()) == viewCell) {
                    g.setColor(p.isSpell() ? new Color(255, 140, 40) : new Color(230, 230, 200));
                    g.fillOval(sx(x, z) - r / 2, sy(0, z) - r / 2, r, r);
                }
            }
        }
        // Explosions: a puff over the square, red for fire, green for poison, grey for smoke.
        for (Explosion e : map.explosionsAt(mx, my)) {
            double z = Math.max(d, 0.6);
            int r = Math.max(2, (int) Math.round(Math.max(48, e.attack()) / 6.0 / (z + EYE_BACK)));
            g.setColor(switch (e.type()) {
                case Explosion.FIREBALL, Explosion.LIGHTNING_BOLT -> new Color(255, 90, 30, 200);
                case Explosion.POISON_BOLT, Explosion.POISON_CLOUD -> new Color(80, 200, 60, 170);
                case Explosion.SMOKE -> new Color(150, 150, 150, 170);
                default -> new Color(120, 160, 255, 190);
            });
            g.fillOval(sx(l, z) - r, sy(0, z) - r, 2 * r, 2 * r);
        }
    }

    // ---- walls ----------------------------------------------------------

    private void drawWallBlock(Graphics2D g, int d, int l) {
        double zNear = d - 0.5;
        double zFar = d + 0.5;
        // Side face that looks toward the centre line, visible for off-centre squares.
        if (l != 0) {
            double x = l < 0 ? l + 0.5 : l - 0.5;
            Polygon side = quad(x, zNear, x, zFar);
            g.setColor(shade(WALL, (zNear + zFar) / 2, 0.78));
            g.fillPolygon(side);
            g.setColor(shade(MORTAR, (zNear + zFar) / 2, 1));
            g.drawPolygon(side);
        }
        if (d > 0) {
            drawFrontWall(g, l - 0.5, l + 0.5, zNear);
        }
    }

    private void drawFrontWall(Graphics2D g, double x0, double x1, double z) {
        int left = sx(x0, z);
        int right = sx(x1, z);
        int top = sy(0.5, z);
        int bottom = sy(-0.5, z);
        g.setColor(shade(WALL, z, 1));
        g.fillRect(left, top, right - left, bottom - top);

        // Brick courses: 4 rows, joints offset on alternate rows.
        g.setColor(shade(MORTAR, z, 1));
        int rows = 4;
        for (int row = 0; row < rows; row++) {
            double yTop = 0.5 - row / (double) rows;
            double yBottom = 0.5 - (row + 1) / (double) rows;
            int ry = sy(yBottom, z);
            g.drawLine(left, ry, right, ry);
            double offset = (row % 2 == 0) ? 0.25 : 0.5;
            for (double bx = x0 + offset; bx < x1 - 0.01; bx += 0.5) {
                int px = sx(bx, z);
                g.drawLine(px, sy(yTop, z), px, ry);
            }
        }
        g.drawRect(left, top, right - left - 1, bottom - top - 1);
    }

    // ---- champion mirrors -------------------------------------------------

    /** Draws the mirror ornament (and portrait, if still there) centred on a front wall face. */
    private void drawMirror(Graphics2D g, int d, int l, ChampionMirror mirror) {
        double z = d - 0.5;
        double scale = (0.5 + EYE_BACK) / (z + EYE_BACK); // 1.0 on the adjacent wall
        int w = (int) Math.round(MIRROR_W * scale);
        int h = (int) Math.round(MIRROR_H * scale);
        int x = sx(l, z) - w / 2;
        int y = (int) Math.round(cy - h / 2.0 - 6 * scale);

        BufferedImage frame = art.sprite(GraphicsFile.MIRROR_FRONT);
        if (frame != null) {
            g.drawImage(frame, x, y, w, h, null);
        } else {
            g.setColor(Art.PALETTE[5]);
            g.fillRect(x, y, w, h);
            g.setColor(Art.PALETTE[4]);
            g.fillRect(x + scaled(GLASS_X, scale), y + scaled(GLASS_Y, scale),
                    scaled(PORTRAIT_W, scale), scaled(PORTRAIT_H, scale));
        }
        if (mirror.taken()) {
            return;
        }
        Rectangle glass = new Rectangle(x + scaled(GLASS_X, scale), y + scaled(GLASS_Y, scale),
                scaled(PORTRAIT_W, scale), scaled(PORTRAIT_H, scale));
        BufferedImage portrait = art.portrait(mirror.champion().portrait());
        if (portrait != null) {
            g.drawImage(portrait, glass.x, glass.y, glass.width, glass.height, null);
        } else {
            Placeholders.portrait(g, mirror.champion(), glass);
        }
        if (d == 1 && l == 0) {
            portraitHit = glass;
        }
    }

    private static int scaled(int v, double scale) {
        return (int) Math.round(v * scale);
    }

    // ---- doors, pits, stairs, teleporters --------------------------------

    private void drawDoor(Graphics2D g, int d, int l, boolean open, boolean button) {
        double z = d;
        double inset = 0.1;
        // Posts and lintel.
        g.setColor(shade(DOOR_FRAME, z, 1));
        fillFace(g, l - 0.5, l - 0.5 + inset, -0.5, 0.5, z);
        fillFace(g, l + 0.5 - inset, l + 0.5, -0.5, 0.5, z);
        fillFace(g, l - 0.5, l + 0.5, 0.5 - inset, 0.5, z);
        if (button) {
            // A small square on the right post.
            Rectangle b = new Rectangle(sx(l + 0.5 - inset * 0.8, z), sy(0.15, z),
                    Math.max(2, sx(l + 0.5 - inset * 0.2, z) - sx(l + 0.5 - inset * 0.8, z)),
                    Math.max(2, sy(0.05, z) - sy(0.15, z)));
            g.setColor(shade(new Color(170, 150, 60), z, 1));
            g.fillRect(b.x, b.y, b.width, b.height);
            if (d == 1 && l == 0) {
                doorButtonHit = b;
            }
        }
        if (open) {
            return;
        }
        double x0 = l - 0.5 + inset;
        double x1 = l + 0.5 - inset;
        g.setColor(shade(DOOR_WOOD, z, 1));
        fillFace(g, x0, x1, -0.5, 0.5 - inset, z);
        g.setColor(shade(DOOR_WOOD.darker(), z, 1));
        int planks = 5;
        for (int i = 1; i < planks; i++) {
            int px = sx(x0 + (x1 - x0) * i / planks, z);
            g.drawLine(px, sy(0.5 - inset, z), px, sy(-0.5, z));
        }
        g.setColor(shade(new Color(60, 60, 60), z, 1));
        fillFace(g, x0, x1, 0.15, 0.2, z);
        fillFace(g, x0, x1, -0.25, -0.2, z);
    }

    /** Wall decorations as a small dark plaque; inscriptions are written out on the wall straight ahead. */
    private void drawDecoration(Graphics2D g, int d, int mx, int my) {
        Direction front = fwd.opposite();
        String text = map.decorations().inscription(mx, my, front);
        double z = d - 0.5;
        Rectangle face = new Rectangle(sx(-0.5, z), sy(0.5, z), sx(0.5, z) - sx(-0.5, z), sy(-0.5, z) - sy(0.5, z));
        if (text != null && d == 1) {
            Inscription.draw(g, art, text, face);
        } else if (map.wallOrnament(mx, my, front) >= 0 && map.mirrorAt(mx, my, front) == null) {
            Polygon plaque = new Polygon();
            plaque.addPoint(sx(-0.15, z), sy(0.2, z));
            plaque.addPoint(sx(0.15, z), sy(0.2, z));
            plaque.addPoint(sx(0.15, z), sy(-0.05, z));
            plaque.addPoint(sx(-0.15, z), sy(-0.05, z));
            g.setColor(shade(MORTAR, z, 1));
            g.fillPolygon(plaque);
            if (d == 1) {
                wallHit = plaque.getBounds();
            }
        }
    }

    /** A floor plate or other floor ornament, as a flat grey slab. */
    private void drawPlate(Graphics2D g, int d, int l) {
        Polygon plate = floorQuad(l - 0.3, l + 0.3, d - 0.3, d + 0.3, -0.5);
        g.setColor(shade(new Color(150, 150, 150), d, 1));
        g.fillPolygon(plate);
        g.setColor(shade(MORTAR, d, 1));
        g.drawPolygon(plate);
    }

    private void drawPit(Graphics2D g, int d, int l) {
        double zNear = d - 0.4;
        double zFar = d + 0.4;
        Polygon hole = floorQuad(l - 0.4, l + 0.4, zNear, zFar, -0.5);
        g.setColor(Color.BLACK);
        g.fillPolygon(hole);
        g.setColor(shade(MORTAR, d, 1));
        g.drawPolygon(hole);
    }

    private void drawStairs(Graphics2D g, int d, int l, boolean up) {
        int steps = 4;
        if (up) {
            // Rising steps, drawn far to near so nearer treads overlap.
            for (int i = steps - 1; i >= 0; i--) {
                double z = d - 0.5 + (i + 0.5) / steps;
                double yTop = -0.5 + (i + 1) / (double) (steps + 1);
                g.setColor(shade(WALL, z, 0.9 - 0.08 * i));
                fillFace(g, l - 0.45, l + 0.45, -0.5, yTop, z);
                g.setColor(shade(MORTAR, z, 1));
                g.drawLine(sx(l - 0.45, z), sy(yTop, z), sx(l + 0.45, z), sy(yTop, z));
            }
        } else {
            double zNear = d - 0.45;
            double zFar = d + 0.45;
            g.setColor(new Color(20, 18, 16));
            g.fillPolygon(floorQuad(l - 0.45, l + 0.45, zNear, zFar, -0.5));
            g.setColor(shade(WALL, d, 0.7));
            for (int i = 1; i < steps; i++) {
                double z = zNear + (zFar - zNear) * i / steps;
                g.drawLine(sx(l - 0.45, z), sy(-0.5, z), sx(l + 0.45, z), sy(-0.5, z));
            }
        }
    }

    private void drawTeleporter(Graphics2D g, int d, int l, int mx, int my) {
        double z = d;
        g.setColor(TELEPORTER);
        fillFace(g, l - 0.5, l + 0.5, -0.5, 0.5, z);
        // Fixed sparkle pattern per square, so it doesn't flicker between repaints.
        long seed = mx * 31L + my * 17L;
        g.setColor(new Color(200, 230, 255, 160));
        for (int i = 0; i < 24; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            double px = l - 0.45 + ((seed >>> 33) & 0xFF) / 255.0 * 0.9;
            double py = -0.45 + ((seed >>> 45) & 0xFF) / 255.0 * 0.9;
            g.fillRect(sx(px, z), sy(py, z), 1, 1);
        }
    }

    // ---- projection helpers ----------------------------------------------

    private int sx(double x, double z) {
        return (int) Math.round(cx + x * FOCAL_X / (z + EYE_BACK));
    }

    private int sy(double y, double z) {
        return (int) Math.round(cy - y * FOCAL_Y / (z + EYE_BACK));
    }

    /** Vertical wall quad running from (x0, z0) to (x1, z1), floor to ceiling. */
    private Polygon quad(double x0, double z0, double x1, double z1) {
        Polygon p = new Polygon();
        p.addPoint(sx(x0, z0), sy(0.5, z0));
        p.addPoint(sx(x1, z1), sy(0.5, z1));
        p.addPoint(sx(x1, z1), sy(-0.5, z1));
        p.addPoint(sx(x0, z0), sy(-0.5, z0));
        return p;
    }

    private Polygon floorQuad(double x0, double x1, double zNear, double zFar, double y) {
        Polygon p = new Polygon();
        p.addPoint(sx(x0, zNear), sy(y, zNear));
        p.addPoint(sx(x1, zNear), sy(y, zNear));
        p.addPoint(sx(x1, zFar), sy(y, zFar));
        p.addPoint(sx(x0, zFar), sy(y, zFar));
        return p;
    }

    /** Axis-aligned rectangle facing the viewer at depth z. */
    private void fillFace(Graphics2D g, double x0, double x1, double y0, double y1, double z) {
        int left = sx(x0, z);
        int right = sx(x1, z);
        int top = sy(y1, z);
        int bottom = sy(y0, z);
        g.fillRect(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
    }

    /** Darkens with distance, like DM's per-depth palettes. */
    private static Color shade(Color c, double z, double factor) {
        double f = factor / (1 + 0.45 * Math.max(0, z - 0.5));
        return new Color(
                clamp((int) (c.getRed() * f)),
                clamp((int) (c.getGreen() * f)),
                clamp((int) (c.getBlue() * f)),
                c.getAlpha());
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
