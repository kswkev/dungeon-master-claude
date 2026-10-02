package dm.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Arc2D;

/**
 * DM's six-button movement arrow panel, in screen-space (320x200) coordinates.
 * <pre>
 *   [turn left] [forward ] [turn right]
 *   [left     ] [backward] [right     ]
 * </pre>
 */
public final class MovementPanel {

    public enum Action { TURN_LEFT, FORWARD, TURN_RIGHT, STRAFE_LEFT, BACKWARD, STRAFE_RIGHT }

    /** Panel area on screen, matching the original's arrow block. */
    public static final Rectangle AREA = new Rectangle(233, 124, 87, 45);

    private static final int BUTTON_W = 28;
    private static final int BUTTON_H = 21;
    private static final int GAP = 1;

    private static final Color PANEL_BG = new Color(0, 0, 0);
    private static final Color BUTTON = new Color(96, 96, 96);
    private static final Color BUTTON_LIGHT = new Color(150, 150, 150);
    private static final Color BUTTON_DARK = new Color(48, 48, 48);
    private static final Color BUTTON_PRESSED = new Color(170, 170, 170);
    private static final Color ARROW = new Color(230, 230, 230);
    private static final Color ARROW_PRESSED = new Color(30, 30, 30);

    private Action pressed;

    public Action hitTest(int x, int y) {
        for (Action a : Action.values()) {
            if (bounds(a).contains(x, y)) {
                return a;
            }
        }
        return null;
    }

    public void setPressed(Action action) {
        pressed = action;
    }

    public void draw(Graphics2D g) {
        g.setColor(PANEL_BG);
        g.fill(AREA);
        for (Action a : Action.values()) {
            drawButton(g, a);
        }
    }

    private static Rectangle bounds(Action a) {
        int col = a.ordinal() % 3;
        int row = a.ordinal() / 3;
        return new Rectangle(
                AREA.x + col * (BUTTON_W + GAP),
                AREA.y + row * (BUTTON_H + GAP),
                BUTTON_W, BUTTON_H);
    }

    private void drawButton(Graphics2D g, Action a) {
        Rectangle r = bounds(a);
        boolean down = a == pressed;
        g.setColor(down ? BUTTON_PRESSED : BUTTON);
        g.fill(r);
        // Bevel: light top-left, dark bottom-right (inverted while pressed).
        g.setColor(down ? BUTTON_DARK : BUTTON_LIGHT);
        g.drawLine(r.x, r.y, r.x + r.width - 1, r.y);
        g.drawLine(r.x, r.y, r.x, r.y + r.height - 1);
        g.setColor(down ? BUTTON_LIGHT : BUTTON_DARK);
        g.drawLine(r.x, r.y + r.height - 1, r.x + r.width - 1, r.y + r.height - 1);
        g.drawLine(r.x + r.width - 1, r.y, r.x + r.width - 1, r.y + r.height - 1);

        g.setColor(down ? ARROW_PRESSED : ARROW);
        int cx = r.x + r.width / 2;
        int cy = r.y + r.height / 2;
        switch (a) {
            case FORWARD -> straightArrow(g, cx, cy, 0, -1);
            case BACKWARD -> straightArrow(g, cx, cy, 0, 1);
            case STRAFE_LEFT -> straightArrow(g, cx, cy, -1, 0);
            case STRAFE_RIGHT -> straightArrow(g, cx, cy, 1, 0);
            case TURN_LEFT -> turnArrow(g, cx, cy, true);
            case TURN_RIGHT -> turnArrow(g, cx, cy, false);
        }
    }

    /** Arrow with a 3px shaft and a triangular head pointing along (dx, dy). */
    private static void straightArrow(Graphics2D g, int cx, int cy, int dx, int dy) {
        int len = 6;
        int head = 5;
        // Perpendicular unit vector.
        int px = -dy;
        int py = dx;
        Polygon p = new Polygon();
        p.addPoint(cx - dx * len + px, cy - dy * len + py);
        p.addPoint(cx + px, cy + py);
        p.addPoint(cx + px * head, cy + py * head);
        p.addPoint(cx + dx * (len + 1), cy + dy * (len + 1));
        p.addPoint(cx - px * head, cy - py * head);
        p.addPoint(cx - px, cy - py);
        p.addPoint(cx - dx * len - px, cy - dy * len - py);
        g.fillPolygon(p);
    }

    /** Curved arrow sweeping over the top and pointing down on the turning side. */
    private static void turnArrow(Graphics2D g, int cx, int cy, boolean left) {
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
        g.draw(new Arc2D.Double(cx - 6, cy - 5, 12, 12, 0, 180, Arc2D.OPEN));
        g.setStroke(old);
        int tipX = left ? cx - 6 : cx + 6;
        Polygon head = new Polygon();
        head.addPoint(tipX - 4, cy + 1);
        head.addPoint(tipX + 4, cy + 1);
        head.addPoint(tipX, cy + 6);
        g.fillPolygon(head);
    }
}
