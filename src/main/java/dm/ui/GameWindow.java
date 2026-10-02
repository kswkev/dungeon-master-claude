package dm.ui;

import dm.model.Party;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;
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
 * Main window. The {@link GameScreen} draws into a 320x200 back buffer, the
 * original screen resolution, which is scaled up with nearest-neighbour
 * filtering. Mouse positions are mapped back to 320x200 coordinates.
 */
public final class GameWindow extends JFrame {

    private static final int DEFAULT_SCALE = 3;
    private static final int BUMP_FLASH_MS = 150;

    public GameWindow(Party party, Art art, SoundPlayer sounds, boolean debug) {
        super("Dungeon Master - Level " + (party.map().level() + 1));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        add(new Screen(new GameScreen(party, art, sounds, debug)));
        pack();
        setLocationRelativeTo(null);
    }

    private static final class Screen extends JPanel {
        private final GameScreen game;
        private final BufferedImage buffer =
                new BufferedImage(GameScreen.WIDTH, GameScreen.HEIGHT, BufferedImage.TYPE_INT_RGB);

        Screen(GameScreen game) {
            this.game = game;
            setPreferredSize(new Dimension(GameScreen.WIDTH * DEFAULT_SCALE, GameScreen.HEIGHT * DEFAULT_SCALE));
            setBackground(Color.BLACK);

            Timer bumpTimer = new Timer(BUMP_FLASH_MS, e -> {
                game.clearBump();
                repaint();
            });
            bumpTimer.setRepeats(false);
            // Repaint once more when the damage burst on the champion boxes expires.
            Timer damageTimer = new Timer(GameScreen.DAMAGE_SHOWN_MS + 20, e -> repaint());
            damageTimer.setRepeats(false);
            game.setOnBump(() -> {
                bumpTimer.restart();
                damageTimer.restart();
            });

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1) {
                        int[] p = toScreen(e.getX(), e.getY());
                        game.press(p[0], p[1]);
                        repaint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    game.release();
                    repaint();
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    if (game.sheet().isOpen()) {
                        int[] p = toScreen(e.getX(), e.getY());
                        game.hover(p[0], p[1]);
                        repaint();
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        /** Largest rectangle with the 320x200 aspect ratio that fits the panel, centred. */
        private Rectangle target() {
            double scale = Math.min(getWidth() / (double) GameScreen.WIDTH, getHeight() / (double) GameScreen.HEIGHT);
            int w = (int) (GameScreen.WIDTH * scale);
            int h = (int) (GameScreen.HEIGHT * scale);
            return new Rectangle((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
        }

        private int[] toScreen(int px, int py) {
            Rectangle t = target();
            return new int[] {
                    (int) ((px - t.x) * (double) GameScreen.WIDTH / t.width),
                    (int) ((py - t.y) * (double) GameScreen.HEIGHT / t.height)
            };
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D bg = buffer.createGraphics();
            try {
                game.render(bg);
            } finally {
                bg.dispose();
            }
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            Rectangle t = target();
            g2.drawImage(buffer, t.x, t.y, t.width, t.height, null);
        }
    }
}
