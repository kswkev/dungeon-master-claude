package dm.ui;

import dm.data.IndexedImage;
import dm.model.Champion;
import dm.model.Party;
import dm.model.Spells;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;

/**
 * DM's spell area (MENUS.C F0392-F0394, F0397-F0398; clicks from EVENTS.C
 * F0370-F0371 and G0454), at (233,42)-(319,73):
 * <ul>
 *   <li>along the top, a tab per champion: the caster's is wide and shows
 *       their name, black on cyan; each other living champion has a small
 *       cyan tab, which makes them the caster when clicked;</li>
 *   <li>below, the spell panel (graphic 9 at (233,50)): the six symbols of
 *       the caster's current row, then the symbols entered so far, the cast
 *       bar and the backspace arrow.</li>
 * </ul>
 * The symbols are DM's rune glyphs from its font (characters 96-119).
 * Without GRAPHICS.DAT the panel is drawn by hand, with the symbols' names.
 */
final class SpellArea {

    static final Rectangle AREA = new Rectangle(233, 42, 87, 32);

    private static final int PANEL_GRAPHIC = 9;
    private static final int PANEL_X = 233;
    private static final int PANEL_Y = 50;
    private static final Color CYAN = new Color(Art.PALETTE[4].getRGB());

    /** What a click in the area asks for. */
    enum Command { NONE, CASTER, SYMBOL, CAST, DELETE }

    record Click(Command command, int index) {
        static final Click NONE = new Click(Command.NONE, -1);
    }

    private final Art art;

    SpellArea(Art art) {
        this.art = art;
    }

    /** The caster's wide tab, from DM's F0393 (ST layout): x from 233 + 14 × caster, 45 wide. */
    private static int wideTabX(int caster) {
        return 233 + 14 * caster;
    }

    /** Another champion's small tab, 12 wide: before the caster's at 233 + 14i, after it at 280 + 14(i - 1). */
    private static int smallTabX(int caster, int member) {
        return member < caster ? 233 + 14 * member : 280 + 14 * (member - 1);
    }

    /**
     * DM's F0370 and G0454: a click at screen point (x, y). The tabs row
     * (y up to 48) picks a caster; below, the six symbols (14 apart from
     * x 235, y 51-61), the cast bar (234-303, y 63-73) and the backspace
     * arrow (305-318).
     */
    static Click click(Party party, int x, int y) {
        int caster = party.magicCaster();
        if (!AREA.contains(x, y) || caster < 0) {
            return Click.NONE;
        }
        if (y <= 48) {
            for (int i = 0; i < party.members().size(); i++) {
                if (i != caster) {
                    int x1 = smallTabX(caster, i);
                    if (x >= x1 && x <= x1 + 11) {
                        return new Click(Command.CASTER, i);
                    }
                }
            }
            return Click.NONE;
        }
        if (y >= 51 && y <= 61) {
            for (int i = 0; i < Spells.PER_ROW; i++) {
                int x1 = 235 + 14 * i;
                if (x >= x1 && x <= x1 + 12) {
                    return new Click(Command.SYMBOL, i);
                }
            }
        } else if (y >= 63 && y <= 73) {
            if (x >= 234 && x <= 303) {
                return new Click(Command.CAST, -1);
            }
            if (x >= 305 && x <= 318) {
                return new Click(Command.DELETE, -1);
            }
        }
        return Click.NONE;
    }

    /** Draws the area; shaded (DM's F0136) when {@code disabled}, as while the party sleeps. */
    void draw(Graphics2D g, Party party, boolean disabled) {
        int caster = party.magicCaster();
        if (caster < 0) {
            if (!art.available()) {
                g.setColor(new Color(40, 40, 40));
                g.drawRect(AREA.x, AREA.y, AREA.width - 1, AREA.height + 2); // nobody to cast yet
            }
            return;
        }
        List<Champion> members = party.members();
        Champion c = members.get(caster);
        g.setColor(Color.BLACK);
        g.fillRect(AREA.x, AREA.y, AREA.width, 8);
        for (int i = 0; i < members.size(); i++) {
            if (i != caster && members.get(i).health() > 0) {
                g.setColor(CYAN);
                g.fillRect(smallTabX(caster, i), 42, 12, 7);
            }
        }
        g.setColor(CYAN);
        g.fillRect(wideTabX(caster), 42, 45, 8);
        text(g, c.name(), wideTabX(caster) + 2, 48, Color.BLACK, CYAN);

        IndexedImage panel = art.indexed(PANEL_GRAPHIC);
        if (panel != null) {
            g.drawImage(art.image(PANEL_GRAPHIC), PANEL_X, PANEL_Y, null);
        } else {
            drawPanel(g);
        }
        int step = c.symbolStep();
        for (int i = 0; i < Spells.PER_ROW; i++) {
            char symbol = (char) (Spells.FIRST_SYMBOL + step * Spells.PER_ROW + i);
            symbol(g, symbol, 239 + 14 * i, 58, 239 + 14 * i - 2);
        }
        String symbols = c.symbols();
        for (int i = 0; i < symbols.length() && i < 4; i++) {
            symbol(g, symbols.charAt(i), 241 + 9 * i, 70, 236 + 17 * i);
        }
        if (disabled) {
            ActionArea.shade(g, AREA.x, AREA.y, AREA.width, AREA.height);
        }
    }

    /** A spell symbol: DM's rune at text point (x, y), or without the font its name at {@code nameX}. */
    private void symbol(Graphics2D g, char symbol, int x, int y, int nameX) {
        DmFont font = art.font();
        if (font != null) {
            font.draw(g, String.valueOf(symbol), x, y, CYAN, Color.BLACK);
        } else {
            String name = Spells.name(symbol);
            PixelFont.draw(g, name.substring(0, Math.min(2, name.length())), nameX, y - 3, CYAN);
        }
    }

    private void text(Graphics2D g, String s, int x, int y, Color color, Color background) {
        DmFont font = art.font();
        if (font != null) {
            font.draw(g, s, x, y, color, background);
        } else {
            PixelFont.draw(g, s, x, y - 4, color);
        }
    }

    /** The spell panel by hand: six symbol boxes over the cast bar and the backspace box. */
    private static void drawPanel(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(PANEL_X, PANEL_Y, 87, 25);
        g.setColor(CYAN);
        g.drawRect(PANEL_X, PANEL_Y, 86, 24);
        for (int i = 1; i < Spells.PER_ROW; i++) {
            g.drawLine(PANEL_X + 14 * i, PANEL_Y, PANEL_X + 14 * i, PANEL_Y + 12);
        }
        g.drawLine(PANEL_X, PANEL_Y + 12, PANEL_X + 86, PANEL_Y + 12);
        g.drawLine(PANEL_X + 71, PANEL_Y + 12, PANEL_X + 71, PANEL_Y + 24);
        PixelFont.draw(g, "<", PANEL_X + 77, PANEL_Y + 16, CYAN);
    }
}
