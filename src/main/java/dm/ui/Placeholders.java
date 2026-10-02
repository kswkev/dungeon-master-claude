package dm.ui;

import dm.model.Champion;
import dm.model.Item;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/** Stand-ins drawn when GRAPHICS.DAT isn't available. */
final class Placeholders {

    private Placeholders() {
    }

    /** A coloured box with the champion's initial, sized to the portrait area. */
    static void portrait(Graphics2D g, Champion c, Rectangle r) {
        g.setColor(c.gender() == 'F' ? new Color(120, 60, 90) : new Color(60, 80, 120));
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(Color.BLACK);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
        String initial = c.name().substring(0, 1);
        PixelFont.draw(g, initial, r.x + (r.width - PixelFont.width(initial)) / 2,
                r.y + (r.height - PixelFont.HEIGHT) / 2, Color.WHITE);
    }

    /** A 16x16 box with the first two letters of the item's name. */
    static void icon(Graphics2D g, Item item, int x, int y) {
        g.setColor(new Color(90, 90, 90));
        g.fillRect(x, y, 16, 16);
        String abbrev = item.name().length() > 2 ? item.name().substring(0, 2) : item.name();
        PixelFont.draw(g, abbrev, x + 2, y + 6, Color.WHITE);
    }
}
