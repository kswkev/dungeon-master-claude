package dm.ui;

import dm.model.Champion;
import dm.model.Direction;
import dm.model.Party;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * DM's party formation box in the top-right corner: a 2x2 grid showing where
 * each champion stands (front row on top, as seen from behind the party).
 * Each champion is DM's helmeted figure (graphic 28) in their colour on
 * black, turned the way they face relative to the party (F291, #38).
 *
 * Click a champion to pick them up, then click any cell to move them there,
 * swapping with whoever stands there. Clicking the picked cell again cancels.
 */
public final class FormationBox {

    /** DM's champion icon boxes (ScummVM's boxChampionIcons): x 281-299 and 301-319, y 0-13 and 15-28. */
    public static final Rectangle AREA = new Rectangle(281, 0, 39, 29);

    static final int CHAMPION_ICONS = 28;
    private static final int ICON_W = 19;
    private static final int ICON_H = 14;
    private static final int CELL_W = 20;
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
        return new Rectangle(AREA.x + col * CELL_W, AREA.y + row * CELL_H, ICON_W, ICON_H);
    }

    /**
     * DM's M26/getChampionIconIndex: which of graphic 28's four figures shows
     * a champion facing {@code facing} in a party facing {@code partyFacing}
     * (0 the same way, then clockwise).
     */
    static int iconIndex(Direction facing, Direction partyFacing) {
        return (facing.ordinal() + 4 - partyFacing.ordinal()) & 3;
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
        // F291: the box in the champion's colour, then the figure keyed on colour 12, so the
        // figure (colour 12) takes the colour and its black background stays black.
        BufferedImage icons = art.keyed(CHAMPION_ICONS, 12);
        for (int p = 0; p < Party.MAX_MEMBERS; p++) {
            Rectangle r = cell(p);
            Champion c = party.at(p);
            if (c == null) {
                if (!art.available()) { // DM leaves an empty cell black (#27); placeholders outline it
                    g.setColor(new Color(40, 40, 40));
                    g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
                }
                continue;
            }
            g.setColor(ChampionBars.COLORS[party.members().indexOf(c)]);
            g.fillRect(r.x, r.y, r.width, r.height);
            int icon = iconIndex(c.facing(), party.facing());
            if (icons != null && icons.getWidth() >= (icon + 1) * ICON_W) {
                g.drawImage(icons.getSubimage(icon * ICON_W, 0, ICON_W, ICON_H), r.x, r.y, null);
            }
            if (p == picked) {
                g.setColor(Color.WHITE);
                g.drawRect(r.x - 1, r.y - 1, r.width + 1, r.height + 1);
            }
        }
    }
}
