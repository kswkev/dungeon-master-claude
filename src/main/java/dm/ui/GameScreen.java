package dm.ui;

import dm.data.GraphicsFile;
import dm.data.Sound;
import dm.model.Champion;
import dm.model.ChampionMirror;
import dm.model.DungeonMap;
import dm.model.Item;
import dm.model.Party;
import dm.model.Slot;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.function.LongSupplier;

/**
 * The 320x200 game screen: what to draw and how clicks are routed, with no
 * Swing dependencies so it can be driven headlessly.
 *
 * Click order: a hand in a champion's status box first, then an open
 * character sheet, then the champion boxes, then the formation box, then in
 * the dungeon view a mirror portrait, a door button, the wall decoration
 * straight ahead, and the rest of the view (floor: pick up or drop; above
 * it: throw); then the movement arrows. While a sheet is open the party
 * can't move or reach the dungeon, as in DM.
 */
public final class GameScreen {

    public static final int WIDTH = 320;
    public static final int HEIGHT = 200;
    /** How long the damage burst stays on a champion's box after a bump. */
    public static final int DAMAGE_SHOWN_MS = 900;
    /** Length of a game tick: doors move one of their 4 steps per tick, roughly as fast as in DM. */
    public static final int TICK_MS = 170;
    /** Viewport rows from here down are the floor of the party's own square (pick up and drop); above is for throwing. */
    static final int FLOOR_CLICK_Y = 100;

    private final Party party;
    private final Art art;
    private final boolean debug;
    private final ViewRenderer view;
    private final MovementPanel arrows = new MovementPanel();
    private final ChampionBars bars;
    private final CharacterSheet sheet;
    private final FormationBox formation;
    private final SoundPlayer sounds;
    private final Sound bumpSound;
    private final Sound doorSound;
    private final Sound clickSound;
    private final Sound screamSound;
    private final Sound swallowSound;
    private LongSupplier clock = System::currentTimeMillis;
    private Runnable onBump = () -> { };
    private Runnable onDamage = () -> { };
    private boolean bumped;
    /** Last mouse position in screen coordinates, or null when the mouse is outside the window. */
    private Point pointer;

    public GameScreen(Party party, Art art, boolean debug) {
        this(party, art, SoundPlayer.silent(), debug);
    }

    public GameScreen(Party party, Art art, SoundPlayer sounds, boolean debug) {
        this.party = party;
        this.art = art;
        this.debug = debug;
        this.view = ViewRenderer.forArt(art);
        this.bars = new ChampionBars(art);
        this.sheet = new CharacterSheet(art);
        this.formation = new FormationBox(art);
        this.sounds = sounds;
        this.bumpSound = art.sound(GraphicsFile.SOUND_BUMP);
        this.doorSound = art.sound(GraphicsFile.SOUND_DOOR);
        this.clickSound = art.sound(GraphicsFile.SOUND_CLICK);
        this.screamSound = art.sound(GraphicsFile.SOUND_SCREAM);
        this.swallowSound = art.sound(GraphicsFile.SOUND_SWALLOW);
    }

    public FormationBox formation() {
        return formation;
    }

    /**
     * Advances the game clock by one tick (the window calls this about every
     * {@link #TICK_MS} ms). Returns true if anything visible changed.
     */
    public boolean tick() {
        DungeonMap.DoorTick doors = party.map().tickDoors();
        if (doors.rattled()) {
            sounds.play(doorSound);
        }
        DungeonMap.ProjectileTick flew = party.map().tickProjectiles();
        if (flew.click()) {
            sounds.play(clickSound);
        }
        boolean moved = arrived(party.settle()); // a landing item may have opened a pit under the party
        Party.Tick upkeep = party.tick();
        if (upkeep.damage() != null) {
            showDamage(upkeep.damage());
        }
        return doors.moved() || flew.moved() || moved || upkeep.changed();
    }

