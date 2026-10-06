package dm.ui;

import dm.data.GraphicsFile;
import dm.model.Champion;
import dm.model.ChampionMirror;
import dm.model.Item;
import dm.model.ItemCatalog;
import dm.model.ItemDescription;
import dm.model.Party;
import dm.model.Slot;
import dm.model.Spells;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A champion's inventory screen, drawn over the dungeon view like DM's.
 *
 * Opened from a mirror it shows a candidate with DM's RESURRECT, REINCARNATE
 * and CANCEL panel (graphic 40); REINCARNATE then shows the {@link RenamePanel}.
 * Opened from the champion bars it shows a party member with CLOSE. The X icon in
 * the top-right corner of the background also closes it.
 * Coordinates below are relative to the viewport's top-left corner.
 */
public final class CharacterSheet {

    /**
     * What a click asks for; SLOT means an inventory cell of a party member
     * ({@link #slotAt}), MOUTH feeding them the held item, EYE showing
     * their skills and statistics while the button is held, and DISK the
     * game menu (save, load, quit). REINCARNATE starts renaming a candidate,
     * and RENAMED is the rename panel's OK. SLEEP is the ZZZ icon. CHEST_CELL
     * is a cell of the open chest ({@link #chestCellAt}).
     */
    public enum Action { NONE, RESURRECT, REINCARNATE, RENAMED, CLOSE, SLOT, MOUTH, EYE, DISK, SLEEP, CHEST_CELL }

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

    /** DM's F347 skills and statistics (on the empty panel): skill lines from y 58, statistics from 86, 7 rows apart. */
    private static final int SKILLS_X = 108;
    private static final int SKILLS_Y = 58;
    private static final int STATS_Y = 86;
    private static final int STATS_VALUE_X = 174;
    private static final int STATS_MAX_X = 192;
    private static final int PANEL_LINE = 7;
    /** F355's labels and F287's values for health, stamina and mana, 8 rows apart from y 116; F292's load line. */
    private static final int VITALS_Y = 116;
    private static final int LOAD_X = 104;
    private static final int LOAD_VALUE_X = 148;
    /** DM's resurrect panel (graphic 40, keyed on dark green) and its click boxes (G0457, less the viewport's 33 rows). */
    private static final int RESURRECT_PANEL_KEY = 6;
    private static final Rectangle RESURRECT_BUTTON = new Rectangle(108, 57, 51, 49);
    private static final Rectangle REINCARNATE_BUTTON = new Rectangle(161, 57, 51, 49);
    private static final Rectangle CANDIDATE_CANCEL = new Rectangle(108, 108, 104, 13);
    private static final Rectangle CLOSE_ICON = new Rectangle(208, 1, 13, 12);
    /** DM's click zones on the inventory background (ScummVM's G0447 mouse input table, less the viewport's 33 rows). */
    private static final Rectangle MOUTH = new Rectangle(56, 13, 16, 16);
    private static final Rectangle EYE = new Rectangle(12, 13, 16, 16);
    /**
     * The disk icon top right: opens the {@link GameMenu}. Measured on the PC
     * inventory graphic (17), where it spans x 180-188, y 3-11; ScummVM's
     * click box (174-182) is for another version's layout and only touched
     * the disk's edge.
     */
    private static final Rectangle DISK = new Rectangle(180, 3, 9, 9);
    /** The ZZZ icon beside it (x 190-208, y 2-12 on the PC graphic; ScummVM's 188-204 is another version's). */
    private static final Rectangle SLEEP = new Rectangle(190, 2, 19, 11);

    /** DM's food/water panel (F345): its box, the labels' boxes, and the bars' rows. */
    private static final Point PANEL = new Point(80, 52);
    private static final Point FOOD_LABEL = new Point(112, 60);
    private static final Point WATER_LABEL = new Point(112, 83);
    private static final int BAR_X = 113;
    private static final int FOOD_BAR_Y = 69;
    private static final int WATER_BAR_Y = 92;
    private static final int PANEL_EMPTY = 20;
    private static final int FOOD_LABEL_GRAPHIC = 30;
    private static final int WATER_LABEL_GRAPHIC = 31;

