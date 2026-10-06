package dm.ui;

import dm.model.Direction;
import dm.model.DungeonMap;
import dm.model.Item;
import dm.model.ItemCatalog;
import dm.model.Party;
import dm.model.Square;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.TreeSet;

/**
 * The map (not in DM): a scroll button between the formation box and the
 * spell area opens a parchment over the dungeon view showing every square
 * the party has seen ({@link DungeonMap#seen}): walls, doors, pits,
 * teleporters and stairs as they looked, the party as a green arrow. Its
 * arrows page through the floors the party has seen. The game is paused
 * while it is open; any other click, or Esc, closes it.
 */
final class AutoMap {

    /** The scroll button: the free strip under the formation box, above the spell area's tabs. */
    static final Rectangle BUTTON = new Rectangle(233, 29, 16, 13);

    private static final Rectangle V = ViewRenderer.VIEWPORT;
    /** Where the squares go, in the viewport's left part. */
    static final Rectangle MAP_AREA = new Rectangle(V.x + 4, V.y + 4, 132, 128);
    static final Rectangle UP = new Rectangle(V.x + 160, V.y + 24, 40, 22);
    static final Rectangle DOWN = new Rectangle(V.x + 160, V.y + 52, 40, 22);
    private static final int PANEL_CENTRE = V.x + 180;
    private static final int LEGEND_X = V.x + 144;
    private static final int LEGEND_Y = V.y + 86;
    private static final int LEGEND_LINE = 9;

    static final Color PARCHMENT = new Color(0xD8C49A);
    static final Color FLOOR = new Color(0xF0E4C4);
    static final Color INK = new Color(0x4A3520);
    private static final Color FADED = new Color(0xB8A47C);
    private static final Color DOOR = new Color(0x9A4E1C);
    private static final Color DOOR_OPEN = new Color(0xC8A070);
    private static final Color PIT = new Color(0x101010);
    private static final Color TELEPORTER = new Color(0x3070D0);
    private static final Color STAIRS_UP = new Color(0xC0C0C0);
    private static final Color STAIRS_DOWN = new Color(0x707070);
    private static final Color STEP = new Color(0x404040);
    static final Color PARTY = new Color(0x00C000);

    private boolean open;
    /** The floor shown, as {@link DungeonMap#level()}. */
    private int level;

    boolean isOpen() {
        return open;
    }

    int level() {
        return level;
    }

    /** Opens on the party's floor. */
    void open(Party party) {
        open = true;
        level = party.level();
    }

    void close() {
        open = false;
    }

    /** The floors to page through: those with a seen square, and the party's own. */
    private static TreeSet<Integer> floors(Party party) {
        TreeSet<Integer> floors = new TreeSet<>();
        floors.add(party.level());
        for (DungeonMap m : party.dungeon().maps()) {
            if (m.explored()) {
                floors.add(m.level());
            }
        }
        return floors;
    }

    /** A click while the map is open: an arrow pages up or down a floor, anywhere else closes it. */
    void click(Party party, int x, int y) {
        TreeSet<Integer> floors = floors(party);
        if (UP.contains(x, y)) {
            Integer up = floors.lower(level);
            if (up != null) {
                level = up;
            }
        } else if (DOWN.contains(x, y)) {
            Integer down = floors.higher(level);
            if (down != null) {
                level = down;
            }
        } else {
            close();
        }
    }

    void drawButton(Graphics2D g, Art art) {
        drawScroll(g, scrollIcon(art), BUTTON, PARCHMENT);
    }

    /**
     * A scroll button in {@code box}: the icon, or without art a plain
     * scroll of colour {@code paper}.
     */
    static void drawScroll(Graphics2D g, BufferedImage icon, Rectangle box, Color paper) {
        if (icon == null) {
            g.setColor(paper);
            g.fillRect(box.x + 2, box.y + 2, box.width - 4, box.height - 4);
            g.setColor(INK);
            g.drawRect(box.x + 1, box.y + 1, box.width - 3, box.height - 3);
            return;
        }
        g.drawImage(icon, box.x, box.y + (box.height - icon.getHeight()) / 2, null);
    }

