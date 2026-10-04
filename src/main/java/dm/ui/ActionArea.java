package dm.ui;

import dm.data.IndexedImage;
import dm.model.Actions;
import dm.model.Champion;
import dm.model.Item;
import dm.model.ItemCatalog;
import dm.model.Party;
import dm.model.Slot;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * DM's action area (MENUS.C, F385-F391), below the spell area at
 * (233,77)-(319,121). It shows one of three things:
 * <ul>
 *   <li>the champions' action icons: what each holds in the action hand,
 *       black on cyan (an empty hand shows a fist; an item with no actions a
 *       blank box), shaded while the champion recovers from an action and
 *       blank once they're dead. Clicking one opens that champion's menu;</li>
 *   <li>the menu: the champion's name, and up to three actions on DM's
 *       action panel (graphic 10). Clicking an action performs it; clicking
 *       the right end of the name row passes;</li>
 *   <li>for one game tick after an action, what it did: the damage on DM's
 *       burst (graphic 14, smaller for less damage), or CAN'T REACH or NEED AMMO.</li>
 * </ul>
 * Without GRAPHICS.DAT the panel and burst are drawn by hand.
 */
final class ActionArea {

    static final Rectangle AREA = new Rectangle(233, 77, 87, 45);

    private static final int PANEL_GRAPHIC = 10;
    private static final int DAMAGE_GRAPHIC = 14;
    private static final int EMPTY_HAND_ICON = 201;
    /** The colour of the icon sheets' background, which becomes the box's cyan; everything else turns black. */
    private static final int ICON_BACKGROUND = 12;
    private static final Color CYAN = new Color(Art.PALETTE[4].getRGB());

    private final Art art;
    /** The member whose menu is open, or -1. */
    private int acting = -1;
    private List<Integer> menu = List.of();
    /** What the last action did, shown at the next tick (DM's G513), and what is shown now. */
    private int pending;
    private int shown;
    private boolean showing;

    ActionArea(Art art) {
        this.art = art;
    }

    /** The member whose action menu is open, or -1. */
    int acting() {
        return acting;
    }

    List<Integer> menu() {
        return menu;
    }

    /** What the area shows after an action: damage, {@link Party#CANT_REACH}, {@link Party#NEED_AMMO}; 0 when it shows icons or a menu. */
    int shownDamage() {
        return showing ? shown : 0;
    }

    /** A click in the area: what to do. */
    record Click(int member, int action) {
        static final Click NONE = new Click(-1, Actions.NONE);
    }

    /**
     * A click at screen point (x, y) (DM's F389 and F391). Opening a menu or
     * passing is handled here; a chosen action is returned for the caller
     * to perform.
     */
    Click click(Party party, int x, int y, boolean candidateShown) {
        if (!AREA.contains(x, y)) {
            return Click.NONE;
        }
        if (acting >= 0) {
            if (x >= 285 && x <= 318 && y >= 77 && y <= 83) {
                close(); // pass
                return Click.NONE;
            }
            for (int i = 0; i < menu.size(); i++) {
                int top = 86 + i * 12;
                if (x >= 234 && x <= 318 && y >= top && y <= top + 10) {
                    Click c = new Click(acting, menu.get(i));
                    close();
                    return c;
                }
            }
            return Click.NONE;
        }
        if (showing || candidateShown) {
            return Click.NONE;
        }
        for (int m = 0; m < party.members().size(); m++) {
            int x1 = 233 + m * 22;
            if (x >= x1 && x <= x1 + 19 && y >= 86 && y <= 120) {
                List<Integer> actions = party.actions(m);
                if (!actions.isEmpty()) {
                    acting = m;
                    menu = actions;
                }
                break;
            }
        }
        return Click.NONE;
    }

    /** Closes the menu without acting (DM's F388). */
    void close() {
        acting = -1;
        menu = List.of();
    }

    /** An action was performed: its result shows at the next tick. */
    void performed(int result) {
        pending = result;
    }

    /**
     * DM's F390, once per game tick: a result waiting is shown for this tick,
     * then the icons come back. Returns whether the area changed.
     */
    boolean tick() {
        if (pending != 0) {
            shown = pending;
            pending = 0;
            showing = true;
            return true;
        }
        if (showing) {
            showing = false;
            return true;
        }
        return false;
    }

    /** Forgets everything, as when a game is loaded. */
    void reset() {
        close();
        pending = 0;
        showing = false;
    }

    void draw(Graphics2D g, Party party, boolean candidateShown) {
        g.setColor(Color.BLACK);
        g.fill(AREA);
        if (party.members().isEmpty()) {
            return;
        }
        if (acting >= 0 && acting < party.members().size()) {
            drawMenu(g, party.members().get(acting));
        } else if (showing) {
            drawResult(g, shown);
        } else {
            for (int m = 0; m < party.members().size(); m++) {
                drawIcon(g, party.members().get(m), m, candidateShown);
            }
        }
    }

