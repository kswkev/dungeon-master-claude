package dm.ui;

import dm.data.SaveGames;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.IntFunction;

/**
 * The game menu behind the disk icon on the character sheet: SAVE, LOAD,
 * QUIT, OPTIONS (not yet used) and CANCEL, with the save slots, the quit
 * question and short messages. No Swing: like the sheet it is drawn over the
 * viewport and hit-tested in screen coordinates.
 *
 * Everything is built from DM's dialog box (graphic 0, 224x136, ReDMCSB
 * F427). It holds a message panel, a wide button and two half buttons; DM
 * patches it for one to four choices and prints them in gold on brown,
 * centred. The game menu needs five choices, so its own screens rearrange
 * those pieces into a title strip and three rows of buttons. The quit
 * question and messages use DM's own three- and one-choice layouts.
 */
public final class GameMenu {

    /** Which set of buttons is up. */
    public enum Screen { MAIN, SAVE_SLOTS, LOAD_SLOTS, QUIT, MESSAGE }

    /** What a click picked. SLOT carries the slot number. */
    public enum Choice { NONE, SAVE, LOAD, QUIT, OPTIONS, CANCEL, SLOT, SAVE_AND_QUIT, QUIT_NOW, OK }

    public record Click(Choice choice, int slot) {
        static final Click NONE = new Click(Choice.NONE, 0);

        static Click of(Choice choice) {
            return new Click(choice, 0);
        }
    }

    private static final Rectangle VIEW = ViewRenderer.VIEWPORT;
    private static final int DIALOG = 0;

    // Pieces of graphic 0 (viewport coordinates), measured from the PC file.
    private static final Rectangle PANEL = new Rectangle(10, 10, 204, 42);
    private static final Rectangle WIDE = new Rectangle(10, 62, 204, 27);
    private static final Rectangle HALVES = new Rectangle(10, 99, 204, 27);
    private static final int RIGHT_HALF_X = 117;
    private static final int HALF_WIDTH = 98;

    // The game menu's own layout: a title strip and three rows.
    private static final Rectangle TITLE = new Rectangle(10, 8, 204, 15);
    private static final int ROW_1 = 29;
    private static final int ROW_2 = 64;
    private static final int ROW_3 = 99;

    private static final Color GOLD = Art.PALETTE[9];
    private static final Color YELLOW = Art.PALETTE[11];
    private static final Color GREY = Art.PALETTE[1];
    private static final DateTimeFormatter SAVED_AT = DateTimeFormatter.ofPattern("dd/MM HH:mm")
            .withZone(ZoneId.systemDefault());

    private final Art art;
    private Screen screen;
    private String message;
    private String detail;
    /** Set when SAVE AND QUIT sent us to the save slots: quit once saved. */
    private boolean quitAfterSave;

    public GameMenu(Art art) {
        this.art = art;
    }

    public void open() {
        screen = Screen.MAIN;
        quitAfterSave = false;
    }

    public void close() {
        screen = null;
        quitAfterSave = false;
    }

    public boolean isOpen() {
        return screen != null;
    }

    public Screen screen() {
        return screen;
    }

    public boolean quitAfterSave() {
        return quitAfterSave;
    }

    public void showSlots(boolean save) {
        screen = save ? Screen.SAVE_SLOTS : Screen.LOAD_SLOTS;
    }

    public void showQuit() {
        screen = Screen.QUIT;
    }

    /** SAVE AND QUIT: pick a slot, then the game ends once it is saved. */
    public void saveThenQuit() {
        screen = Screen.SAVE_SLOTS;
        quitAfterSave = true;
    }

    /** A message with an OK button; {@code detail} (may be null) goes on a second line. */
    public void showMessage(String message, String detail) {
        screen = Screen.MESSAGE;
        this.message = message;
        this.detail = detail;
    }

    // ---- buttons ------------------------------------------------------------

    private static Rectangle left(int y) {
        return new Rectangle(HALVES.x, y, HALF_WIDTH, HALVES.height);
    }

    private static Rectangle right(int y) {
        return new Rectangle(RIGHT_HALF_X, y, HALVES.x + HALVES.width - RIGHT_HALF_X, HALVES.height);
    }

    private static Rectangle wide(int y) {
        return new Rectangle(WIDE.x, y, WIDE.width, WIDE.height);
    }

