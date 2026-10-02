package dm.ui;

import dm.data.GraphicsFile;
import dm.model.Champion;
import dm.model.Item;
import dm.model.Party;
import dm.model.Slot;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * The four champion status boxes across the top of the screen. Each box shows
 * the name, both hands, and health/stamina/mana bars in the champion's colour.
 * While a champion's sheet is open their portrait replaces the name and hands,
 * as in DM. A candidate being viewed at a mirror takes the next free box.
 */
public final class ChampionBars {

    /** DM's champion colours, by party position: green, yellow, red, blue. */
    public static final Color[] COLORS = {Art.PALETTE[7], Art.PALETTE[11], Art.PALETTE[8], Art.PALETTE[14]};

    private static final int PITCH = 69;
    private static final int BOX_W = 67;
    private static final int BOX_H = 29;
    private static final int BAR_W = 4;
    private static final int BAR_TOP = 3;
    private static final int BAR_BOTTOM = 26;
    private static final int[] BAR_X = {46, 53, 60};
    private static final Color BOX_BG = Art.PALETTE[12];
    private static final Color BAR_BG = Art.PALETTE[0];

    private final Art art;

    public ChampionBars(Art art) {
        this.art = art;
    }

    /** Index of the box at screen point (x, y), or -1. */
    public int hitTest(int x, int y) {
        if (y < 0 || y >= BOX_H || x < 0) {
            return -1;
        }
        int i = x / PITCH;
        return i < Party.MAX_MEMBERS && x - i * PITCH < BOX_W ? i : -1;
    }

    /**
     * @param shown     the champion whose sheet is open, or null
     * @param candidate a mirror champion being viewed (not yet recruited), or null
     */
    public void draw(Graphics2D g, List<Champion> members, Champion shown, Champion candidate) {
        for (int i = 0; i < Party.MAX_MEMBERS; i++) {
            int x = i * PITCH;
            Champion c = i < members.size() ? members.get(i)
                    : i == members.size() ? candidate : null;
            if (c == null) {
                g.setColor(new Color(40, 40, 40));
                g.drawRect(x, 0, BOX_W - 1, BOX_H - 1);
                continue;
            }
            g.setColor(BOX_BG);
            g.fillRect(x, 0, BOX_W, BOX_H);
            if (c == shown) {
                BufferedImage portrait = art.portrait(c.portrait());
                Rectangle r = new Rectangle(x, 0, 32, 29);
                if (portrait != null) {
                    g.drawImage(portrait, r.x, r.y, null);
                } else {
                    Placeholders.portrait(g, c, r);
                }
            } else {
                PixelFont.draw(g, c.name(), x + 2, 2, COLORS[i]);
                drawHand(g, c.items().get(Slot.READY_HAND), x + 3, 10);
                drawHand(g, c.items().get(Slot.ACTION_HAND), x + 23, 10);
            }
            drawBar(g, x + BAR_X[0], c.health(), c.maxHealth(), COLORS[i]);
            drawBar(g, x + BAR_X[1], c.stamina(), c.maxStamina(), COLORS[i]);
            drawBar(g, x + BAR_X[2], c.mana(), c.maxMana(), COLORS[i]);
        }
    }

    private void drawHand(Graphics2D g, Item item, int x, int y) {
        BufferedImage box = art.image(GraphicsFile.SLOT_BOX);
        if (box != null) {
            g.drawImage(box, x, y, null);
        } else {
            g.setColor(Art.PALETTE[1]);
            g.drawRect(x, y, 17, 17);
        }
        if (item == null) {
            return;
        }
        BufferedImage icon = art.icon(item);
        if (icon != null) {
            g.drawImage(icon, x + 1, y + 1, null);
        } else {
            Placeholders.icon(g, item, x + 1, y + 1);
        }
    }

    private static void drawBar(Graphics2D g, int x, int value, int max, Color color) {
        int full = BAR_BOTTOM - BAR_TOP;
        g.setColor(BAR_BG);
        g.fillRect(x, BAR_TOP, BAR_W, full);
        if (max <= 0) {
            return;
        }
        int h = Math.max(value > 0 ? 1 : 0, full * Math.min(value, max) / max);
        g.setColor(color);
        g.fillRect(x, BAR_BOTTOM - h, BAR_W, h);
    }
}
