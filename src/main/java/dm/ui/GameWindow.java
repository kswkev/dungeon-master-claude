package dm.ui;

import dm.model.Party;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * Main window. Everything is drawn to a 320x200 back buffer, the original
 * screen resolution, and scaled up with nearest-neighbour filtering.
 */
public final class GameWindow extends JFrame {

    public static final int SCREEN_W = 320;
    public static final int SCREEN_H = 200;
    private static final int DEFAULT_SCALE = 3;
    private static final int BUMP_FLASH_MS = 150;

    public GameWindow(Party party, boolean debug) {
        super("Dungeon Master - Level " + (party.map().level() + 1));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        add(new Screen(party, debug));
        pack();
        setLocationRelativeTo(null);
    }

    private static final class Screen extends JPanel {
        private final Party party;
        private final boolean debug;
        private final DungeonViewRenderer view = new DungeonViewRenderer();
        private final MovementPanel arrows = new MovementPanel();
        private final BufferedImage buffer = new BufferedImage(SCREEN_W, SCREEN_H, BufferedImage.TYPE_INT_RGB);
        private final Timer bumpTimer;
        private boolean bumped;

        Screen(Party party, boolean debug) {
            this.party = party;
            this.debug = debug;
            setPreferredSize(new Dimension(SCREEN_W * DEFAULT_SCALE, SCREEN_H * DEFAULT_SCALE));
            setBackground(Color.BLACK);

            bumpTimer = new Timer(BUMP_FLASH_MS, e -> {
                bumped = false;
                repaint();
            });
            bumpTimer.setRepeats(false);

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.getButton() != MouseEvent.BUTTON1) {
                        return;
                    }
                    int[] p = toScreen(e.getX(), e.getY());
                    MovementPanel.Action action = arrows.hitTest(p[0], p[1]);
                    if (action != null) {
                        arrows.setPressed(action);
                        perform(action);
                        repaint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    arrows.setPressed(null);
                    repaint();
                }
            };
            addMouseListener(mouse);
        }

        private void perform(MovementPanel.Action action) {
            boolean moved = switch (action) {
                case TURN_LEFT -> {
                    party.turnLeft();
                    yield true;
                }
                case TURN_RIGHT -> {
                    party.turnRight();
                    yield true;
                }
                case FORWARD -> party.move(Party.Move.FORWARD);
                case BACKWARD -> party.move(Party.Move.BACKWARD);
                case STRAFE_LEFT -> party.move(Party.Move.LEFT);
                case STRAFE_RIGHT -> party.move(Party.Move.RIGHT);
            };
            if (!moved) {
                bumped = true;
                bumpTimer.restart();
            }
            if (debug) {
                System.out.printf("%s -> (%d,%d) facing %s%s%n", action, party.x(), party.y(), party.facing(),
                        moved ? "" : " [blocked]");
            }
        }

        /** Largest rectangle with the 320x200 aspect ratio that fits the panel, centred. */
        private Rectangle target() {
            double scale = Math.min(getWidth() / (double) SCREEN_W, getHeight() / (double) SCREEN_H);
            int w = (int) (SCREEN_W * scale);
            int h = (int) (SCREEN_H * scale);
            return new Rectangle((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
        }

        private int[] toScreen(int px, int py) {
            Rectangle t = target();
            return new int[] {
                    (int) ((px - t.x) * (double) SCREEN_W / t.width),
                    (int) ((py - t.y) * (double) SCREEN_H / t.height)
            };
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            renderScreen();
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            Rectangle t = target();
            g2.drawImage(buffer, t.x, t.y, t.width, t.height, null);
        }

        private void renderScreen() {
            Graphics2D g = buffer.createGraphics();
            try {
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, SCREEN_W, SCREEN_H);
                drawPlaceholders(g);
                view.draw(g, party);
                if (bumped) {
                    g.setColor(new Color(200, 0, 0));
                    g.setStroke(new BasicStroke(2));
                    Rectangle v = DungeonViewRenderer.VIEWPORT;
                    g.drawRect(v.x + 1, v.y + 1, v.width - 2, v.height - 2);
                }
                arrows.draw(g);
                if (debug) {
                    g.setColor(Color.YELLOW);
                    g.setFont(g.getFont().deriveFont(8f));
                    g.drawString(party.x() + "," + party.y() + " " + party.facing(), 236, 190);
                }
            } finally {
                g.dispose();
            }
        }

        /** Outlines the screen regions later sprints will fill: champions, spells, actions. */
        private static void drawPlaceholders(Graphics2D g) {
            g.setColor(new Color(40, 40, 40));
            for (int i = 0; i < 4; i++) {
                g.drawRect(i * 69, 0, 66, 28);
            }
            g.drawRect(233, 42, 86, 34);  // spell casting area
            g.drawRect(233, 77, 86, 45);  // action area
        }
    }
}