    /** The choice under screen point (x, y). */
    public Click click(int x, int y) {
        if (screen == null) {
            return Click.NONE;
        }
        int vx = x - VIEW.x;
        int vy = y - VIEW.y;
        return switch (screen) {
            case MAIN -> left(ROW_1).contains(vx, vy) ? Click.of(Choice.SAVE)
                    : right(ROW_1).contains(vx, vy) ? Click.of(Choice.LOAD)
                    : left(ROW_2).contains(vx, vy) ? Click.of(Choice.QUIT)
                    : right(ROW_2).contains(vx, vy) ? Click.of(Choice.OPTIONS)
                    : wide(ROW_3).contains(vx, vy) ? Click.of(Choice.CANCEL) : Click.NONE;
            case SAVE_SLOTS, LOAD_SLOTS -> {
                Rectangle[] slots = slotButtons();
                for (int i = 0; i < slots.length; i++) {
                    if (slots[i].contains(vx, vy)) {
                        yield new Click(Choice.SLOT, i + 1);
                    }
                }
                yield wide(ROW_3).contains(vx, vy) ? Click.of(Choice.CANCEL) : Click.NONE;
            }
            case QUIT -> wide(WIDE.y).contains(vx, vy) ? Click.of(Choice.SAVE_AND_QUIT)
                    : left(HALVES.y).contains(vx, vy) ? Click.of(Choice.QUIT_NOW)
                    : right(HALVES.y).contains(vx, vy) ? Click.of(Choice.CANCEL) : Click.NONE;
            case MESSAGE -> wide(HALVES.y).contains(vx, vy) ? Click.of(Choice.OK) : Click.NONE;
        };
    }

    private static Rectangle[] slotButtons() {
        return new Rectangle[] {left(ROW_1), right(ROW_1), left(ROW_2), right(ROW_2)};
    }

    /** Screen point at the centre of a choice, for tests and scripted clicks. */
    static java.awt.Point centre(Screen screen, Choice choice, int slot) {
        Rectangle r = switch (screen) {
            case MAIN -> switch (choice) {
                case SAVE -> left(ROW_1);
                case LOAD -> right(ROW_1);
                case QUIT -> left(ROW_2);
                case OPTIONS -> right(ROW_2);
                default -> wide(ROW_3);
            };
            case SAVE_SLOTS, LOAD_SLOTS -> choice == Choice.SLOT ? slotButtons()[slot - 1] : wide(ROW_3);
            case QUIT -> switch (choice) {
                case SAVE_AND_QUIT -> wide(WIDE.y);
                case QUIT_NOW -> left(HALVES.y);
                default -> right(HALVES.y);
            };
            case MESSAGE -> wide(HALVES.y);
        };
        return new java.awt.Point(VIEW.x + r.x + r.width / 2, VIEW.y + r.y + r.height / 2);
    }

    // ---- drawing ------------------------------------------------------------

    /** Draws the current screen over the viewport; {@code headers} gives each slot's saved game, or null. */
    public void draw(Graphics2D g, IntFunction<SaveGames.Header> headers) {
        if (screen == null) {
            return;
        }
        Graphics2D v = (Graphics2D) g.create(VIEW.x, VIEW.y, VIEW.width, VIEW.height);
        try {
            BufferedImage box = art.image(DIALOG);
            switch (screen) {
                case MAIN -> {
                    menuLayout(v, box, "GAME MENU");
                    choice(v, "SAVE", left(ROW_1));
                    choice(v, "LOAD", right(ROW_1));
                    choice(v, "QUIT", left(ROW_2));
                    choice(v, "OPTIONS", right(ROW_2));
                    choice(v, "CANCEL", wide(ROW_3));
                }
                case SAVE_SLOTS, LOAD_SLOTS -> {
                    menuLayout(v, box, screen == Screen.SAVE_SLOTS ? "SAVE GAME" : "LOAD GAME");
                    Rectangle[] slots = slotButtons();
                    for (int i = 0; i < slots.length; i++) {
                        slot(v, i + 1, headers.apply(i + 1), slots[i]);
                    }
                    choice(v, "CANCEL", wide(ROW_3));
                }
                case QUIT -> {
                    dmLayout(v, box, false);
                    centred(v, "QUIT THE GAME?", PANEL.y + 19, YELLOW);
                    choice(v, "SAVE AND QUIT", wide(WIDE.y));
                    choice(v, "QUIT", left(HALVES.y));
                    choice(v, "CANCEL", right(HALVES.y));
                }
                case MESSAGE -> {
                    dmLayout(v, box, true);
                    int y = detail == null ? PANEL.y + 19 : PANEL.y + 13;
                    centred(v, message, y, YELLOW);
                    if (detail != null) {
                        centred(v, detail, y + 10, GOLD);
                    }
                    choice(v, "OK", wide(HALVES.y));
                }
            }
        } finally {
            v.dispose();
        }
    }

