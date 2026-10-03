package dm.ui;

import dm.model.Champion;
import dm.model.Party;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * DM's party formation box in the top-right corner: a 2x2 grid showing where
 * each champion stands (front row on top, as seen from behind the party).
 * Each champion appears as their colour with DM's champion icon on top.
 *
 * Click a champion to pick them up, then click any cell to move them there,
 * swapping with whoever stands there. Clicking the picked cell again cancels.
 */
public final class FormationBox {

    public static final Rectangle AREA = new Rectangle(276, 0, 44, 29);

    static final int CHAMPION_ICONS = 28;
    private static final int ICON_W = 19;
    private static final int ICON_H = 14;
    private static final int CELL_W = 21;
    private static final int CELL_H = 15;

    private final Art art;
    private int picked = -1;

    public FormationBox(Art art) {
        this.art = art;
    }

    /** Formation position (Party.FRONT_LEFT..BACK_LEFT) under screen point (x, y), or -1. */
    public static int hitTest(int x, int y) {
        if (!AREA.contains(x, y)) {
            return -1;
        }
        int col = Math.min(1, (x - AREA.x) / CELL_W);
        int row = Math.min(1, (y - AREA.y) / CELL_H);
        return row == 0 ? (col == 0 ? Party.FRONT_LEFT : Party.FRONT_RIGHT)
                : (col == 0 ? Party.BACK_LEFT : Party.BACK_RIGHT);
    }

    private static Rectangle cell(int position) {
        int col = position == Party.FRONT_LEFT || position == Party.BACK_LEFT ? 0 : 1;
        int row = position == Party.FRONT_LEFT || position == Party.FRONT_RIGHT ? 0 : 1;
        return new Rectangle(AREA.x + 1 + col * CELL_W, AREA.y + row * CELL_H, ICON_W, ICON_H);
    }

    /** The position picked up for moving, or -1. */
    public int picked() {
        return picked;
    }

    /** Forgets a half-made swap (a game was loaded). */
    void clearPick() {
        picked = -1;
    }

    /** Handles a click inside {@link #AREA}; returns true if the formation changed. */
    public boolean click(Party party, int x, int y) {
        int position = hitTest(x, y);
        if (position < 0) {
            return false;
        }
        if (picked < 0) {
            if (party.at(position) != null) {
                picked = position;
            }
            return false;
        }
        if (picked == position) {
            picked = -1;
            return false;
        }
        party.swap(picked, position);
        picked = -1;
        return true;
    }

    public void draw(Graphics2D g, Party party) {
        g.setColor(Color.BLACK);
        g.fillRect(AREA.x, AREA.y, AREA.width, AREA.height);
        BufferedImage icons = art.sprite(CHAMPION_ICONS);
        for (int p = 0; p < Party.MAX_MEMBERS; p++) {
            Rectangle r = cell(p);
            Champion c = party.at(p);
            if (c == null) {
                g.setColor(new Color(40, 40, 40));
                g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
                continue;
            }
            g.setColor(ChampionBars.COLORS[party.members().indexOf(c)]);
            g.fillRect(r.x, r.y, r.width, r.height);
            if (icons != null) {
                // Icon 0 faces the same way as the party; black is see-through so the colour shows.
                drawIcon(g, icons, r);
            }
            if (p == picked) {
                g.setColor(Color.WHITE);
                g.drawRect(r.x - 1, r.y - 1, r.width + 1, r.height + 1);
            }
        }
    }

    private static void drawIcon(Graphics2D g, BufferedImage icons, Rectangle r) {
        for (int y = 0; y < ICON_H; y++) {
            for (int x = 0; x < ICON_W; x++) {
                int argb = icons.getRGB(x, y);
                if ((argb >>> 24) != 0 && (argb & 0xFFFFFF) != 0) {
                    g.setColor(new Color(argb, true));
                    g.fillRect(r.x + x, r.y + y, 1, 1);
                }
            }
        }
    }
}