    /** DM's scroll icon, its middle rows if it is taller than the strip, or null without art. */
    static BufferedImage scrollIcon(Art art) {
        BufferedImage icon = art.iconSprite(ItemCatalog.item(Item.Category.SCROLL, 0));
        if (icon == null) {
            return null;
        }
        int top = icon.getHeight();
        int bottom = -1;
        for (int y = 0; y < icon.getHeight(); y++) {
            for (int x = 0; x < icon.getWidth(); x++) {
                if ((icon.getRGB(x, y) >>> 24) != 0) {
                    top = Math.min(top, y);
                    bottom = y;
                }
            }
        }
        if (bottom < 0) {
            return null;
        }
        int rows = bottom - top + 1;
        if (rows > BUTTON.height) {
            top += (rows - BUTTON.height) / 2;
            rows = BUTTON.height;
        }
        return icon.getSubimage(0, top, icon.getWidth(), rows);
    }

    /** The parchment over the dungeon view. */
    void draw(Graphics2D g, Party party, Art art) {
        g.setColor(PARCHMENT);
        g.fillRect(V.x, V.y, V.width, V.height);
        g.setColor(INK);
        g.drawRect(V.x + 1, V.y + 1, V.width - 3, V.height - 3);
        drawSquares(g, party);
        TreeSet<Integer> floors = floors(party);
        text(g, art, "LEVEL " + (level + 1), PANEL_CENTRE, V.y + 14, true);
        arrow(g, UP, Direction.NORTH, floors.lower(level) != null ? INK : FADED);
        arrow(g, DOWN, Direction.SOUTH, floors.higher(level) != null ? INK : FADED);
        legend(g, art, 0, "DOOR", FLOOR, null);
        fill(g, DOOR, LEGEND_X, LEGEND_Y - 2, 6, 2);
        legend(g, art, 1, "PIT", FLOOR, PIT);
        legend(g, art, 2, "TELEPORT", FLOOR, TELEPORTER);
        legend(g, art, 3, "STAIRS UP", null, null);
        stairs(g, true, LEGEND_X, LEGEND_Y + 3 * LEGEND_LINE - 4, 6);
        legend(g, art, 4, "STAIRS DOWN", null, null);
        stairs(g, false, LEGEND_X, LEGEND_Y + 4 * LEGEND_LINE - 4, 6);
    }

    /** Every seen square of the shown floor (which may span several maps), fitted to {@link #MAP_AREA}. */
    private void drawSquares(Graphics2D g, Party party) {
        List<DungeonMap> maps = party.dungeon().maps().stream()
                .filter(m -> m.level() == level && m.explored()).toList();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (DungeonMap m : maps) {
            for (int x = 0; x < m.width(); x++) {
                for (int y = 0; y < m.height(); y++) {
                    if (m.seen(x, y)) {
                        minX = Math.min(minX, x + m.offsetX());
                        maxX = Math.max(maxX, x + m.offsetX());
                        minY = Math.min(minY, y + m.offsetY());
                        maxY = Math.max(maxY, y + m.offsetY());
                    }
                }
            }
        }
        if (maxX < minX) {
            return;
        }
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        int tile = Math.max(2, Math.min(8, Math.min(MAP_AREA.width / w, MAP_AREA.height / h)));
        int left = MAP_AREA.x + (MAP_AREA.width - w * tile) / 2;
        int top = MAP_AREA.y + (MAP_AREA.height - h * tile) / 2;
        Rectangle clip = g.getClipBounds();
        g.clipRect(MAP_AREA.x, MAP_AREA.y, MAP_AREA.width, MAP_AREA.height);
        for (DungeonMap m : maps) {
            for (int x = 0; x < m.width(); x++) {
                for (int y = 0; y < m.height(); y++) {
                    if (m.seen(x, y)) {
                        square(g, m, x, y, left + (x + m.offsetX() - minX) * tile,
                                top + (y + m.offsetY() - minY) * tile, tile);
                    }
                }
            }
        }
        DungeonMap here = party.map();
        if (here.level() == level) {
            int cx = left + (party.x() + here.offsetX() - minX) * tile + tile / 2;
            int cy = top + (party.y() + here.offsetY() - minY) * tile + tile / 2;
            partyArrow(g, cx, cy, Math.max(4, tile * 2 / 3), party.facing());
        }
        g.setClip(clip);
    }

