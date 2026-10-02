package dm.ui;

import dm.data.GraphicsFile;
import dm.data.Sound;
import dm.model.ChampionMirror;
import dm.model.Party;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.function.LongSupplier;

/**
 * The 320x200 game screen: what to draw and how clicks are routed, with no
 * Swing dependencies so it can be driven headlessly.
 *
 * Click order: an open character sheet first, then the champion bars, then
 * a mirror portrait in the dungeon view, then the movement arrows. While a
 * sheet is open the party can't move, as in DM.
 */
public final class GameScreen {

    public static final int WIDTH = 320;
    public static final int HEIGHT = 200;
    /** How long the damage burst stays on a champion's box after a bump. */
    public static final int DAMAGE_SHOWN_MS = 900;

    private final Party party;
    private final boolean debug;
    private final DungeonViewRenderer view;
    private final MovementPanel arrows = new MovementPanel();
    private final ChampionBars bars;
    private final CharacterSheet sheet;
    private final SoundPlayer sounds;
    private final Sound bumpSound;
    private LongSupplier clock = System::currentTimeMillis;
    private Runnable onBump = () -> { };
    private boolean bumped;

    public GameScreen(Party party, Art art, boolean debug) {
        this(party, art, SoundPlayer.silent(), debug);
    }

    public GameScreen(Party party, Art art, SoundPlayer sounds, boolean debug) {
        this.party = party;
        this.debug = debug;
        this.view = new DungeonViewRenderer(art);
        this.bars = new ChampionBars(art);
        this.sheet = new CharacterSheet(art);
        this.sounds = sounds;
        this.bumpSound = art.sound(GraphicsFile.SOUND_BUMP);
    }

    /**
     * Called when a move is blocked. The caller clears the red flash with
     * {@link #clearBump} and repaints once the damage burst has expired
     * ({@link #DAMAGE_SHOWN_MS}).
     */
    public void setOnBump(Runnable onBump) {
        this.onBump = onBump;
    }

    /** Replaces the millisecond clock, for tests. */
    public void setClock(LongSupplier clock) {
        this.clock = clock;
    }

    public ChampionBars bars() {
        return bars;
    }

    public void clearBump() {
        bumped = false;
    }

    public CharacterSheet sheet() {
        return sheet;
    }

    /** Handles a left-button press at screen point (x, y). */
    public void press(int x, int y) {
        if (sheet.isOpen()) {
            pressWithSheetOpen(x, y);
            return;
        }
        int box = bars.hitTest(x, y);
        if (box >= 0 && box < party.members().size()) {
            sheet.openMember(party.members().get(box));
            return;
        }
        Rectangle portrait = view.portraitHit();
        ChampionMirror mirror = party.facingMirror();
        if (portrait != null && mirror != null && portrait.contains(x, y)) {
            sheet.openCandidate(mirror, party.isFull());
            return;
        }
        MovementPanel.Action action = arrows.hitTest(x, y);
        if (action != null) {
            arrows.setPressed(action);
            move(action);
        }
    }

    private void pressWithSheetOpen(int x, int y) {
        if (DungeonViewRenderer.VIEWPORT.contains(x, y)) {
            switch (sheet.click(x, y)) {
                case RESURRECT -> {
                    ChampionMirror mirror = sheet.candidate();
                    if (party.recruit(mirror) && debug) {
                        System.out.println("Resurrected " + mirror.champion().fullName());
                    }
                    sheet.close();
                }
                case CLOSE -> sheet.close();
                case NONE -> { }
            }
            return;
        }
        // Clicking party boxes switches between members (DM toggles off the one shown).
        int box = bars.hitTest(x, y);
        if (sheet.candidate() == null && box >= 0 && box < party.members().size()) {
            if (party.members().get(box) == sheet.champion()) {
                sheet.close();
            } else {
                sheet.openMember(party.members().get(box));
            }
        }
    }

    public void release() {
        arrows.setPressed(null);
    }

    public void hover(int x, int y) {
        sheet.hover(x, y);
    }

    private void move(MovementPanel.Action action) {
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
            bump();
        }
        if (debug) {
            System.out.printf("%s -> (%d,%d) facing %s%s%n", action, party.x(), party.y(), party.facing(),
                    moved ? "" : " [blocked]");
        }
    }

    /** DM's wall bump: the thud, damage to the front row with a burst on their boxes, plus the red flash. */
    private void bump() {
        sounds.play(bumpSound);
        int[] damage = party.bump();
        long until = clock.getAsLong() + DAMAGE_SHOWN_MS;
        for (int i = 0; i < damage.length; i++) {
            if (damage[i] > 0) {
                bars.showDamage(i, damage[i], until);
                if (debug) {
                    System.out.printf("%s takes %d damage (health %d)%n",
                            party.members().get(i).name(), damage[i], party.members().get(i).health());
                }
            }
        }
        bumped = true;
        onBump.run();
    }

    public void render(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        drawPlaceholders(g);
        ChampionMirror viewed = sheet.candidate();
        bars.draw(g, party.members(), sheet.champion(), viewed == null ? null : viewed.champion(),
                clock.getAsLong());
        if (sheet.isOpen()) {
            sheet.draw(g);
        } else {
            view.draw(g, party);
        }
        if (bumped) {
            g.setColor(new Color(200, 0, 0));
            g.setStroke(new BasicStroke(2));
            Rectangle v = DungeonViewRenderer.VIEWPORT;
            g.drawRect(v.x + 1, v.y + 1, v.width - 2, v.height - 2);
        }
        arrows.draw(g);
        if (debug) {
            PixelFont.draw(g, party.x() + "," + party.y() + " " + party.facing(), 236, 190, Color.YELLOW);
        }
    }

    /** Outlines the screen regions later sprints will fill: spells and actions. */
    private static void drawPlaceholders(Graphics2D g) {
        g.setColor(new Color(40, 40, 40));
        g.drawRect(233, 42, 86, 34);  // spell casting area
        g.drawRect(233, 77, 86, 45);  // action area
    }
}
