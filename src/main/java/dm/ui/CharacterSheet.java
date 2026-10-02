package dm.ui;

import dm.data.GraphicsFile;
import dm.model.Champion;
import dm.model.ChampionMirror;
import dm.model.Item;
import dm.model.Slot;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A champion's inventory screen, drawn over the dungeon view like DM's.
 *
 * Opened from a mirror it shows a candidate with RESURRECT and CANCEL; opened
 * from the champion bars it shows a party member with CLOSE. The X icon in
 * the top-right corner of the background also closes it.
 * Coordinates below are relative to the viewport's top-left corner.
 */
public final class CharacterSheet {

    /** What a click asks for; SLOT means an inventory cell of a party member ({@link #slotAt}). */
    public enum Action { NONE, RESURRECT, CLOSE, SLOT }

    private static final Rectangle VIEW = ViewRenderer.VIEWPORT;

    /** Top-left of each slot's 16x16 icon; the outline box is 1 pixel further out. */
    private static final Map<Slot, Point> SLOT_ICONS = new EnumMap<>(Slot.class);

    static {
        SLOT_ICONS.put(Slot.READY_HAND, new Point(6, 53));
        SLOT_ICONS.put(Slot.ACTION_HAND, new Point(62, 53));
        SLOT_ICONS.put(Slot.HEAD, new Point(34, 26));
        SLOT_ICONS.put(Slot.NECK, new Point(6, 33));
        SLOT_ICONS.put(Slot.TORSO, new Point(34, 46));
        SLOT_ICONS.put(Slot.LEGS, new Point(34, 66));
        SLOT_ICONS.put(Slot.FEET, new Point(34, 86));
        SLOT_ICONS.put(Slot.POUCH_1, new Point(6, 73));
        SLOT_ICONS.put(Slot.POUCH_2, new Point(6, 90));
        SLOT_ICONS.put(Slot.QUIVER_1, new Point(62, 73));
        SLOT_ICONS.put(Slot.QUIVER_2, new Point(79, 73));
        SLOT_ICONS.put(Slot.QUIVER_3, new Point(62, 90));
        SLOT_ICONS.put(Slot.QUIVER_4, new Point(79, 90));
        // Backpack: 9 slots on the lower row, then 8 on the upper row.
        Slot[] slots = Slot.values();
        int first = Slot.BACKPACK_1.ordinal();
        for (int i = 0; i < 9; i++) {
            SLOT_ICONS.put(slots[first + i], new Point(66 + 17 * i, 33));
        }
        for (int i = 0; i < 8; i++) {
            SLOT_ICONS.put(slots[first + 9 + i], new Point(83 + 17 * i, 16));
        }
    }

    private static final int TEXT_X = 98;
    private static final int TEXT_RIGHT = 221;
    private static final int SKILL_LINE = 7;
    private static final int STAT_LINE = 6;
    private static final Rectangle RESURRECT_BUTTON = new Rectangle(98, 123, 62, 11);
    private static final Rectangle CANCEL_BUTTON = new Rectangle(164, 123, 58, 11);
    private static final Rectangle CLOSE_ICON = new Rectangle(208, 1, 13, 12);

    private static final Color TEXT = Art.PALETTE[13];
    private static final Color HEADING = Art.PALETTE[15];
    private static final Color DISABLED = Art.PALETTE[1];
    private static final List<Champion.Stat> SHOWN_STATS = List.of(
            Champion.Stat.STRENGTH, Champion.Stat.DEXTERITY, Champion.Stat.WISDOM,
            Champion.Stat.VITALITY, Champion.Stat.ANTI_MAGIC, Champion.Stat.ANTI_FIRE);

    private final Art art;
    private Champion champion;
    private ChampionMirror candidate;
    private boolean canRecruit;
    private Point hover;

    public CharacterSheet(Art art) {
        this.art = art;
    }

    public void openCandidate(ChampionMirror mirror, boolean partyFull) {
        champion = mirror.champion();
        candidate = mirror;
        canRecruit = !partyFull;
    }

    public void openMember(Champion member) {
        champion = member;
        candidate = null;
    }

    public void close() {
        champion = null;
        candidate = null;
        hover = null;
    }

    public boolean isOpen() {
        return champion != null;
    }

    public Champion champion() {
        return champion;
    }

    /** The mirror being viewed, or null when showing a party member. */
    public ChampionMirror candidate() {
        return candidate;
    }

    /** Mouse position in screen coordinates, for item name tooltips. */
    public void hover(int x, int y) {
        hover = VIEW.contains(x, y) ? new Point(x - VIEW.x, y - VIEW.y) : null;
    }

    /** Handles a click at screen point (x, y). */
    public Action click(int x, int y) {
        int vx = x - VIEW.x;
        int vy = y - VIEW.y;
        if (CLOSE_ICON.contains(vx, vy) || CANCEL_BUTTON.contains(vx, vy)) {
            return Action.CLOSE;
        }
        if (candidate != null && canRecruit && RESURRECT_BUTTON.contains(vx, vy)) {
            return Action.RESURRECT;
        }
        // A candidate's belongings can't be touched until they are resurrected.
        if (candidate == null && slotAt(x, y) != null) {
            return Action.SLOT;
        }
        return Action.NONE;
    }

    /** Screen point at the centre of a slot's icon, for tests and scripted clicks. */
    static Point slotCentre(Slot slot) {
        Point p = SLOT_ICONS.get(slot);
        return new Point(VIEW.x + p.x + 8, VIEW.y + p.y + 8);
    }