    /** DM's open chest (F333): graphic 25 in the panel box, its 8 cells' icons at G030's slot boxes 38-45. */
    private static final int PANEL_OPEN_CHEST = 25;
    private static final Point[] CHEST_CELLS = {
            new Point(117, 59), new Point(106, 76), new Point(111, 93), new Point(128, 98),
            new Point(145, 101), new Point(162, 103), new Point(179, 104), new Point(196, 105)};
    /** The action hand shows the chest open while its panel is (icon 145, F333). */
    private static final int ICON_OPEN_CHEST = 145;
    /** DM's open scroll (F341): graphic 23 in the panel box; lines centred on x 162 and y 92, 7 rows apart. */
    private static final int PANEL_OPEN_SCROLL = 23;
    private static final int SCROLL_CENTRE_X = 162;
    private static final int SCROLL_CENTRE_Y = 92;
    /** The parchment is 98 pixels wide (x 33-130 of graphic 23): 16 characters. */
    private static final int SCROLL_COLUMNS = 16;
    /** Its rows 6-64 (viewport y 58-116) hold 8 lines 7 apart. */
    private static final int PARCHMENT_TOP = 58;
    private static final int PARCHMENT_HEIGHT = 59;
    private static final int SCROLL_ROWS = 8;
    /** F339: the arrow (graphic 18) over a chest or scroll, or the eye (19) while it's looked at, keyed on red. */
    private static final int ARROW_FOR_CHEST = 18;
    private static final int EYE_FOR_DESCRIPTION = 19;
    private static final Point ARROW_OR_EYE = new Point(83, 57);
    /** F342: the description circle (graphic 29, keyed on darkest grey), the icon in it, the name and the lines. */
    private static final int DESCRIPTION_CIRCLE = 29;
    private static final Point CIRCLE = new Point(105, 53);
    private static final Point DESCRIPTION_ICON = new Point(111, 59);
    private static final Point DESCRIPTION_NAME = new Point(134, 68);
    private static final Point DESCRIPTION_LINES = new Point(108, 87);
    private static final int DESCRIPTION_LINE = 7;

    private static final Color TEXT = Art.PALETTE[13];
    /** DM's F292: the leader's name and title are gold, the others' lightest grey. */
    private static final Color LEADER = Art.PALETTE[9];
    private static final Color YELLOW = Art.PALETTE[11];
    private static final Color RED = Art.PALETTE[8];
    private static final Color LIGHT_GREEN = Art.PALETTE[7];
    private static final List<Champion.Stat> SHOWN_STATS = List.of(
            Champion.Stat.STRENGTH, Champion.Stat.DEXTERITY, Champion.Stat.WISDOM,
            Champion.Stat.VITALITY, Champion.Stat.ANTI_MAGIC, Champion.Stat.ANTI_FIRE);

    private final Art art;
    private Champion champion;
    private ChampionMirror candidate;
    private RenamePanel renaming;
    private Point hover;
    private boolean pressingEye;
    /**
     * The open chest's cells as shown (DM's G425): items keep their cells,
     * gaps included, while the chest stays open; the chest itself holds them
     * packed, as DM's F334 leaves them on closing.
     */
    private final Item[] chestCells = new Item[Item.CHEST_CELLS];

    public CharacterSheet(Art art) {
        this.art = art;
    }

    /** Shows a mirror's champion; DM only does this while the party has room and the hand is empty. */
    public void openCandidate(ChampionMirror mirror) {
        champion = mirror.champion();
        candidate = mirror;
        renaming = null;
    }

    public void openMember(Champion member) {
        champion = member;
        candidate = null;
        renaming = null;
    }

    public void close() {
        champion = null;
        candidate = null;
        renaming = null;
        hover = null;
        pressingEye = false;
    }

    /** While the eye is held down a member's panel shows skills and statistics instead of food and water, as in DM. */
    public void setPressingEye(boolean pressing) {
        pressingEye = pressing;
    }

