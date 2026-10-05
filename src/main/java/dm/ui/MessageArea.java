package dm.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * DM's message area (TEXT.C, F047-F051): four 7-pixel rows at the bottom
 * of the screen (y 172-199), 53 six-pixel columns wide. A message starts a
 * new row, scrolling the others up when the bottom row is in use; words
 * that don't fit wrap to a new row indented by 2. A row is cleared 200
 * game ticks after its last text.
 */
final class MessageArea {

    static final int TOP = 172;
    private static final int ROWS = 4;
    private static final int ROW_HEIGHT = 7;
    private static final int COLUMNS = 53;
    private static final int CELL = 6;
    /** Ticks a row stays after its last text. */
    static final int LIFETIME = 200;

    private record Segment(int column, String text, Color color) {
    }

    private final List<List<Segment>> rows = new ArrayList<>();
    private final long[] expires = new long[ROWS];
    private int row = ROWS - 1;
    private int column;

    MessageArea() {
        for (int r = 0; r < ROWS; r++) {
            rows.add(new ArrayList<>());
            expires[r] = -1;
        }
    }

    /** Prints {@code text} on a new row (DM's "\n" + message), at game time {@code now}. */
    void print(String text, Color color, long now) {
        if (column != 0 || row != 0) {
            column = 0;
            newRow();
        }
        for (String word : text.split(" ", -1)) {
            if (word.isEmpty()) {
                if (column != COLUMNS) {
                    put(" ", color, now);
                }
                continue;
            }
            if (column + word.length() > COLUMNS) {
                column = 2;
                newRow();
            }
            put(word, color, now);
            if (column < COLUMNS) {
                put(" ", color, now);
            }
        }
    }

    private void put(String s, Color color, long now) {
        rows.get(row).add(new Segment(column, s, color));
        column += s.length();
        expires[row] = now + LIFETIME;
    }

    private void newRow() {
        if (row == ROWS - 1) {
            rows.remove(0);
            rows.add(new ArrayList<>());
            System.arraycopy(expires, 1, expires, 0, ROWS - 1);
            expires[ROWS - 1] = -1;
        } else {
            row++;
        }
    }

    /** Whether any row still shows text. */
    boolean hasText() {
        for (long e : expires) {
            if (e >= 0) {
                return true;
            }
        }
        return false;
    }

    /** DM's F052: rows whose time is up go blank. */
    /** DM's F043: every row cleared, and the next message starts a new row at the bottom. */
    void clearAll() {
        for (int r = 0; r < ROWS; r++) {
            rows.get(r).clear();
            expires[r] = -1;
        }
        row = ROWS - 1;
        column = 0;
    }

    void clearExpired(long now) {
        for (int r = 0; r < ROWS; r++) {
            if (expires[r] >= 0 && expires[r] <= now) {
                rows.get(r).clear();
                expires[r] = -1;
            }
        }
    }

    /** The text on row {@code r} (0 top), for tests. */
    String rowText(int r) {
        StringBuilder sb = new StringBuilder();
        for (Segment s : rows.get(r)) {
            while (sb.length() < s.column()) {
                sb.append(' ');
            }
            sb.append(s.text());
        }
        return sb.toString().stripTrailing();
    }

    void draw(Graphics2D g) {
        for (int r = 0; r < ROWS; r++) {
            for (Segment s : rows.get(r)) {
                PixelFont.draw(g, s.text(), s.column() * CELL, TOP + r * ROW_HEIGHT + 1, s.color());
            }
        }
    }
}
