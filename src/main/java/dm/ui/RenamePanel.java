package dm.ui;

import dm.model.Champion;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * DM's rename panel (F281), shown on the character sheet after REINCARNATE:
 * graphic 27's on-screen keyboard over the sheet's panel, where the new
 * name (up to 7 letters) and then the title (up to 19) are typed, by
 * clicking letters or on the keyboard. RETURN moves from the name to the
 * title; BACKSPACE at the start of the title goes back into the name; OK
 * takes them once there is a name. Coordinates are the viewport's.
 */
final class RenamePanel {

    /** DM's panel box (G032) and the rename graphic, keyed on cyan. */
    private static final int PANEL_X = 80;
    private static final int PANEL_Y = 52;
    private static final int GRAPHIC = 27;
    /** Where the name and the title are typed (DM's text positions, less the viewport's 33 rows). */
    private static final int NAME_X = 177;
    private static final int NAME_Y = 58;
    private static final int TITLE_X = 105;
    private static final int TITLE_Y = 76;
    /** The champion's name heading, cleared to dark grey while renaming. */
    private static final int HEADING_W = 168;
    private static final String SPECIALS = ",.;: ";

    private final StringBuilder name = new StringBuilder();
    private final StringBuilder title = new StringBuilder();
    private boolean typingTitle;

    String name() {
        return name.toString();
    }

    String title() {
        return title.toString();
    }

    /** Whether OK is live: DM takes it once a name has a letter, or the title is being typed. */
    private boolean canFinish() {
        return typingTitle || name.length() > 0;
    }

    /**
     * One character typed or clicked: a letter, one of , . ; : and space
     * (not first), '\r' (from name to title) or '\b' (backspace).
     */
    void type(char c) {
        c = Character.toUpperCase(c);
        StringBuilder field = typingTitle ? title : name;
        if ((c >= 'A' && c <= 'Z') || SPECIALS.indexOf(c) >= 0) {
            boolean titleFull = typingTitle && title.length() == Champion.MAX_TITLE;
            if ((c != ' ' || field.length() != 0) && !titleFull) {
                field.append(c);
                if (!typingTitle && name.length() == Champion.MAX_NAME) {
                    typingTitle = true;
                }
            }
        } else if (c == '\r' || c == '\n') {
            if (!typingTitle && name.length() > 0) {
                typingTitle = true;
            }
        } else if (c == '\b') {
            if (typingTitle && title.length() == 0) {
                typingTitle = false;
                if (name.length() > 0) {
                    name.setLength(name.length() - 1);
                }
            } else if (field.length() > 0) {
                field.setLength(field.length() - 1);
            }
        }
    }

    /**
     * A click at viewport point (vx, vy): a key, BACKSPACE, or OK. Returns
     * true for OK when a name has been typed; the caller still checks the
     * name is free, as DM does.
     */
    boolean click(int vx, int vy) {
        // DM's F281 works in screen coordinates; the viewport starts at row 33.
        int x = vx;
        int y = vy + ViewRenderer.VIEWPORT.y;
        if (canFinish() && x >= 197 && x <= 215 && y >= 147 && y <= 155) {
            return true;
        }
        if (x >= 107 && x <= 175 && y >= 147 && y <= 155) {
            type('\b');
            return false;
        }
        if (x < 107 || x > 215 || y < 116 || y > 144) {
            return false;
        }
        // The one-pixel gaps between keys don't count, except inside RETURN's two rows.
        if ((x + 4) % 10 == 0 || ((y + 5) % 10 == 0 && (x < 207 || y != 135))) {
            return false;
        }
        int c = 'A' + 11 * ((y - 116) / 10) + (x - 107) / 10;
        if (c == 86 || c == 97) {
            type('\r'); // RETURN takes two keys
            return false;
        }
        if (c >= 87) {
            c--; // RETURN's first key comes before V
        }
        type(c > 'Z' ? SPECIALS.charAt(c - 'Z' - 1) : (char) c);
        return false;
    }

    void draw(Graphics2D g, Art art) {
        Color field = Art.PALETTE[12];
        Color text = Art.PALETTE[13];
        g.setColor(field);
        g.fillRect(3, 3, HEADING_W, 6);
        BufferedImage panel = art.keyed(GRAPHIC, 4);
        if (panel != null) {
            g.drawImage(panel, PANEL_X, PANEL_Y, null);
        } else {
            drawKeyboard(g);
        }
        PixelFont.draw(g, "_".repeat(Champion.MAX_NAME), NAME_X, NAME_Y, text);
        PixelFont.draw(g, "_".repeat(Champion.MAX_TITLE), TITLE_X, TITLE_Y, text);
        drawField(g, name, NAME_X, NAME_Y, field, text);
        drawField(g, title, TITLE_X, TITLE_Y, field, text);
        // The gold cursor where the next letter goes (none once the title is full).
        if (!typingTitle) {
            PixelFont.draw(g, "_", NAME_X + name.length() * PixelFont.ADVANCE, NAME_Y, Art.PALETTE[9]);
        } else if (title.length() < Champion.MAX_TITLE) {
            PixelFont.draw(g, "_", TITLE_X + title.length() * PixelFont.ADVANCE, TITLE_Y, Art.PALETTE[9]);
        }
    }

    /** Each typed letter on dark grey over its underscore, as DM prints them. */
    private static void drawField(Graphics2D g, CharSequence s, int x, int y, Color background, Color text) {
        for (int i = 0; i < s.length(); i++) {
            int cx = x + i * PixelFont.ADVANCE;
            g.setColor(background);
            g.fillRect(cx, y - 1, PixelFont.ADVANCE, PixelFont.HEIGHT + 3);
            PixelFont.draw(g, String.valueOf(s.charAt(i)), cx, y, text);
        }
    }

    /** Without GRAPHICS.DAT: the same keys, drawn by hand where graphic 27 has them. */
    private static void drawKeyboard(Graphics2D g) {
        g.setColor(Art.PALETTE[0]);
        g.fillRect(PANEL_X, PANEL_Y, 144, 73);
        Color key = Art.PALETTE[13];
        int top = 116 - ViewRenderer.VIEWPORT.y;
        String rows = "ABCDEFGHIJKLMNOPQRSTUVWXYZ,.;: ";
        int i = 0;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 11; col++) {
                int x = 107 + col * 10;
                int y = top + row * 10;
                if (col == 10 && row > 0) {
                    continue;
                }
                g.setColor(Art.PALETTE[1]);
                g.drawRect(x - 1, y - 1, 9, 9);
                PixelFont.draw(g, String.valueOf(rows.charAt(i++)), x + 1, y + 1, key);
            }
        }
        g.setColor(Art.PALETTE[1]);
        g.drawRect(206, top + 9, 9, 20);
        PixelFont.draw(g, "R", 208, top + 17, key);
        int buttons = 147 - ViewRenderer.VIEWPORT.y;
        g.drawRect(106, buttons - 1, 70, 10);
        PixelFont.draw(g, "BACKSPACE", 115, buttons + 2, key);
        g.drawRect(196, buttons - 1, 20, 10);
        PixelFont.draw(g, "OK", 201, buttons + 2, key);
    }
}