    /** The inventory cell under screen point (x, y), or null. */
    public Slot slotAt(int x, int y) {
        int vx = x - VIEW.x;
        int vy = y - VIEW.y;
        for (Map.Entry<Slot, Point> e : SLOT_ICONS.entrySet()) {
            Point p = e.getValue();
            if (vx >= p.x && vx < p.x + 16 && vy >= p.y && vy < p.y + 16) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Draws the sheet; item name tooltips are left out while an item is on the pointer. */
    public void draw(Graphics2D g, boolean holding) {
        if (champion == null) {
            return;
        }
        Graphics2D v = (Graphics2D) g.create(VIEW.x, VIEW.y, VIEW.width, VIEW.height);
        try {
            drawBackground(v);
            PixelFont.draw(v, champion.fullName(), 3, 3, HEADING);
            drawItems(v);
            drawVitals(v);
            drawSkillsAndStats(v);
            drawButtons(v);
            if (!holding) {
                drawTooltip(v);
            }
        } finally {
            v.dispose();
        }
    }

    private void drawBackground(Graphics2D g) {
        BufferedImage bg = art.image(GraphicsFile.INVENTORY);
        if (bg != null) {
            g.drawImage(bg, 0, 0, null);
            return;
        }
        g.setColor(Art.PALETTE[12]);
        g.fillRect(0, 0, VIEW.width, VIEW.height);
        g.setColor(Art.PALETTE[1]);
        for (Point p : SLOT_ICONS.values()) {
            g.drawRect(p.x - 1, p.y - 1, 17, 17);
        }
        PixelFont.draw(g, "X", CLOSE_ICON.x + 4, CLOSE_ICON.y + 3, Art.PALETTE[8]);
    }

    private void drawItems(Graphics2D g) {
        for (Map.Entry<Slot, Item> e : champion.items().entrySet()) {
            Point p = SLOT_ICONS.get(e.getKey());
            BufferedImage icon = art.icon(e.getValue());
            if (icon != null) {
                g.drawImage(icon, p.x, p.y, null);
            } else {
                Placeholders.icon(g, e.getValue(), p.x, p.y);
            }
        }
    }

    private void drawVitals(Graphics2D g) {
        int y = 112;
        vitalLine(g, "HEALTH", champion.health(), champion.maxHealth(), y);
        vitalLine(g, "STAMINA", champion.stamina(), champion.maxStamina(), y + 8);
        vitalLine(g, "MANA", champion.mana(), champion.maxMana(), y + 16);
    }

    private static void vitalLine(Graphics2D g, String label, int value, int max, int y) {
        PixelFont.draw(g, label, 5, y, TEXT);
        String v = value + "/" + max;
        PixelFont.draw(g, v, 92 - PixelFont.width(v), y, TEXT);
    }

    /** Up to 4 skill lines and 6 stat lines must fit between y=52 and the buttons at y=123. */
    private void drawSkillsAndStats(Graphics2D g) {
        int y = 52;
        for (int s = 0; s < Champion.BASE_SKILLS.size(); s++) {
            String title = champion.skillTitle(s);
            if (title != null) {
                PixelFont.draw(g, Champion.BASE_SKILLS.get(s) + " " + title, TEXT_X, y, HEADING);
                y += SKILL_LINE;
            }
        }
        y += 2;
        for (Champion.Stat stat : SHOWN_STATS) {
            PixelFont.draw(g, stat.label(), TEXT_X, y, TEXT);
            String v = champion.stat(stat) + "/" + champion.maxStat(stat);
            PixelFont.draw(g, v, TEXT_RIGHT - PixelFont.width(v), y, TEXT);
            y += STAT_LINE;
        }
    }

    private void drawButtons(Graphics2D g) {
        if (candidate != null) {
            button(g, RESURRECT_BUTTON, "RESURRECT", canRecruit ? Art.PALETTE[4] : DISABLED,
                    canRecruit ? Art.PALETTE[14] : DISABLED);
            button(g, CANCEL_BUTTON, "CANCEL", Art.PALETTE[11], Art.PALETTE[8]);
        } else {
            button(g, CANCEL_BUTTON, "CLOSE", Art.PALETTE[11], Art.PALETTE[8]);
        }
    }

    private static void button(Graphics2D g, Rectangle r, String label, Color text, Color border) {
        g.setColor(Color.BLACK);
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(border);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
        PixelFont.draw(g, label, r.x + (r.width - PixelFont.width(label)) / 2, r.y + 3, text);
    }

    private void drawTooltip(Graphics2D g) {
        if (hover == null) {
            return;
        }
        Slot slot = slotAt(hover.x + VIEW.x, hover.y + VIEW.y);
        Item item = slot == null ? null : champion.items().get(slot);
        if (item == null) {
            return;
        }
        String name = item.name();
        int w = PixelFont.width(name) + 4;
        int x = Math.min(hover.x + 6, VIEW.width - w - 1);
        int y = Math.max(hover.y - 10, 0);
        g.setColor(Color.BLACK);
        g.fillRect(x, y, w, 9);
        g.setColor(Art.PALETTE[11]);
        g.drawRect(x, y, w - 1, 8);
        PixelFont.draw(g, name, x + 2, y + 2, Art.PALETTE[11]);
    }
}