    public boolean pressingEye() {
        return pressingEye;
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

    /** REINCARNATE: the candidate's panel becomes DM's rename keyboard, with an empty name and title. */
    public void startRenaming() {
        if (candidate != null) {
            renaming = new RenamePanel();
        }
    }

    public boolean renaming() {
        return renaming != null;
    }

    /** The name typed so far on the rename panel. */
    public String newName() {
        return renaming == null ? "" : renaming.name();
    }

    public String newTitle() {
        return renaming == null ? "" : renaming.title();
    }

    /** A key typed while renaming (letters, , . ; : space, Enter, Backspace). */
    public void type(char c) {
        if (renaming != null) {
            renaming.type(c);
        }
    }

    /** Mouse position in screen coordinates, for item name tooltips. */
    public void hover(int x, int y) {
        hover = VIEW.contains(x, y) ? new Point(x - VIEW.x, y - VIEW.y) : null;
    }

    /** Handles a click at screen point (x, y). */
    public Action click(int x, int y) {
        int vx = x - VIEW.x;
        int vy = y - VIEW.y;
        if (renaming != null) {
            // DM's rename panel takes every click until OK.
            return renaming.click(vx, vy) ? Action.RENAMED : Action.NONE;
        }
        if (DISK.contains(vx, vy)) {
            return Action.DISK;
        }
        if (candidate != null) {
            // A candidate's belongings can't be touched until they join.
            if (RESURRECT_BUTTON.contains(vx, vy)) {
                return Action.RESURRECT;
            }
            if (REINCARNATE_BUTTON.contains(vx, vy)) {
                return Action.REINCARNATE;
            }
            if (CANDIDATE_CANCEL.contains(vx, vy) || CLOSE_ICON.contains(vx, vy)) {
                return Action.CLOSE;
            }
            return EYE.contains(vx, vy) ? Action.EYE : Action.NONE;
        }
        if (CLOSE_ICON.contains(vx, vy)) {
            return Action.CLOSE;
        }
        if (SLEEP.contains(vx, vy)) {
            return Action.SLEEP; // not for a candidate, as in DM
        }
        if (slotAt(x, y) != null) {
            return Action.SLOT;
        }
        if (chestCellAt(x, y) >= 0) {
            return Action.CHEST_CELL;
        }
        if (MOUTH.contains(vx, vy)) {
            return Action.MOUTH;
        }
        if (EYE.contains(vx, vy)) {
            return Action.EYE;
        }
        return Action.NONE;
    }

    /** Screen points at the centre of the candidate panel's buttons, for tests. */
    static Point resurrectCentre() {
        return centre(RESURRECT_BUTTON);
    }

    static Point reincarnateCentre() {
        return centre(REINCARNATE_BUTTON);
    }

    static Point candidateCancelCentre() {
        return centre(CANDIDATE_CANCEL);
    }

    private static Point centre(Rectangle r) {
        return new Point(VIEW.x + r.x + r.width / 2, VIEW.y + r.y + r.height / 2);
    }

    static Point sleepCentre() {
        return centre(SLEEP);
    }

    /** Screen point at the centre of the disk icon, for tests. */
    static Point diskCentre() {
        return new Point(VIEW.x + DISK.x + DISK.width / 2, VIEW.y + DISK.y + DISK.height / 2);
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

    /**
     * The chest in a member's action hand, open in the panel, or null (DM's
     * F347: not while the eye is held or for a candidate).
     */
    public Item openChest() {
        if (champion == null || candidate != null || renaming != null || pressingEye) {
            return null;
        }
        Item hand = champion.items().get(Slot.ACTION_HAND);
        return hand != null && hand.category() == Item.Category.CONTAINER ? hand : null;
    }

    /** The open chest's cell under screen point (x, y), or -1. */
    public int chestCellAt(int x, int y) {
        if (openChest() == null) {
            return -1;
        }
        int vx = x - VIEW.x;
        int vy = y - VIEW.y;
        for (int i = 0; i < CHEST_CELLS.length; i++) {
            Point p = CHEST_CELLS[i];
            if (vx >= p.x && vx < p.x + 16 && vy >= p.y && vy < p.y + 16) {
                return i;
            }
        }
        return -1;
    }

    /** Screen point at the centre of chest cell {@code i}, for tests. */
    static Point chestCellCentre(int i) {
        return new Point(VIEW.x + CHEST_CELLS[i].x + 8, VIEW.y + CHEST_CELLS[i].y + 8);
    }

    /**
     * DM's click on a chest cell (F302): the held item goes into cell
     * {@code cell} and whatever was there comes out, which is returned. An
     * item the chest doesn't take (G237's chest bit: never another chest)
     * stays in hand, so {@code held} comes back.
     */
    public Item swapChestCell(int cell, Item held) {
        Item chest = openChest();
        if (chest == null || held != null && !ItemCatalog.fitsChest(held)) {
            return held;
        }
        Item[] cells = chestCells(chest);
        Item out = cells[cell];
        cells[cell] = held;
        champion.place(Slot.ACTION_HAND, chest.withContents(packed(cells)));
        return out;
    }

    /** The cells of {@code chest} as shown: the ones kept while it stays open, else its contents in order. */
    private Item[] chestCells(Item chest) {
        if (!packed(chestCells).equals(chest.contents())) {
            System.arraycopy(cellsOf(chest.contents()), 0, chestCells, 0, chestCells.length);
        }
        return chestCells;
    }

    private static List<Item> packed(Item[] cells) {
        List<Item> out = new ArrayList<>();
        for (Item i : cells) {
            if (i != null) {
                out.add(i);
            }
        }
        return out;
    }

    /**
     * Draws the sheet. The panel follows DM's F347 and F352: while the eye is
     * held, the held item's panel (or skills and statistics with an empty
     * hand); otherwise a chest or scroll in the action hand opens there, and
     * anything else leaves food and water. Item name tooltips are left out
     * while an item is on the pointer.
     */
    public void draw(Graphics2D g, Party party) {
        if (champion == null) {
            return;
        }
        Item held = party.held();
        if (openChest() == null) {
            Arrays.fill(chestCells, null); // DM's F334 closes the chest, packing its cells
        }
        Graphics2D v = (Graphics2D) g.create(VIEW.x, VIEW.y, VIEW.width, VIEW.height);
        try {
            drawBackground(v);
            drawStateBoxes(v);
            drawNameAndTitle(v, party);
            drawItems(v);
            drawVitals(v, party);
            Item hand = champion.items().get(Slot.ACTION_HAND);
            if (renaming != null) {
                renaming.draw(v, art);
            } else if (pressingEye) {
                if (held == null || candidate != null) {
                    drawSkillsAndStats(v);
                } else {
                    drawObjectPanel(v, held, party, true);
                }
                drawLookingEye(v);
            } else if (candidate != null) {
                drawResurrectPanel(v);
            } else if (hand != null && !ItemDescription.describes(hand)) {
                drawObjectPanel(v, hand, party, false);
            } else {
                drawFoodAndWater(v);
            }
            if (held == null) {
                drawTooltip(v);
            }
        } finally {
            v.dispose();
        }
    }

    /** DM's F342: a scroll's text, a chest's cells, or anything else's description, then F339's arrow or eye. */
    private void drawObjectPanel(Graphics2D g, Item item, Party party, boolean looking) {
        switch (item.category()) {
            case SCROLL -> drawScroll(g, item.text());
            case CONTAINER -> drawChest(g, item, looking);
            default -> drawDescription(g, ItemDescription.of(item, champion, party), item);
        }
        BufferedImage mark = art.keyed(looking ? EYE_FOR_DESCRIPTION : ARROW_FOR_CHEST, 8);
        if (mark != null) {
            g.drawImage(mark, ARROW_OR_EYE.x, ARROW_OR_EYE.y, null);
        }
    }

    /** F333: the open chest with an icon in each filled cell; the action hand's chest shows open. */
    private void drawChest(Graphics2D g, Item chest, boolean looking) {
        drawPanel(g, PANEL_OPEN_CHEST);
        Item[] cells = looking ? cellsOf(chest.contents()) : chestCells(chest);
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] != null) {
                drawIcon(g, cells[i], CHEST_CELLS[i].x, CHEST_CELLS[i].y);
            }
        }
        BufferedImage open = looking ? null : art.icon(ICON_OPEN_CHEST);
        if (open != null) {
            Point p = SLOT_ICONS.get(Slot.ACTION_HAND);
            g.drawImage(open, p.x, p.y, null);
        }
    }

    private static Item[] cellsOf(List<Item> contents) {
        Item[] cells = new Item[Item.CHEST_CELLS];
        for (int i = 0; i < contents.size() && i < cells.length; i++) {
            cells[i] = contents.get(i);
        }
        return cells;
    }

    /**
     * F341/F340: the scroll's lines in DM's font, black on white, each centred
     * on x 162 and the block on y 92. Letters use the font's scroll glyphs,
     * 64 codes below the plain ones.
     */
    private void drawScroll(Graphics2D g, String text) {
        drawPanel(g, PANEL_OPEN_SCROLL);
        DmFont font = art.font();
        if (text == null) {
            return;
        }
        List<String> lines = scrollLines(text);
        int spacing = DESCRIPTION_LINE;
        int y = SCROLL_CENTRE_Y - DESCRIPTION_LINE * lines.size() / 2;
        if (lines.size() > SCROLL_ROWS) {
            // Only a line of spell symbols makes more than DM's 8: closer rows, centred on the parchment.
            spacing = DmFont.HEIGHT;
            y = PARCHMENT_TOP + 4 + (PARCHMENT_HEIGHT - spacing * lines.size()) / 2;
        }
        for (String line : lines) {
            int x = SCROLL_CENTRE_X - (DmFont.ADVANCE * line.length() >> 1);
            if (font != null) {
                font.draw(g, scrollGlyphs(line), x, y, Art.PALETTE[0], Art.PALETTE[15]);
            } else {
                PixelFont.draw(g, line, x, y - 4, Art.PALETTE[0]);
            }
            y += spacing;
        }
    }

    /**
     * The scroll's lines, each followed by the symbols of the spells it
     * names (not in DM; {@link Spells#namedIn}): on the same line where they
     * fit the parchment, otherwise on a line of their own after it.
     */
    static List<String> scrollLines(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n")) {
            StringBuilder symbols = new StringBuilder();
            for (String spell : Spells.namedIn(line)) {
                symbols.append(symbols.length() == 0 ? "" : " ").append(spell);
            }
            if (symbols.length() == 0) {
                lines.add(line);
            } else if (line.length() + 1 + symbols.length() <= SCROLL_COLUMNS) {
                lines.add(line + " " + symbols);
            } else {
                lines.add(line);
                lines.add(symbols.toString());
            }
        }
        return lines;
    }

    static String scrollGlyphs(String line) {
        StringBuilder sb = new StringBuilder(line.length());
        for (char c : line.toCharArray()) {
            sb.append(c >= 'A' && c <= 'Z' ? (char) (c - 64) : c);
        }
        return sb.toString();
    }

    /** F342: the circle with the icon, the name beside it and the lines under it, light grey on dark grey. */
    private void drawDescription(Graphics2D g, ItemDescription description, Item item) {
        drawPanel(g, PANEL_EMPTY);
        BufferedImage circle = art.keyed(DESCRIPTION_CIRCLE, 12);
        if (circle != null) {
            g.drawImage(circle, CIRCLE.x, CIRCLE.y, null);
        }
        drawIcon(g, item, DESCRIPTION_ICON.x, DESCRIPTION_ICON.y);
        printPanelText(g, description.name(), DESCRIPTION_NAME.x, DESCRIPTION_NAME.y);
        // DM's descriptions take at most 6 rows; a weapon's actions can make 7 (not in DM): closer rows,
        // starting 2 higher so the last clears the panel's border.
        boolean seven = description.lines().size() > 6;
        int y = DESCRIPTION_LINES.y - (seven ? 2 : 0);
        int spacing = seven ? DmFont.HEIGHT : DESCRIPTION_LINE;
        for (String line : description.lines()) {
            printPanelText(g, line, DESCRIPTION_LINES.x, y);
            y += spacing;
        }
    }

    /** DM's F052: text in the viewport, in a colour on darkest grey. */
    private void printPanelText(Graphics2D g, String text, int x, int y) {
        print(g, text, x, y, TEXT);
    }

    /** DM's F052 in any colour; without DM's font, the pixel font at the same place. */
    private void print(Graphics2D g, String text, int x, int y, Color colour) {
        DmFont font = art.font();
        if (font != null) {
            font.draw(g, text, x, y, colour, Art.PALETTE[12]);
        } else {
            PixelFont.draw(g, text, x, y - 4, colour);
        }
    }

    private void drawPanel(Graphics2D g, int graphic) {
        BufferedImage panel = art.keyed(graphic, 8);
        if (panel != null) {
            g.drawImage(panel, PANEL.x, PANEL.y, null);
        } else {
            g.setColor(Art.PALETTE[12]);
            g.fillRect(PANEL.x, PANEL.y, 144, 73);
        }
    }

    private void drawIcon(Graphics2D g, Item item, int x, int y) {
        BufferedImage icon = art.icon(item);
        if (icon != null) {
            g.drawImage(icon, x, y, null);
        } else {
            Placeholders.icon(g, item, x, y);
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

    /** DM's red slot box, for a wounded body part, a hungry, thirsty or poisoned mouth, or a weakened eye. */
    private static final int SLOT_BOX_WOUNDED = 34;
    /** DM's empty-slot outlines start with the ready hand (212); each body part has a normal and a wounded one. */
    private static final int EMPTY_SLOT_ICON = 212;
    /** F291's other empty-slot outlines: neck 208, pouch 1 209, quiver 1 210, the first backpack cell 211 (#43). */
    private static final List<Slot> OUTLINED_SLOTS = List.of(Slot.NECK, Slot.POUCH_1, Slot.QUIVER_1, Slot.BACKPACK_1);
    private static final int EMPTY_NECK_ICON = 208;
    /** DM's POISONED label (graphic 32) and its place in the food and water panel (G037). */
    private static final int POISONED_LABEL = 32;
    private static final Point POISONED = new Point(112, 105);

    /**
     * DM's F291/F292: an empty body part's cell shows its outline; a wounded
     * one gets the red slot box and, if empty, the wounded outline (DM's icon
     * after the normal one). The neck, the first pouch, the first quiver cell
     * and the first backpack cell show their own outlines when empty. The mouth
     * box is red while the champion is hungry, thirsty or poisoned, and the
     * eye box while any statistic is below its maximum.
     */
    private void drawStateBoxes(Graphics2D g) {
        for (int part = 0; part < Champion.WOUND_SLOTS.size(); part++) {
            Slot slot = Champion.WOUND_SLOTS.get(part);
            boolean wounded = champion.isWounded(slot);
            Point p = SLOT_ICONS.get(slot);
            if (wounded) {
                drawWoundedBox(g, p.x - 1, p.y - 1);
            }
            if (champion.items().get(slot) == null) {
                drawOutline(g, EMPTY_SLOT_ICON + part * 2 + (wounded ? 1 : 0), p);
            }
        }
        for (int i = 0; i < OUTLINED_SLOTS.size(); i++) {
            Slot slot = OUTLINED_SLOTS.get(i);
            if (champion.items().get(slot) == null) {
                drawOutline(g, EMPTY_NECK_ICON + i, SLOT_ICONS.get(slot));
            }
        }
        if (champion.food() < 0 || champion.water() < 0 || champion.poisoned()) {
            drawWoundedBox(g, MOUTH.x - 1, MOUTH.y - 1);
        }
        for (Champion.Stat s : Champion.Stat.values()) {
            if (s != Champion.Stat.LUCK && champion.stat(s) < champion.maxStat(s)) {
                drawWoundedBox(g, EYE.x - 1, EYE.y - 1);
                break;
            }
        }
    }

    private void drawOutline(Graphics2D g, int icon, Point p) {
        BufferedImage outline = art.icon(icon);
        if (outline != null) {
            g.drawImage(outline, p.x, p.y, null);
        }
    }

    private void drawWoundedBox(Graphics2D g, int x, int y) {
        BufferedImage box = art.keyed(SLOT_BOX_WOUNDED, 12);
        if (box != null) {
            g.drawImage(box, x, y, null);
        } else {
            g.setColor(Art.PALETTE[8]);
            g.drawRect(x, y, 17, 17);
        }
    }

    private void drawItems(Graphics2D g) {
        for (Map.Entry<Slot, Item> e : champion.items().entrySet()) {
            Point p = SLOT_ICONS.get(e.getKey());
            BufferedImage icon = art.icon(ItemCatalog.shownIn(e.getValue(), e.getKey()));
            if (icon != null) {
                g.drawImage(icon, p.x, p.y, null);
            } else {
                Placeholders.icon(g, e.getValue(), p.x, p.y);
            }
        }
    }

    /** DM's F292: name and title at (3, 7), the title a cell further on unless it starts with , ; or -. */
    private void drawNameAndTitle(Graphics2D g, Party party) {
        Color colour = party.leader() == champion ? LEADER : TEXT;
        print(g, champion.name(), 3, 7, colour);
        int x = 6 * champion.name().length() + 3;
        char first = champion.title().isEmpty() ? ' ' : champion.title().charAt(0);
        if (first != ',' && first != ';' && first != '-') {
            x += 6;
        }
        print(g, champion.title(), x, 7, colour);
    }

    /** DM's F355 labels and F287 values ("%3d/%3d"), then F292's load line beside MANA. */
    private void drawVitals(Graphics2D g, Party party) {
        vitalLine(g, "HEALTH", champion.health(), champion.maxHealth(), VITALS_Y);
        vitalLine(g, "STAMINA", champion.stamina(), champion.maxStamina(), VITALS_Y + 8);
        vitalLine(g, "MANA", champion.mana(), champion.maxMana(), VITALS_Y + 16);
        int load = party.load(champion);
        int max = champion.maxLoad();
        Color colour = loadColour(load, max);
        print(g, "LOAD ", LOAD_X, VITALS_Y + 16, colour);
        print(g, loadText(load, max), LOAD_VALUE_X, VITALS_Y + 16, colour);
    }

    private void vitalLine(Graphics2D g, String label, int value, int max, int y) {
        print(g, label, 5, y, TEXT);
        print(g, pad(value), 55, y, TEXT);
        print(g, "/", 73, y, TEXT);
        print(g, pad(max), 79, y, TEXT);
    }

    /** F292: red over the maximum load, yellow over five eighths of it, otherwise lightest grey. */
    static Color loadColour(int load, int max) {
        if (load > max) {
            return RED;
        }
        return ((long) load << 3) > (long) max * 5 ? YELLOW : TEXT;
    }

    /** F292's "%3d.%d/%3d KG": the load in tenths of a kilogram, the maximum rounded to whole ones. */
    static String loadText(int load, int max) {
        return pad(load / 10) + "." + (load % 10) + "/" + pad((max + 5) / 10) + " KG";
    }

    /** DM's F288 with padding: right-aligned in 3 characters. */
    private static String pad(int value) {
        return String.format("%3d", value);
    }

    /**
     * DM's F347 on the empty panel: each base skill above level 1 (no
     * temporary experience, at most 16) as "LEVEL SKILL", then the
     * statistics, the current value red below its maximum and light green
     * above it.
     */
    private void drawSkillsAndStats(Graphics2D g) {
        BufferedImage panel = art.keyed(PANEL_EMPTY, 8);
        if (panel != null) {
            g.drawImage(panel, PANEL.x, PANEL.y, null);
        }
        int y = SKILLS_Y;
        for (int s = 0; s < Champion.BASE_SKILLS.size(); s++) {
            int level = champion.lastingSkillLevel(s);
            if (level > 1) {
                print(g, Champion.LEVEL_NAMES.get(level - 2) + " " + Champion.BASE_SKILLS.get(s), SKILLS_X, y, TEXT);
                y += PANEL_LINE;
            }
        }
        y = STATS_Y;
        for (Champion.Stat stat : SHOWN_STATS) {
            int value = champion.stat(stat);
            int max = champion.maxStat(stat);
            print(g, stat.label(), SKILLS_X, y, TEXT);
            print(g, pad(value), STATS_VALUE_X, y, value < max ? RED : value > max ? LIGHT_GREEN : TEXT);
            print(g, "/" + pad(max), STATS_MAX_X, y, TEXT);
            y += PANEL_LINE;
        }
    }

    /**
     * While the eye is held DM draws it looking down to the right (icon 203,
     * F352) over the background's eye; letting go brings back icon 202,
     * which is what the background already shows.
     */
    private void drawLookingEye(Graphics2D g) {
        BufferedImage eye = art.icon(EYE_LOOKING);
        if (eye != null) {
            g.drawImage(eye, EYE.x, EYE.y, null);
        }
    }

    private static final int EYE_LOOKING = 203;

    /** DM's F345: the empty panel with FOOD and WATER labels and a bar for each. */
    private void drawFoodAndWater(Graphics2D g) {
        BufferedImage panel = art.keyed(PANEL_EMPTY, 8);
        BufferedImage food = art.keyed(FOOD_LABEL_GRAPHIC, 12);
        BufferedImage water = art.keyed(WATER_LABEL_GRAPHIC, 12);
        if (panel != null) {
            g.drawImage(panel, PANEL.x, PANEL.y, null);
        }
        if (food != null && water != null) {
            g.drawImage(food, FOOD_LABEL.x, FOOD_LABEL.y, null);
            g.drawImage(water, WATER_LABEL.x, WATER_LABEL.y, null);
        } else {
            PixelFont.draw(g, "FOOD", FOOD_LABEL.x, FOOD_LABEL.y + 2, TEXT);
            PixelFont.draw(g, "WATER", WATER_LABEL.x, WATER_LABEL.y + 2, TEXT);
        }
        drawFoodOrWaterBar(g, champion.food(), FOOD_BAR_Y, Art.PALETTE[5]);
        drawFoodOrWaterBar(g, champion.water(), WATER_BAR_Y, Art.PALETTE[14]);
        if (champion.poisoned()) {
            BufferedImage label = art.keyed(POISONED_LABEL, 12);
            if (label != null) {
                g.drawImage(label, POISONED.x, POISONED.y, null);
            } else {
                PixelFont.draw(g, "POISONED", POISONED.x, POISONED.y + 4, Art.PALETTE[8]);
            }
        }
    }

    /** DM's F344: a 7-row bar, (amount + 1024) / 32 + 1 pixels long, with a black shadow; yellow when hungry, red when starving. */
    private static void drawFoodOrWaterBar(Graphics2D g, int amount, int y, Color color) {
        Color c = amount < -512 ? Art.PALETTE[8] : amount < 0 ? Art.PALETTE[11] : color;
        int width = Math.min(amount + 1024, 3071) >> 5;
        g.setColor(Art.PALETTE[0]);
        g.fillRect(BAR_X + 2, y + 2, width + 1, 7);
        g.setColor(c);
        g.fillRect(BAR_X, y, width + 1, 7);
    }

    /** DM's F0283: graphic 40 in the panel box, or its three buttons drawn by hand. */
    private void drawResurrectPanel(Graphics2D g) {
        BufferedImage panel = art.keyed(GraphicsFile.RESURRECT_PANEL, RESURRECT_PANEL_KEY);
        if (panel != null) {
            g.drawImage(panel, PANEL.x, PANEL.y, null);
            return;
        }
        button(g, RESURRECT_BUTTON, "RESUR", Art.PALETTE[4], Art.PALETTE[14]);
        button(g, REINCARNATE_BUTTON, "REINC", Art.PALETTE[4], Art.PALETTE[14]);
        button(g, CANDIDATE_CANCEL, "CANCEL", Art.PALETTE[11], Art.PALETTE[8]);
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
