package dm.ui;

import dm.model.Direction;
import dm.model.DungeonMap;
import dm.model.Party;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * DM's entrance (ReDMCSB STARTND2.C F438-F441, ScummVM's drawEntrance and
 * openEntranceDoors): graphic 4 with the dungeon's closed doors (graphics
 * 2 and 3) over a little corridor seen facing south, all in the entrance's
 * own palette. The PC graphic offers ENTER, RESUME and QUIT. ENTER clicks,
 * then the doors slide apart, 4 pixels a step for 31 steps with a rattle
 * every third, and the game begins.
 */
final class Entrance {

    /** DM's G0445 boxes: ENTER and RESUME; QUIT is where the PC graphic draws it, under RESUME. */
    static final Rectangle ENTER = new Rectangle(244, 45, 55, 14);
    static final Rectangle RESUME = new Rectangle(244, 76, 55, 18);
    static final Rectangle QUIT = new Rectangle(244, 107, 55, 18);

    private static final int SCREEN = 4;
    private static final int LEFT_DOOR = 2;
    private static final int RIGHT_DOOR = 3;
    /** The doors' top row, and the closed right door's left edge (G010/G011). */
    private static final int DOORS_Y = 30;
    private static final int RIGHT_DOOR_X = 105;
    /** The doorway's right edge (G011's x 231). */
    private static final int DOORWAY_RIGHT = 231;
    /** ScummVM's delay(3) between steps: 3 vertical blanks. */
    static final long STEP_MS = 50;
    static final int STEPS = 31;

    /** G0020: the entrance's palette, PC VGA (4 bits a channel). */
    private static final int[] PALETTE = {0x000, 0x666, 0x888, 0x840, 0xCA8, 0x0C0, 0x080, 0x0A0, 0x864, 0xF00,
            0xA86, 0x642, 0x444, 0xAAA, 0x620, 0xFFF};

    private final Art art;
    /** F439's micro dungeon: a corridor running south with an opening on its west side, 2 squares ahead. */
    private final Party corridor = new Party(DungeonMap.fromAscii(0,
            "##.##",
            "##.##",
            "#..##",
            "##.##",
            "##.##"), 2, 0, Direction.SOUTH);
    private BufferedImage closed;
    private BufferedImage behind;
    private BufferedImage left;
    private BufferedImage right;
    /** When ENTER was clicked (the clock's milliseconds), or -1 while waiting. */
    private long openedAt = -1;

    Entrance(Art art) {
        this.art = art;
    }

    boolean opening() {
        return openedAt >= 0;
    }

    void open(long now) {
        openedAt = now;
    }

    /** The door step reached by {@code now}: 0 while closed, 1-31 opening, over 31 open. */
    int step(long now) {
        return openedAt < 0 ? 0 : 1 + (int) ((now - openedAt) / STEP_MS);
    }

    boolean done(long now) {
        return step(now) > STEPS;
    }

    void draw(Graphics2D g, ViewRenderer view, long now) {
        if (closed == null) {
            prepare(view);
        }
        int step = step(now);
        if (step == 0) {
            g.drawImage(closed, 0, 0, null);
            return;
        }
        g.drawImage(behind, 0, 0, null);
        int shift = 4 * (Math.min(step, STEPS) - 1);
        if (left != null) {
            // ScummVM: the left door's box (0-100) loses 4 columns a step as its picture moves left
            int width = Math.max(0, 101 - shift);
            if (width > 0) {
                g.drawImage(left.getSubimage(shift, 0, width, left.getHeight()), 0, DOORS_Y, null);
            }
        }
        if (right != null) {
            int x = RIGHT_DOOR_X + 4 + shift;
            int width = Math.min(right.getWidth(), DOORWAY_RIGHT + 1 - x);
            if (width > 0) {
                g.drawImage(right.getSubimage(0, 0, width, right.getHeight()), x, DOORS_Y, null);
            }
        }
    }

    /** F439: the screen with the corridor in the viewport, with and without the closed doors. */
    private void prepare(ViewRenderer view) {
        behind = new BufferedImage(GameScreen.WIDTH, GameScreen.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = behind.createGraphics();
        BufferedImage screen = art.image(SCREEN);
        if (screen != null) {
            g.drawImage(screen, 0, 0, null);
        } else {
            g.setColor(Art.PALETTE[12]);
            g.fillRect(0, 0, GameScreen.WIDTH, GameScreen.HEIGHT);
            labels(g);
        }
        Rectangle v = ViewRenderer.VIEWPORT;
        g.setClip(v.x, v.y, v.width, v.height);
        view.draw(g, corridor);
        g.dispose();
        left = art.image(LEFT_DOOR);
        right = art.image(RIGHT_DOOR);
        if (screen != null) {
            recolour(behind);
            left = left == null ? null : recolour(left);
            right = right == null ? null : recolour(right);
        }
        closed = new BufferedImage(GameScreen.WIDTH, GameScreen.HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D c = closed.createGraphics();
        c.drawImage(behind, 0, 0, null);
        if (left != null) {
            c.drawImage(left, 0, DOORS_Y, null);
        }
        if (right != null) {
            c.drawImage(right.getSubimage(0, 0, DOORWAY_RIGHT + 1 - RIGHT_DOOR_X, right.getHeight()), RIGHT_DOOR_X,
                    DOORS_Y, null);
        } else {
            c.setColor(Art.PALETTE[3]);
            c.fillRect(0, DOORS_Y, DOORWAY_RIGHT + 1, 161);
        }
        c.dispose();
    }

    /** Without GRAPHICS.DAT: the three choices written in their boxes. */
    private static void labels(Graphics2D g) {
        String[] names = {"ENTER", "RESUME", "QUIT"};
        Rectangle[] boxes = {ENTER, RESUME, QUIT};
        for (int i = 0; i < 3; i++) {
            g.setColor(Art.PALETTE[13]);
            g.drawRect(boxes[i].x, boxes[i].y, boxes[i].width - 1, boxes[i].height - 1);
            PixelFont.draw(g, names[i], boxes[i].x + 4, boxes[i].y + 4, Art.PALETTE[11]);
        }
    }

    /** The picture through the entrance's palette instead of the dungeon's (DM fades to G0020). */
    private static BufferedImage recolour(BufferedImage image) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < 16; i++) {
            int c = PALETTE[i];
            int rgb = ((c >> 8 & 15) * 17) << 16 | ((c >> 4 & 15) * 17) << 8 | (c & 15) * 17;
            map.put(Art.PALETTE[i].getRGB() & 0xFFFFFF, rgb);
        }
        BufferedImage out = image.getType() == BufferedImage.TYPE_INT_RGB ? image
                : new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y) & 0xFFFFFF;
                out.setRGB(x, y, map.getOrDefault(rgb, rgb));
            }
        }
        return out;
    }

    /** What a click on the waiting entrance chose. */
    enum Choice { NONE, ENTER, RESUME, QUIT }

    Choice click(int x, int y) {
        if (opening()) {
            return Choice.NONE;
        }
        return ENTER.contains(x, y) ? Choice.ENTER : RESUME.contains(x, y) ? Choice.RESUME
                : QUIT.contains(x, y) ? Choice.QUIT : Choice.NONE;
    }
}