    /**
     * Plays and shows what moving the party (or the floor under it) set off:
     * a sensor click, and fall damage bursts. Returns true if the party fell
     * or was teleported.
     */
    private boolean arrived(DungeonMap.StepResult result) {
        if (result.fell()) {
            sounds.play(screamSound); // first: it starts as the party drops
        }
        if (result.click()) {
            sounds.play(clickSound);
        }
        if (result.damage() != null) {
            showDamage(result.damage());
        }
        if (debug && result.fell()) {
            System.out.printf("Fell to Level %d: (%d,%d)%n", party.level() + 1, party.x(), party.y());
        }
        if (debug && result.teleported()) {
            System.out.printf("Teleported to Level %d: (%d,%d) facing %s%n",
                    party.level() + 1, party.x(), party.y(), party.facing());
        }
        return result.fell() || result.teleported();
    }

    /**
     * Called when a move is blocked. The caller clears the red flash with
     * {@link #clearBump} and repaints once the damage burst has expired
     * ({@link #DAMAGE_SHOWN_MS}).
     */
    public void setOnBump(Runnable onBump) {
        this.onBump = onBump;
    }

    /**
     * Called whenever champions are hurt (a bump or a fall); the caller
     * repaints once the damage burst has expired ({@link #DAMAGE_SHOWN_MS}).
     */
    public void setOnDamage(Runnable onDamage) {
        this.onDamage = onDamage;
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

    /** The dungeon view renderer, for tests that click what it drew. */
    ViewRenderer view() {
        return view;
    }

    /** Handles a left-button press at screen point (x, y). */
    public void press(int x, int y) {
        pointer = new Point(x, y);
        if (clickHand(x, y)) {
            return;
        }
        if (sheet.isOpen()) {
            pressWithSheetOpen(x, y);
            return;
        }
        int box = bars.hitTest(x, y);
        if (box >= 0 && box < party.members().size()) {
            sheet.openMember(party.members().get(box));
            return;
        }
        if (FormationBox.AREA.contains(x, y)) {
            if (formation.click(party, x, y) && debug) {
                System.out.println("Formation: FL " + name(Party.FRONT_LEFT) + ", FR " + name(Party.FRONT_RIGHT)
                        + ", BL " + name(Party.BACK_LEFT) + ", BR " + name(Party.BACK_RIGHT));
            }
            return;
        }
        Rectangle portrait = view.portraitHit();
        ChampionMirror mirror = party.facingMirror();
        if (portrait != null && mirror != null && portrait.contains(x, y)) {
            sheet.openCandidate(mirror, party.isFull());
            return;
        }
        Rectangle button = view.doorButtonHit();
        if (button != null && button.contains(x, y)) {
            pressDoorButton();
            return;
        }
        Rectangle wall = view.wallHit();
        if (wall != null && wall.contains(x, y)) {
            clickWall();
            return;
        }
        if (ViewRenderer.VIEWPORT.contains(x, y)) {
            clickView(x - ViewRenderer.VIEWPORT.x, y - ViewRenderer.VIEWPORT.y);
            return;
        }
        MovementPanel.Action action = arrows.hitTest(x, y);
        if (action != null) {
            arrows.setPressed(action);
            move(action);
        }
    }

    private void pressWithSheetOpen(int x, int y) {
        if (ViewRenderer.VIEWPORT.contains(x, y)) {
            switch (sheet.click(x, y)) {
                case SLOT -> clickSlot(sheet.champion(), sheet.slotAt(x, y));
                case RESURRECT -> {
                    ChampionMirror mirror = sheet.candidate();
                    if (party.recruit(mirror) && debug) {
                        System.out.println("Resurrected " + mirror.champion().fullName());
                    }
                    sheet.close();
                }
                case CLOSE -> sheet.close();
                case MOUTH -> feed(sheet.champion());
                case EYE -> sheet.setPressingEye(true);
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

    /**
     * A click on a hand in a champion's status box works like that hand's
     * cell on the sheet (issue #12). Hands aren't shown, so can't be clicked,
     * for the champion whose sheet is open or for a mirror candidate.
     */
    private boolean clickHand(int x, int y) {
        ChampionBars.Hand hand = bars.handAt(x, y);
        if (hand == null || hand.box() >= party.members().size()) {
            return false;
        }
        Champion champion = party.members().get(hand.box());
        if (champion == sheet.champion()) {
            return false;
        }
        clickSlot(champion, hand.slot());
        return true;
    }

    /** Clicking the mouth with food, a waterskin or a potion in hand: the champion eats or drinks it. */
    private void feed(Champion champion) {
        Item item = party.held();
        if (!party.feed(champion)) {
            return;
        }
        sounds.play(swallowSound);
        if (debug) {
            System.out.printf("%s consumed %s: food %d, water %d%n",
                    champion.name(), item.name(), champion.food(), champion.water());
        }
    }

    /** The square straight ahead of the party. */
    private int aheadX() {
        return party.x() + party.facing().dx;
    }

    private int aheadY() {
        return party.y() + party.facing().dy;
    }

    /** The button on the door straight ahead toggles it; the door sound comes from {@link #tick}. */
    private void pressDoorButton() {
        boolean started = party.map().pressDoorButton(aheadX(), aheadY());
        sounds.play(clickSound);
        if (debug) {
            System.out.println("Door button at (" + aheadX() + "," + aheadY() + ")" + (started ? ": door moving" : ""));
        }
    }

    /** Uses the decoration on the wall straight ahead: switches, keyholes, torch holders, alcoves. */
    private void clickWall() {
        Item before = party.held();
        DungeonMap.WallClick result = party.map().clickWall(aheadX(), aheadY(), party.facing().opposite(),
                party, art::iconIndex);
        if (result.sound()) {
            sounds.play(clickSound);
        }
        if (result.drank()) {
            sounds.play(swallowSound);
        }
        if (debug) {
            System.out.println("Clicked wall (" + aheadX() + "," + aheadY() + ") " + party.facing().opposite()
                    + ": " + party.map().wallSensors(aheadX(), aheadY(), party.facing().opposite())
                    + (result.fired() ? " fired" : "") + (result.doorStarted() ? ", door moving" : "")
                    + (result.drank() ? ", the party drank" : "")
                    + (result.handChanged() ? ", hand " + name(before) + " -> " + name(party.held()) : ""));
        }
        arrived(party.settle()); // a lever may have opened a pit under the party
    }

    private static String name(Item item) {
        return item == null ? "empty" : item.name();
    }

    /**
     * DM's inventory click: with an empty hand, pick up what's in the cell;
     * holding an item, put it down there if it fits, picking up whatever was
     * in the cell (a swap). An item that doesn't fit stays in hand.
     */
    private void clickSlot(Champion champion, Slot slot) {
        Item held = party.held();
        if (held == null) {
            Item taken = champion.take(slot);
            party.setHeld(taken);
            if (taken != null && debug) {
                System.out.println("Picked up " + taken.name() + " from " + champion.name() + "'s " + slot);
            }
        } else if (held.fits(slot)) {
            party.setHeld(champion.place(slot, held));
            if (debug) {
                System.out.println("Placed " + held.name() + " in " + champion.name() + "'s " + slot);
            }
        }
    }

    /**
     * A click in the dungeon view, in viewport coordinates. As in DM, the
     * bottom of the view is the floor of the party's own square: its left and
     * right halves are the two cells ahead of the party, where a click picks
     * up the top item or drops the held one. Above that, a click with an item
     * in hand throws it from that side.
     */
    private void clickView(int vx, int vy) {
        boolean right = vx >= ViewRenderer.VIEWPORT.width / 2;
        DungeonMap map = party.map();
        Item held = party.held();
        if (vy >= FLOOR_CLICK_Y) {
            int cell = party.facing().cellOf(right ? 1 : 0);
            if (held == null) {
                DungeonMap.Pickup pickup = map.pickUpItem(party.x(), party.y(), cell);
                party.setHeld(pickup.item());
                arrived(pickup.result());
                if (pickup.item() != null && debug) {
                    System.out.println("Picked up " + pickup.item().name() + " from the floor");
                }
            } else {
                party.setHeld(null);
                arrived(map.dropItem(party.x(), party.y(), cell, held));
                if (debug) {
                    System.out.println("Dropped " + held.name());
                }
            }
        } else if (held != null) {
            map.throwItem(held, party.x(), party.y(), party.facing(), right, Party.THROW_RANGE);
            party.setHeld(null);
            if (debug) {
                System.out.println("Threw " + held.name() + (right ? " from the right" : " from the left"));
            }
        }
    }

    /** True while an item rides on the mouse pointer, which the window then hides. */
    public boolean holding() {
        return party.held() != null;
    }

    public void release() {
        arrows.setPressed(null);
        sheet.setPressingEye(false);
    }

    /** The mouse moved to screen point (x, y). */
    public void hover(int x, int y) {
        pointer = new Point(x, y);
        sheet.hover(x, y);
    }

    /** The mouse left the window: nothing is drawn at the pointer. */
    public void pointerGone() {
        pointer = null;
        sheet.hover(-1, -1);
    }

    private String name(int position) {
        return party.at(position) == null ? "-" : party.at(position).name();
    }

    private void move(MovementPanel.Action action) {
        Party.Move step = switch (action) {
            case TURN_LEFT -> {
                party.turnLeft();
                yield null;
            }
            case TURN_RIGHT -> {
                party.turnRight();
                yield null;
            }
            case FORWARD -> Party.Move.FORWARD;
            case BACKWARD -> Party.Move.BACKWARD;
            case STRAFE_LEFT -> Party.Move.LEFT;
            case STRAFE_RIGHT -> Party.Move.RIGHT;
        };
        boolean moved = true;
        if (step != null) {
            DungeonMap.StepResult result = party.step(step);
            moved = result != null;
            if (!moved) {
                bump(step);
            } else {
                arrived(result);
                if (result.levelChanged() && !result.fell() && !result.teleported() && debug) {
                    System.out.printf("Took the stairs to Level %d: (%d,%d) facing %s%n",
                            party.level() + 1, party.x(), party.y(), party.facing());
                }
                // The door sound comes from tick(), once per door step.
            }
        }
        if (debug) {
            System.out.printf("%s -> (%d,%d) facing %s%s%n", action, party.x(), party.y(), party.facing(),
                    moved ? "" : " [blocked]");
        }
    }

    /** DM's wall bump: the thud, damage to the side that hit the wall with a burst on their boxes, plus the red flash. */
    private void bump(Party.Move step) {
        sounds.play(bumpSound);
        showDamage(party.bump(step));
        bumped = true;
        onBump.run();
    }

    /** DM's damage burst on each hurt champion's box; the caller's onDamage repaints once it has expired. */
    private void showDamage(int[] damage) {
        onDamage.run();
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
    }

    public void render(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        drawPlaceholders(g);
        ChampionMirror viewed = sheet.candidate();
        bars.draw(g, party.members(), sheet.champion(), viewed == null ? null : viewed.champion(),
                clock.getAsLong());
        formation.draw(g, party);
        if (sheet.isOpen()) {
            sheet.draw(g, holding());
        } else {
            view.draw(g, party);
        }
        if (bumped) {
            g.setColor(new Color(200, 0, 0));
            g.setStroke(new BasicStroke(2));
            Rectangle v = ViewRenderer.VIEWPORT;
            g.drawRect(v.x + 1, v.y + 1, v.width - 2, v.height - 2);
        }
        arrows.draw(g);
        if (debug) {
            PixelFont.draw(g, party.x() + "," + party.y() + " " + party.facing(), 236, 190, Color.YELLOW);
        }
        drawHeldItem(g);
    }

    /** The held item replaces the mouse pointer: its icon centred on the pointer, over everything else. */
    private void drawHeldItem(Graphics2D g) {
        Item held = party.held();
        if (held == null || pointer == null) {
            return;
        }
        int x = pointer.x - 8;
        int y = pointer.y - 8;
        BufferedImage icon = art.iconSprite(held);
        if (icon != null) {
            g.drawImage(icon, x, y, null);
        } else {
            Placeholders.icon(g, held, x, y);
        }
    }

    /** Outlines the screen regions later sprints will fill: spells and actions. */
    private static void drawPlaceholders(Graphics2D g) {
        g.setColor(new Color(40, 40, 40));
        g.drawRect(233, 42, 86, 34);  // spell casting area
        g.drawRect(233, 77, 86, 45);  // action area
    }
}
