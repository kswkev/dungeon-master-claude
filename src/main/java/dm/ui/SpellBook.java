package dm.ui;

import dm.model.Party;
import dm.model.Spells;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * The spell lists (not in DM): two tinted scrolls beside the map's open a
 * parchment over the dungeon view listing the wizard or the priest spells,
 * in G0487's order, each the party has cast as its runes (in DM's font),
 * their names and the spell's name; the others as ??? rows. Like the map,
 * the game is paused while it is open, and any click or Esc closes it.
 */
final class SpellBook {

    /** The wizard's (blue) and the priest's (gold) scrolls, after the map's. */
    static final Rectangle WIZARD_BUTTON = new Rectangle(251, 29, 16, 13);
    static final Rectangle PRIEST_BUTTON = new Rectangle(269, 29, 16, 13);

    private static final Color WIZARD_TINT = new Color(0x70A8FF);
    private static final Color PRIEST_TINT = new Color(0xFFC840);
    private static final Color FADED = new Color(0xA08C64);

    private static final Rectangle V = ViewRenderer.VIEWPORT;
    static final int FIRST_ROW = V.y + 24;
    static final int ROW = 8;
    /** Columns: up to 3 runes, then their names (up to 12 characters, FUL BRO NETA), then the spell's (up to 18). */
    static final int RUNES_X = V.x + 8;
    static final int NAMES_X = V.x + 32;
    static final int TITLE_X = V.x + 110;

    private boolean open;
    private boolean wizard;
    private BufferedImage wizardIcon;
    private BufferedImage priestIcon;

    boolean isOpen() {
        return open;
    }

    /** Whether the wizard's list is the one open (or was last). */
    boolean wizard() {
        return wizard;
    }

    void open(boolean wizard) {
        this.open = true;
        this.wizard = wizard;
    }

    void close() {
        open = false;
    }

    /** The button at screen point (x, y): 1 the wizard's, 2 the priest's, 0 neither. */
    static int buttonAt(int x, int y) {
        return WIZARD_BUTTON.contains(x, y) ? 1 : PRIEST_BUTTON.contains(x, y) ? 2 : 0;
    }

    /** The spells of one school, in G0487's order. */
    static List<Spells.Spell> spells(boolean wizard) {
        return Spells.table().stream().filter(s -> Spells.isWizard(s) == wizard).toList();
    }

    void drawButtons(Graphics2D g, Art art) {
        if (wizardIcon == null) {
            BufferedImage scroll = AutoMap.scrollIcon(art);
            wizardIcon = tint(scroll, WIZARD_TINT);
            priestIcon = tint(scroll, PRIEST_TINT);
        }
        AutoMap.drawScroll(g, wizardIcon, WIZARD_BUTTON, WIZARD_TINT);
        AutoMap.drawScroll(g, priestIcon, PRIEST_BUTTON, PRIEST_TINT);
    }

    /** The scroll's paper (its light pixels) in {@code tint}, shaded as the paper was; or null without art. */
    static BufferedImage tint(BufferedImage icon, Color tint) {
        if (icon == null) {
            return null;
        }
        BufferedImage out = new BufferedImage(icon.getWidth(), icon.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < icon.getHeight(); y++) {
            for (int x = 0; x < icon.getWidth(); x++) {
                int argb = icon.getRGB(x, y);
                int r = (argb >> 16) & 0xFF;
                int gr = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int light = (r + gr + b) / 3;
                if ((argb >>> 24) != 0 && light > 150) {
                    argb = 0xFF000000 | (tint.getRed() * light / 255) << 16
                            | (tint.getGreen() * light / 255) << 8 | tint.getBlue() * light / 255;
                }
                out.setRGB(x, y, argb);
            }
        }
        return out;
    }

    void draw(Graphics2D g, Party party, Art art) {
        g.setColor(AutoMap.PARCHMENT);
        g.fillRect(V.x, V.y, V.width, V.height);
        g.setColor(AutoMap.INK);
        g.drawRect(V.x + 1, V.y + 1, V.width - 3, V.height - 3);
        List<Spells.Spell> spells = spells(wizard);
        long cast = spells.stream().filter(party::hasCast).count();
        AutoMap.text(g, art, (wizard ? "WIZARD" : "PRIEST") + " SPELLS  " + cast + " OF " + spells.size(),
                V.x + V.width / 2, V.y + 12, true);
        int y = FIRST_ROW;
        for (Spells.Spell spell : spells) {
            if (party.hasCast(spell)) {
                String runes = spell.symbolString();
                AutoMap.text(g, art, runes, RUNES_X, y, false);
                StringBuilder names = new StringBuilder();
                for (char rune : runes.toCharArray()) {
                    names.append(names.length() == 0 ? "" : " ").append(Spells.name(rune));
                }
                AutoMap.text(g, art, names.toString(), NAMES_X, y, false);
                AutoMap.text(g, art, Spells.title(spell), TITLE_X, y, false);
            } else {
                AutoMap.text(g, art, "???", RUNES_X, y, false, FADED);
                AutoMap.text(g, art, "???", NAMES_X, y, false, FADED);
                AutoMap.text(g, art, "???", TITLE_X, y, false, FADED);
            }
            y += ROW;
        }
    }
}