    /** One square as the party saw it: hidden pits and teleporters are floor, fake walls are walls. */
    private static void square(Graphics2D g, DungeonMap m, int x, int y, int px, int py, int t) {
        Square sq = m.get(x, y);
        int inset = t >= 4 ? Math.max(1, t / 5) : 0;
        switch (sq.type()) {
            case WALL, FAKEWALL -> fill(g, INK, px, py, t, t);
            case DOOR -> {
                fill(g, FLOOR, px, py, t, t);
                int state = m.doorState(x, y);
                g.setColor(state == DungeonMap.DOOR_OPEN || state == DungeonMap.DOOR_BROKEN ? DOOR_OPEN : DOOR);
                int bar = Math.max(1, t / 3);
                if (sq.runsNorthSouth()) { // the panel spans east-west
                    g.fillRect(px, py + (t - bar) / 2, t, bar);
                } else {
                    g.fillRect(px + (t - bar) / 2, py, bar, t);
                }
            }
            case PIT -> {
                fill(g, FLOOR, px, py, t, t);
                if (m.isPitOpen(x, y) && !sq.pitInvisible()) {
                    fill(g, PIT, px + inset, py + inset, t - 2 * inset, t - 2 * inset);
                }
            }
            case TELEPORTER -> {
                fill(g, FLOOR, px, py, t, t);
                if (m.isTeleporterOpen(x, y) && sq.teleporterVisible()) {
                    fill(g, TELEPORTER, px + inset, py + inset, t - 2 * inset, t - 2 * inset);
                }
            }
            case STAIRS -> stairs(g, sq.stairsUp(), px, py, t);
            default -> fill(g, FLOOR, px, py, t, t);
        }
    }

    /** Stairs: grey, light going up and dark going down, with a step line every other row. */
    private static void stairs(Graphics2D g, boolean up, int x, int y, int t) {
        fill(g, up ? STAIRS_UP : STAIRS_DOWN, x, y, t, t);
        g.setColor(STEP);
        for (int r = 1; r < t; r += 2) {
            g.fillRect(x, y + r, t, 1);
        }
    }

    private static void fill(Graphics2D g, Color c, int x, int y, int w, int h) {
        g.setColor(c);
        g.fillRect(x, y, w, h);
    }

    /** The party: a green arrow centred on (cx, cy), pointing the way it faces. */
    static void partyArrow(Graphics2D g, int cx, int cy, int r, Direction facing) {
        int[][] points = {{0, -r}, {r, r}, {0, r / 2}, {-r, r}};
        Polygon p = new Polygon();
        for (int[] pt : points) {
            int px = pt[0];
            int py = pt[1];
            for (int i = 0; i < facing.ordinal(); i++) { // a quarter turn clockwise each
                int t = px;
                px = -py;
                py = t;
            }
            p.addPoint(cx + px, cy + py);
        }
        g.setColor(PARTY);
        g.fillPolygon(p);
        if (r >= 5) { // smaller, an outline would hide the green
            g.setColor(INK);
            g.drawPolygon(p);
        }
    }

    /** A paging button: a triangle pointing up or down in a frame. */
    private static void arrow(Graphics2D g, Rectangle r, Direction way, Color colour) {
        g.setColor(colour);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
        int cx = r.x + r.width / 2;
        int tip = way == Direction.NORTH ? r.y + 4 : r.y + r.height - 5;
        int base = way == Direction.NORTH ? r.y + r.height - 6 : r.y + 5;
        g.fillPolygon(new int[] {cx, cx - 9, cx + 9}, new int[] {tip, base, base}, 3);
    }

    private static void legend(Graphics2D g, Art art, int row, String label, Color swatch, Color inner) {
        int y = LEGEND_Y + row * LEGEND_LINE;
        if (swatch != null) {
            fill(g, swatch, LEGEND_X, y - 4, 6, 6);
        }
        if (inner != null) {
            fill(g, inner, LEGEND_X + 1, y - 3, 4, 4);
        }
        text(g, art, label, LEGEND_X + 10, y, false);
    }

    /** Text in DM's font at its text point (x, y), or centred on x. */
    static void text(Graphics2D g, Art art, String s, int x, int y, boolean centred) {
        text(g, art, s, x, y, centred, INK);
    }

    static void text(Graphics2D g, Art art, String s, int x, int y, boolean centred, Color colour) {
        if (centred) {
            x -= s.length() * DmFont.ADVANCE / 2;
        }
        DmFont font = art.font();
        if (font != null) {
            font.draw(g, s, x, y, colour, null);
        } else {
            PixelFont.draw(g, s, x, y - 4, colour);
        }
    }
}