    /** DM's F386: one champion's action icon. */
    private void drawIcon(Graphics2D g, Champion c, int m, boolean candidateShown) {
        int x1 = 233 + m * 22;
        if (c.health() == 0) {
            return;
        }
        g.setColor(CYAN);
        g.fillRect(x1, 86, 20, 35);
        Item hand = c.items().get(Slot.ACTION_HAND);
        BufferedImage icon = null;
        if (hand == null) {
            icon = silhouette(art.icon(EMPTY_HAND_ICON));
        } else if (ItemCatalog.actionSet(hand) != 0) {
            icon = silhouette(art.icon(hand));
        }
        if (icon != null) {
            g.drawImage(icon, x1 + 2, 95, null);
        } else if (!art.available() && hand != null) {
            Placeholders.icon(g, hand, x1 + 2, 95);
        } else if (!art.available()) {
            g.setColor(Color.BLACK);
            g.fillOval(x1 + 5, 98, 10, 10); // a fist
        }
        if (c.actionDisabled() || candidateShown) {
            shade(g, x1, 86, 20, 35);
        }
    }

    /** DM's G498 palette change for action icons: the background turns cyan, everything else black. */
    private static BufferedImage silhouette(BufferedImage icon) {
        if (icon == null) {
            return null;
        }
        BufferedImage out = new BufferedImage(icon.getWidth(), icon.getHeight(), BufferedImage.TYPE_INT_RGB);
        int background = Art.PALETTE[ICON_BACKGROUND].getRGB();
        for (int y = 0; y < icon.getHeight(); y++) {
            for (int x = 0; x < icon.getWidth(); x++) {
                out.setRGB(x, y, icon.getRGB(x, y) == background ? CYAN.getRGB() : Color.BLACK.getRGB());
            }
        }
        return out;
    }

    /** DM's F136: every other pixel of the box goes black. */
    static void shade(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(Color.BLACK);
        for (int py = y; py < y + h; py++) {
            for (int px = x + ((py + x) & 1); px < x + w; px += 2) {
                g.fillRect(px, py, 1, 1);
            }
        }
    }

    /** DM's F387 menu: the panel cut to the number of actions, the name, then the actions. */
    private void drawMenu(Graphics2D g, Champion c) {
        int bottom = menu.size() == 3 ? 121 : menu.size() == 2 ? 109 : 97; // DM's G499-G501
        int height = bottom - AREA.y + 1;
        BufferedImage panel = art.image(PANEL_GRAPHIC);
        if (panel != null) {
            g.drawImage(panel.getSubimage(0, 0, Math.min(AREA.width, panel.getWidth()),
                    Math.min(height, panel.getHeight())), AREA.x, AREA.y, null);
        } else {
            g.setColor(CYAN);
            g.fillRect(AREA.x, AREA.y, AREA.width, 8);
            g.drawRect(AREA.x, AREA.y, AREA.width - 1, height - 1);
        }
        PixelFont.draw(g, c.name(), 234, 79, Color.BLACK);
        for (int i = 0; i < menu.size(); i++) {
            PixelFont.draw(g, Actions.name(menu.get(i)), 240, 89 + i * 12, CYAN);
        }
    }

    /** DM's F385: the damage on the burst, or a message. */
    private void drawResult(Graphics2D g, int result) {
        if (result < 0) {
            String text = result == Party.CANT_REACH ? "CAN'T REACH" : "NEED AMMO";
            int x = result == Party.CANT_REACH ? 242 : 248;
            PixelFont.draw(g, text, x - 1, 96, CYAN);
            return;
        }
        IndexedImage burst = art.indexed(DAMAGE_GRAPHIC);
        if (burst != null) {
            BufferedImage img;
            int x;
            int y;
            if (result > 40) {
                img = Bitmaps.toImage(burst, -1, Bitmaps.palette());
                img = img.getSubimage(0, 0, Math.min(AREA.width, img.getWidth()), Math.min(AREA.height, img.getHeight()));
                x = 233;
                y = 77;
            } else {
                int w = result > 15 ? 64 : 42;
                img = Bitmaps.toImage(Bitmaps.shrink(burst, w, 37, null), -1, Bitmaps.palette());
                x = result > 15 ? 242 : 251;
                y = 81;
            }
            g.drawImage(img, x, y, null);
        } else {
            g.setColor(new Color(Art.PALETTE[11].getRGB()));
            int r = result > 40 ? 22 : result > 15 ? 18 : 14;
            g.fillOval(276 - r, 99 - r * 3 / 4, r * 2, r * 3 / 2);
        }
        String digits = Integer.toString(result);
        int x = 274 - 3 * digits.length();
        g.setColor(Color.BLACK);
        g.fillRect(x - 1, 95, digits.length() * PixelFont.ADVANCE + 1, 7);
        PixelFont.draw(g, digits, x, 96, CYAN);
    }
}