    /** DM's dialog as it is (three choices), or with the middle button cleared and one wide button below. */
    private static void dmLayout(Graphics2D g, BufferedImage box, boolean oneChoice) {
        if (box == null) {
            plain(g, PANEL);
            if (!oneChoice) {
                plain(g, WIDE);
                plain(g, left(HALVES.y));
                plain(g, right(HALVES.y));
            } else {
                plain(g, wide(HALVES.y));
            }
            return;
        }
        g.drawImage(box, 0, 0, null);
        if (oneChoice) {
            g.setColor(GREY);
            g.fillRect(WIDE.x, WIDE.y, WIDE.width, WIDE.height);
            paste(g, box, WIDE, WIDE.x, HALVES.y);
        }
    }

    /** The game menu's layout: a title strip, two rows of half buttons and a wide one. */
    private static void menuLayout(Graphics2D g, BufferedImage box, String title) {
        if (box == null) {
            plain(g, TITLE);
            for (Rectangle r : slotButtons()) {
                plain(g, r);
            }
            plain(g, wide(ROW_3));
        } else {
            g.drawImage(box, 0, 0, null);
            g.setColor(GREY);
            g.fillRect(1, 1, box.getWidth() - 2, box.getHeight() - 2);
            // The title strip: the panel's top 7 rows and bottom 8 rows.
            paste(g, box, new Rectangle(PANEL.x, PANEL.y, PANEL.width, 7), TITLE.x, TITLE.y);
            paste(g, box, new Rectangle(PANEL.x, PANEL.y + PANEL.height - 8, PANEL.width, 8), TITLE.x, TITLE.y + 7);
            paste(g, box, HALVES, HALVES.x, ROW_1);
            paste(g, box, HALVES, HALVES.x, ROW_2);
            paste(g, box, WIDE, WIDE.x, ROW_3);
        }
        centred(g, title, TITLE.y + 5, YELLOW);
    }

    private static void paste(Graphics2D g, BufferedImage box, Rectangle from, int x, int y) {
        g.drawImage(box.getSubimage(from.x, from.y, from.width, from.height), x, y, null);
    }

    /** Without GRAPHICS.DAT: brown boxes with a dark edge. */
    private static void plain(Graphics2D g, Rectangle r) {
        g.setColor(Art.PALETTE[5]);
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(Art.PALETTE[0]);
        g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
    }

    /** A choice's text, centred on its button in gold, as DM's F425. */
    private static void choice(Graphics2D g, String text, Rectangle button) {
        int x = button.x + (button.width - PixelFont.width(text)) / 2;
        int y = button.y + (button.height - PixelFont.HEIGHT) / 2;
        PixelFont.draw(g, text, x, y, GOLD);
    }

    /** A slot button: its number and level, and when it was saved (or EMPTY). */
    private static void slot(Graphics2D g, int number, SaveGames.Header header, Rectangle button) {
        if (header == null) {
            choice(g, number + "  EMPTY", button);
            return;
        }
        String first = number + "  LEVEL " + (header.level() + 1);
        String second = SAVED_AT.format(Instant.ofEpochMilli(header.savedAt()));
        int y = button.y + button.height / 2 - PixelFont.HEIGHT - 1;
        PixelFont.draw(g, first, button.x + (button.width - PixelFont.width(first)) / 2, y, GOLD);
        PixelFont.draw(g, second, button.x + (button.width - PixelFont.width(second)) / 2, y + 8, GOLD);
    }

    /** Text centred across the dialog, cut to fit inside the panel. */
    private static void centred(Graphics2D g, String text, int y, Color color) {
        int fits = (PANEL.width - 6) / PixelFont.ADVANCE;
        if (text.length() > fits) {
            text = text.substring(0, fits - 2) + "..";
        }
        PixelFont.draw(g, text, (VIEW.width - PixelFont.width(text)) / 2, y, color);
    }
}
