package dm.ui;

import dm.data.GraphicsFile;
import dm.model.Champion;
import dm.model.Item;
import dm.model.ItemCatalog;
import dm.model.Party;
import dm.model.Slot;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
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

    private static final int BURST_W = 32;
    private static final int BURST_H = 29;

    private final Art art;
    private final int[] damageShown = new int[Party.MAX_MEMBERS];
    private final long[] damageUntil = new long[Party.MAX_MEMBERS];

    public ChampionBars(Art art) {
        this.art = art;
    }

    /** Shows DM's damage burst with {@code amount} over box {@code slot} until time {@code until} (ms). */
    public void showDamage(int slot, int amount, long until) {
        damageShown[slot] = amount;
        damageUntil[slot] = until;
    }

    /** Takes every damage burst off (a game was loaded). */
    void clearDamage() {
        Arrays.fill(damageUntil, 0);
    }

    /** The damage currently shown on box {@code slot} at time {@code now}, or 0. */
    public int damageShown(int slot, long now) {
        return now < damageUntil[slot] ? damageShown[slot] : 0;
    }

    /** Index of the box at screen point (x, y), or -1. */
    public int hitTest(int x, int y) {
        if (y < 0 || y >= BOX_H || x < 0) {
            return -1;
        }
        int i = x / PITCH;
        return i < Party.MAX_MEMBERS && x - i * PITCH < BOX_W ? i : -1;
    }

    /** A hand box in a champion's status box: which box, and which hand. */
    public record Hand(int box, Slot slot) {
    }

    private static final int HAND_SIZE = 18;
    private static final int READY_HAND_X = 3;
    private static final int ACTION_HAND_X = 23;
    private static final int HAND_Y = 10;

    /**
     * The hand box at screen point (x, y), or null. Hands are only drawn,
     * and so only clickable, while that champion's sheet isn't open.
     */
    public Hand handAt(int x, int y) {
        int box = hitTest(x, y);
        if (box < 0 || y < HAND_Y || y >= HAND_Y + HAND_SIZE) {
            return null;
        }
        int bx = x - box * PITCH;
        if (bx >= READY_HAND_X && bx < READY_HAND_X + HAND_SIZE) {
            return new Hand(box, Slot.READY_HAND);
        }
        if (bx >= ACTION_HAND_X && bx < ACTION_HAND_X + HAND_SIZE) {
            return new Hand(box, Slot.ACTION_HAND);
        }
        return null;
    }

    /**
     * @param shown     the champion whose sheet is open, or null
     * @param candidate a mirror champion being viewed (not yet recruited), or null
     * @param now       current time in ms, for expiring damage bursts
     */
    public void draw(Graphics2D g, List<Champion> members, Champion shown, Champion candidate, long now) {
        draw(g, members, shown, candidate, now, 0);
    }

    /** The party's shields, for {@code shields}: each draws its border round every living champion's box. */
    public static final int PARTY_SHIELD = 1;
    public static final int SPELL_SHIELD = 2;
    public static final int FIRE_SHIELD = 4;
    /** DM's border graphics (F292): party shield 37, fire shield 38, spell shield 39. */
    private static final int BORDER_PARTY_SHIELD = 37;
    private static final int BORDER_FIRE_SHIELD = 38;
    private static final int BORDER_SPELL_SHIELD = 39;

    /** As {@link #draw(Graphics2D, List, Champion, Champion, long)}, with the party's {@code shields} bits. */
    public void draw(Graphics2D g, List<Champion> members, Champion shown, Champion candidate, long now, int shields) {
        for (int i = 0; i < Party.MAX_MEMBERS; i++) {
            int x = i * PITCH;
            Champion c = i < members.size() ? members.get(i)
                    : i == members.size() ? candidate : null;
            if (c == null) {
                if (!art.available()) { // DM leaves an empty status box black (#27); placeholders outline it
                    g.setColor(new Color(40, 40, 40));
                    g.drawRect(x, 0, BOX_W - 1, BOX_H - 1);
                }
                continue;
            }
            if (c.health() == 0 && c != candidate) {
                drawDead(g, c, x);
                continue;
            }
            g.setColor(BOX_BG);
            g.fillRect(x, 0, BOX_W, BOX_H);
            drawShields(g, x, c.shieldDefense() > 0 ? shields | PARTY_SHIELD : shields); // F292: a YA potion's shield too
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
                drawHand(g, c, Slot.READY_HAND, x + READY_HAND_X, HAND_Y);
                drawHand(g, c, Slot.ACTION_HAND, x + ACTION_HAND_X, HAND_Y);
            }
            drawBar(g, x + BAR_X[0], c.health(), c.maxHealth(), COLORS[i]);
            drawBar(g, x + BAR_X[1], c.stamina(), c.maxStamina(), COLORS[i]);
            drawBar(g, x + BAR_X[2], c.mana(), c.maxMana(), COLORS[i]);
            int damage = damageShown(i, now);
            if (damage > 0) {
                drawDamage(g, x, damage);
            }
        }
    }

    /**
     * F292's shield borders round a status box: the party shield's, then the
     * spell shield's, then the fire shield's on top, keyed on colour 10.
     * Without GRAPHICS.DAT, a coloured outline each.
     */
    private void drawShields(Graphics2D g, int x, int shields) {
        int[][] borders = {
                {PARTY_SHIELD, BORDER_PARTY_SHIELD, 0x0000FF}, {SPELL_SHIELD, BORDER_SPELL_SHIELD, 0x00DBDB},
                {FIRE_SHIELD, BORDER_FIRE_SHIELD, 0x00B600}};
        for (int[] border : borders) {
            if ((shields & border[0]) == 0) {
                continue;
            }
            BufferedImage img = art.sprite(border[1]);
            if (img != null) {
                g.drawImage(img, x, 0, null);
            } else {
                g.setColor(new Color(border[2]));
                g.drawRect(x, 0, BOX_W - 1, BOX_H - 1);
            }
        }
    }

    /** DM's dead-champion status box (graphic 8, F292): the skull and bones with the name, and no bars or hands. */
    private void drawDead(Graphics2D g, Champion c, int x) {
        BufferedImage box = art.image(DEAD_BOX);
        if (box != null) {
            g.drawImage(box, x, 0, null);
        } else {
            g.setColor(Art.PALETTE[1]);
            g.fillRect(x, 0, BOX_W, BOX_H);
        }
        PixelFont.draw(g, c.name(), x + 2, 2, Art.PALETTE[13]);
    }

    private static final int DEAD_BOX = 8;

    /** The burst covers the name and hands; the number sits in its centre. */
    private void drawDamage(Graphics2D g, int x, int damage) {
        BufferedImage burst = art.sprite(GraphicsFile.DAMAGE_TO_CHAMPION);
        if (burst != null) {
            g.drawImage(burst, x, 0, null);
        } else {
            g.setColor(Art.PALETTE[8]);
            g.fillRect(x + 4, 4, BURST_W - 8, BURST_H - 8);
        }
        String n = String.valueOf(damage);
        int tx = x + (BURST_W - PixelFont.width(n)) / 2;
        int ty = (BURST_H - PixelFont.HEIGHT) / 2;
        // A dark outline keeps the number readable on the bright burst.
        for (int[] o : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            PixelFont.draw(g, n, tx + o[0], ty + o[1], Art.PALETTE[0]);
        }
        PixelFont.draw(g, n, tx, ty, Art.PALETTE[15]);
    }

    /** The item in a hand as DM draws it (a torch there is lit). */
    private static Item shown(Champion c, Slot hand) {
        Item item = c.items().get(hand);
        return item == null ? null : ItemCatalog.shownIn(item, hand);
    }

    /** DM's red slot box for a wounded hand, and the wounded hand outline (icons 213 and 215) when it's empty. */
    private static final int SLOT_BOX_WOUNDED = 34;
    private static final int WOUNDED_HAND_ICON = 213;

    private void drawHand(Graphics2D g, Champion c, Slot hand, int x, int y) {
        Item item = shown(c, hand);
        boolean wounded = c.isWounded(hand);
        BufferedImage box = art.image(wounded ? SLOT_BOX_WOUNDED : GraphicsFile.SLOT_BOX);
        if (box != null) {
            g.drawImage(box, x, y, null);
        } else {
            g.setColor(Art.PALETTE[wounded ? 8 : 1]);
            g.drawRect(x, y, 17, 17);
        }
        if (item == null) {
            BufferedImage outline = wounded ? art.icon(WOUNDED_HAND_ICON + (hand == Slot.ACTION_HAND ? 2 : 0)) : null;
            if (outline != null) {
                g.drawImage(outline, x + 1, y + 1, null);
            }
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
