package dm.ui;

import dm.data.GraphicsFile;
import dm.data.SaveGames;
import dm.data.Sound;
import dm.model.Actions;
import dm.model.Champion;
import dm.model.ChampionMirror;
import dm.model.Difficulty;
import dm.model.Direction;
import dm.model.DungeonMap;
import dm.model.Group;
import dm.model.Item;
import dm.model.Party;
import dm.model.Slot;
import dm.model.Sounds;
import dm.model.Spells;
import dm.model.Square;
import dm.model.SquareType;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    /**
     * DM's G0462 drop boxes (screen coordinates), by view cell: the party
     * square's far left and right cells along the bottom of the view, and
     * the near right and left cells of the square ahead above them.
     */
    static final Rectangle[] PILE_BOXES = {
            new Rectangle(24, 148, 88, 21), new Rectangle(112, 148, 88, 21),
            new Rectangle(112, 122, 72, 26), new Rectangle(40, 122, 72, 26)};

    /** The game being played; loading a saved game replaces it ({@link #restore}). */
    private Party party;
    private final Art art;
    private final boolean debug;
    private final ViewRenderer view;
    private final MovementPanel arrows;
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
    private final GameMenu menu;
    private SaveGames saves = SaveGames.standard();
    private Runnable onQuit = () -> { };

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
        this.menu = new GameMenu(art);
        this.arrows = new MovementPanel(art);
        this.actions = new ActionArea(art);
        this.spells = new SpellArea(art);
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

    /** The game being played (a loaded game replaces the one the screen started with). */
    public Party party() {
        return party;
    }

    public GameMenu menu() {
        return menu;
    }

    /** Where games are saved; {@link SaveGames#standard()} unless replaced (tests use a temporary folder). */
    public void setSaveGames(SaveGames saves) {
        this.saves = saves;
    }

    /** Called when the player quits from the game menu; the window closes the game. */
    public void setOnQuit(Runnable onQuit) {
        this.onQuit = onQuit;
    }

    /**
     * Advances the game clock by one tick (the window calls this about every
     * {@link #TICK_MS} ms), or {@link #SLEEP_TICKS} while the party sleeps.
     * Returns true if anything visible changed. Nothing moves while the game
     * menu is open: as in DM, a dialog pauses the game.
     */
    public boolean tick() {
        if (menu.isOpen() || automap.isOpen()) {
            return false; // the map pauses the game too (not in DM)
        }
        if (party.endgame() != null) {
            return endgameTick();
        }
        boolean changed = step();
        for (int i = 1; i < SLEEP_TICKS && party.sleeping() && !gameOver; i++) {
            changed |= step();
        }
        return changed;
    }

    /**
     * Game ticks per window tick while asleep. DM stops waiting for input
     * while the party sleeps (G318 = 0), so time runs as fast as the machine
     * allows; this is an estimate of that speed-up.
     */
    static final int SLEEP_TICKS = 5;

    private boolean step() {
        DungeonMap.DoorTick doors = party.map().tickDoors();
        if (doors.rattled()) {
            sounds.play(doorSound, doors.rattle().soft());
        }
        if (doors.thud()) {
            sounds.play(dmSound(WOODEN_THUD), doors.thudSound().soft());
        }
        Party.Tick upkeep = party.tick();
        boolean moved = arrived(party.settle()); // a landing item may have opened a pit under the party
        if (actions.acting() >= 0 && party.members().get(actions.acting()).health() == 0) {
            actions.close();
        }
        boolean acted = actions.tick();
        boolean printed = takeMessages();
        if (messages.hasText()) {
            messages.clearExpired(party.time());
            printed = true;
        }
        for (Sounds.Heard heard : upkeep.sounds()) {
            sounds.play(dmSound(heard.dmSound()), heard.soft());
        }
        if (upkeep.click()) {
            sounds.play(clickSound);
        }
        if (upkeep.damage() != null) {
            showDamage(upkeep.damage());
        }
        // A teleporter's field shimmers: DM redraws the view every tick.
        boolean shimmer = view.animated() && !sheet.isOpen() && !party.sleeping();
        return doors.moved() || moved || upkeep.changed() || printed || acted || shimmer;
    }

    /** DM's action area, under the spell area. */
    private final ActionArea actions;

    ActionArea actionArea() {
        return actions;
    }

    /** A click in the action area: open a champion's menu, pass, or perform the chosen action. */
    private void clickActionArea(int x, int y) {
        ActionArea.Click click = actions.click(party, x, y, sheet.candidate() != null);
        if (click.member() < 0) {
            return;
        }
        Champion c = party.members().get(click.member());
        int result = party.act(click.member(), click.action());
        actions.performed(result);
        if (debug) {
            System.out.println(c.name() + ": " + Actions.name(click.action())
                    + (result > 0 ? ", " + result + " damage" : result == Party.CANT_REACH ? ", can't reach"
                    : result == Party.NEED_AMMO ? ", needs ammunition" : ""));
        }
        arrived(party.settle()); // climbing down a pit
    }

    /** DM's message area along the bottom of the screen. */
    private final MessageArea messages = new MessageArea();

    /** The map of what the party has seen (not in DM). */
    private final AutoMap automap = new AutoMap();

    AutoMap automap() {
        return automap;
    }

    /** Moves the party's new messages (level gains and the like) into the message area. Returns whether there were any. */
    private boolean takeMessages() {
        List<Party.Message> taken = party.takeMessages();
        for (Party.Message m : taken) {
            if (m.member() == Party.MESSAGE_CLEAR) {
                messages.clearAll(); // DM's F043, before each closing message
                continue;
            }
            Color colour = m.member() >= 0 && m.member() < ChampionBars.COLORS.length
                    ? ChampionBars.COLORS[m.member()] : m.member() == Party.MESSAGE_WHITE ? Color.WHITE : MESSAGE_CYAN;
            for (String line : m.text().split("\n")) { // DM's F047 starts a new row at each line break
                messages.print(line, colour, party.time());
            }
        }
        return !taken.isEmpty();
    }

    /** DM's default message colour (palette 4, cyan). */
    private static final Color MESSAGE_CYAN = new Color(Art.PALETTE[4].getRGB());

    /** DM's C04 wooden thud: a door bouncing off a creature. */
    private static final int WOODEN_THUD = 4;

    private final Map<Integer, Sound> dmSounds = new HashMap<>();

    /** DM sound {@code index} (creature attacks and steps, a champion hit...), loaded once. */
    private Sound dmSound(int index) {
        return dmSounds.computeIfAbsent(index, i -> art.sound(GraphicsFile.soundEntry(i)));
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
        if (menu.isOpen()) {
            clickMenu(menu.click(x, y));
            return;
        }
        if (automap.isOpen()) {
            automap.click(party, x, y);
            return;
        }
        if (inputBlocked()) {
            return;
        }
        if (party.sleeping()) {
            // DM's G450: asleep, only a click in the view (down to the message area) does anything.
            if (x < 224 && y >= 33 && y <= 168) {
                party.wakeUp();
            }
            return;
        }
        if (AutoMap.BUTTON.contains(x, y)) {
            party.explore();
            arrows.setPressed(null);
            sheet.setPressingEye(false);
            automap.open(party);
            return;
        }
        if (clickHand(x, y)) {
            return;
        }
        if (ActionArea.AREA.contains(x, y)) { // works with the sheet open, as in DM
            clickActionArea(x, y);
            return;
        }
        if (SpellArea.AREA.contains(x, y)) { // so does the spell area
            clickSpellArea(x, y);
            return;
        }
        if (sheet.isOpen()) {
            pressWithSheetOpen(x, y);
            return;
        }
        int box = bars.hitTest(x, y);
        if (box >= 0 && box < party.members().size()) {
            if (party.members().get(box).health() > 0) { // a dead champion's box does nothing
                sheet.openMember(party.members().get(box));
            }
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
            // DM's F280: only with room in the party and nothing in hand.
            if (!party.isFull() && party.held() == null) {
                sheet.openCandidate(mirror);
            }
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
            clickView(x, y);
            return;
        }
        MovementPanel.Action action = arrows.hitTest(x, y);
        if (action != null) {
            arrows.setPressed(action);
            move(action);
        }
    }

    /**
     * Handles a right-button press at screen point (x, y), from DM's mouse
     * tables (#47): on a status box (G0447, x 69i to 69i + 66, y 0-28) it
     * toggles that champion's sheet; with a sheet open anywhere else closes
     * it (G0449); with none, anywhere from y 33 down opens the leader's
     * (G0448). Asleep it wakes the party like a left click (G0450). Nothing
     * toggles while a mirror candidate is shown, the eye is held, or a
     * champion is dead (F355). The item on the pointer stays in hand.
     */
    public void rightPress(int x, int y) {
        pointer = new Point(x, y);
        if (menu.isOpen() || inputBlocked()) {
            return;
        }
        if (automap.isOpen()) {
            automap.close();
            return;
        }
        if (party.sleeping()) {
            if (x < 224 && y >= 33 && y <= 168) {
                party.wakeUp();
            }
            return;
        }
        if (sheet.candidate() != null || sheet.pressingEye()) {
            return;
        }
        int box = x / 69;
        if (y <= 28 && x % 69 <= 66) {
            if (box < party.members().size()) {
                toggleSheet(party.members().get(box));
            }
            return;
        }
        if (sheet.isOpen()) {
            sheet.close();
        } else if (y >= 33 && party.leader() != null) {
            sheet.openMember(party.leader());
        }
    }

    /** DM's F355: opens a living champion's sheet, or closes it if it is the one shown. */
    private void toggleSheet(Champion champion) {
        if (champion.health() <= 0) {
            return;
        }
        if (sheet.champion() == champion) {
            sheet.close();
        } else {
            sheet.openMember(champion);
        }
    }

    private void pressWithSheetOpen(int x, int y) {
        if (ViewRenderer.VIEWPORT.contains(x, y)) {
            switch (sheet.click(x, y)) {
                case SLOT -> clickSlot(sheet.champion(), sheet.slotAt(x, y));
                case CHEST_CELL -> party.setHeld(sheet.swapChestCell(sheet.chestCellAt(x, y), party.held()));
                case RESURRECT -> {
                    ChampionMirror mirror = sheet.candidate();
                    if (party.recruit(mirror) && debug) {
                        System.out.println("Resurrected " + mirror.champion().fullName());
                    }
                    sheet.close();
                }
                case REINCARNATE -> sheet.startRenaming();
                case RENAMED -> {
                    // DM keeps the panel open while the name is a member's already.
                    ChampionMirror mirror = sheet.candidate();
                    if (party.reincarnate(mirror, sheet.newName(), sheet.newTitle())) {
                        if (debug) {
                            System.out.println("Reincarnated " + mirror.champion().fullName());
                        }
                        sheet.close();
                    }
                }
                case CLOSE -> sheet.close();
                case MOUTH -> feed(sheet.champion());
                case EYE -> sheet.setPressingEye(true);
                case DISK -> menu.open();
                case SLEEP -> {
                    // DM's C145: the sheet closes and the view goes dark until the party wakes.
                    if (party.sleep()) {
                        sheet.close();
                        actions.close();
                    }
                }
                case NONE -> { }
            }
            return;
        }
        // Clicking party boxes switches between members (DM toggles off the one shown).
        int box = bars.hitTest(x, y);
        if (sheet.candidate() == null && box >= 0 && box < party.members().size()
                && party.members().get(box).health() > 0) {
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
        if (champion == sheet.champion() || champion.health() == 0) {
            return false;
        }
        clickSlot(champion, hand.slot());
        return true;
    }

    /**
     * A click on the game menu. CANCEL goes back to the character sheet the
     * menu was opened from. Options take effect at once; BACK returns to the
     * main menu.
     */
    private void clickMenu(GameMenu.Click click) {
        switch (click.choice()) {
            case SAVE -> menu.showSlots(true);
            case LOAD -> menu.showSlots(false);
            case QUIT -> menu.showQuit();
            case SAVE_AND_QUIT -> menu.saveThenQuit();
            case QUIT_NOW -> quit();
            case CANCEL, OK -> menu.close();
            case SLOT -> {
                if (menu.screen() == GameMenu.Screen.SAVE_SLOTS) {
                    save(click.slot());
                } else {
                    load(click.slot());
                }
            }
            case OPTIONS -> menu.showOptions();
            case DIFFICULTY -> party.setDifficulty(Difficulty.values()[click.slot()]);
            case GOD_MODE -> party.setGodMode(!party.godMode());
            case DEEP_SLEEP -> party.setDeepSleep(!party.deepSleep());
            case LOCK_MASTER -> party.setLockMaster(!party.lockMaster());
            case BACK -> menu.showMain();
            case NONE -> { }
        }
    }

    private void save(int slot) {
        boolean thenQuit = menu.quitAfterSave();
        try {
            saves.save(slot, party);
        } catch (IOException e) {
            menu.showMessage("UNABLE TO SAVE GAME", e.getMessage());
            return;
        }
        if (debug) {
            System.out.println("Saved to slot " + slot + ": " + saves.file(slot).toAbsolutePath());
        }
        if (thenQuit) {
            quit();
        } else {
            menu.showMessage("GAME SAVED", null);
        }
    }

    /** Loading an empty slot does nothing. */
    private void load(int slot) {
        if (saves.header(slot) == null) {
            return;
        }
        try {
            restore(saves.load(slot));
        } catch (IOException e) {
            menu.showMessage("UNABLE TO LOAD GAME", e.getMessage());
            return;
        }
        if (debug) {
            System.out.printf("Loaded slot %d: Level %d (%d,%d)%n", slot, party.level() + 1, party.x(), party.y());
        }
        menu.showMessage("GAME LOADED, READY TO PLAY", null);
    }

    /** Swaps in a loaded game and clears what the screen remembered of the old one. */
    void restore(Party loaded) {
        party = loaded;
        sheet.close();
        automap.close();
        gameOver = false;
        bumped = false;
        bars.clearDamage();
        formation.clearPick();
        arrows.setPressed(null);
        actions.reset();
    }

    private void quit() {
        menu.close();
        if (debug) {
            System.out.println("Quit");
        }
        onQuit.run();
    }

    /**
     * Esc: opens the game menu from anywhere, even THE END (so a saved game
     * can be loaded after the party dies), and closes it again as its CANCEL
     * does.
     */
    public void escape() {
        if (party.endgame() != null && !gameWon()) {
            return; // the fuse sequence plays out first
        }
        if (menu.isOpen()) {
            menu.close();
        } else if (automap.isOpen()) {
            automap.close();
        } else {
            arrows.setPressed(null);
            sheet.setPressingEye(false);
            menu.open();
        }
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
    /**
     * DM's F377 for the floor (EVENTS.C G0462, F0373-F0375). An empty hand
     * grabs the top object of the pile clicked: the party square's far cells
     * or the near cells of the square ahead, where no creature on the ground
     * stands ({@link ViewRenderer#pileHit}). A held item is thrown from DM's
     * throw zone, or dropped in one of {@link #PILE_BOXES}; with a wall
     * ahead only on the party's own square, and never thrown.
     */
    private void clickView(int x, int y) {
        if (party.leader() == null) {
            return;
        }
        DungeonMap map = party.map();
        Direction fwd = party.facing();
        int aheadX = party.x() + fwd.dx;
        int aheadY = party.y() + fwd.dy;
        Item held = party.held();
        if (held == null) {
            for (int viewCell = 0; viewCell < 4; viewCell++) {
                Rectangle pile = view.pileHit(viewCell);
                if (pile != null && pile.contains(x, y)) {
                    grab(viewCell, aheadX, aheadY);
                    return;
                }
            }
            return;
        }
        Square ahead = map.get(aheadX, aheadY);
        boolean wallAhead = ahead.looksSolid();
        if (!wallAhead && throwHeld(x, y, ahead.type() == SquareType.DOOR && ahead.facesAlong(fwd))) {
            return;
        }
        for (int viewCell = 0; viewCell < (wallAhead ? 2 : 4); viewCell++) {
            if (PILE_BOXES[viewCell].contains(x, y)) {
                boolean onAhead = viewCell >= 2;
                int cell = fwd.cellOf(viewCell);
                party.setHeld(null);
                arrived(map.dropItem(onAhead ? aheadX : party.x(), onAhead ? aheadY : party.y(), cell, held));
                if (debug) {
                    System.out.println("Dropped " + held.name() + (onAhead ? " on the square ahead" : ""));
                }
                return;
            }
        }
    }

    /** F0373: the top object of view cell {@code viewCell}'s pile into the hand. */
    private void grab(int viewCell, int aheadX, int aheadY) {
        DungeonMap map = party.map();
        int cell = party.facing().cellOf(viewCell);
        boolean onAhead = viewCell >= 2;
        if (onAhead && creatureOnCell(map.groupAt(aheadX, aheadY), cell)) {
            return; // DM: not from under a creature on the ground
        }
        DungeonMap.Pickup pickup = map.pickUpItem(onAhead ? aheadX : party.x(), onAhead ? aheadY : party.y(), cell);
        party.setHeld(pickup.item());
        arrived(pickup.result());
        if (pickup.item() != null && debug) {
            System.out.println("Picked up " + pickup.item().name() + " from the floor");
        }
    }

    /** F0176: whether a creature of {@code group} that doesn't levitate stands on {@code cell}. */
    private static boolean creatureOnCell(Group group, int cell) {
        if (group == null || group.type().levitates()) {
            return false;
        }
        if (group.centred()) {
            return true;
        }
        for (int i = 0; i < group.count(); i++) {
            if (group.cellOf(i) == cell) {
                return true;
            }
        }
        return false;
    }

    /**
     * F0377's throw zone: screen rows 47-102, the left half from x 32 (64
     * with a door ahead seen head-on) to 111, the right half from 112 to 191
     * (163). Returns whether the click was in it and the item flew.
     */
    private boolean throwHeld(int x, int y, boolean doorAhead) {
        if (y < 47 || y > 102) {
            return false;
        }
        boolean right = x > 111;
        if (right ? x > (doorAhead ? 163 : 191) : x < (doorAhead ? 64 : 32)) {
            return false;
        }
        Item held = party.held();
        if (!party.throwHeld(right)) {
            return false;
        }
        if (debug) {
            System.out.println("Threw " + held.name() + (right ? " from the right" : " from the left"));
        }
        return true;
    }

    /** True while an item rides on the mouse pointer, which the window then hides. */
    public boolean holding() {
        return party.held() != null;
    }

    /**
     * A movement key ({@link KeyMap}): the same as clicking that arrow, which
     * lights up while the key is held. Ignored while a sheet is open or once
     * the game is over, like the arrows.
     */
    public void key(MovementPanel.Action action) {
        if (action == null || inputBlocked() || sheet.isOpen() || menu.isOpen() || automap.isOpen()
                || party.sleeping()) {
            return;
        }
        arrows.setPressed(action);
        move(action);
    }

    /** Whether keys are typing a reincarnated champion's name rather than moving. */
    public boolean typing() {
        return sheet.renaming() && !menu.isOpen() && !automap.isOpen();
    }

    /** A character typed on the rename panel: letters, , . ; : space, Enter and Backspace. */
    public void type(char c) {
        if (typing()) {
            sheet.type(c);
        }
    }

    /** Return: wakes a sleeping party (DM's G460); otherwise casts the caster's spell (not in DM). */
    public void pressReturn() {
        if (menu.isOpen() || automap.isOpen() || inputBlocked()) {
            return;
        }
        if (party.sleeping()) {
            party.wakeUp();
        } else if (spellsUsable()) {
            castSpell();
        }
    }

    /** A top-row digit 1-6: the caster enters symbol {@code column} of their current row (not in DM). */
    public void spellSymbol(int column) {
        if (spellsUsable()) {
            party.addSymbol(column);
        }
    }

    /** Backspace: the caster takes back their last symbol (not in DM). */
    public void backspace() {
        if (spellsUsable()) {
            party.deleteSymbol();
        }
    }

    /** Whether the spell area takes input: not asleep, not over, no menu, and no mirror candidate shown (DM's F0377). */
    private boolean spellsUsable() {
        return !menu.isOpen() && !automap.isOpen() && !inputBlocked() && !party.sleeping() && sheet.candidate() == null
                && party.magicCaster() >= 0;
    }

    /** DM's spell area (MENUS.C F0392-F0412), above the action area. */
    private final SpellArea spells;

    /** A click in the spell area: choose the caster, enter or take back a symbol, or cast. */
    private void clickSpellArea(int x, int y) {
        if (!spellsUsable()) {
            return;
        }
        SpellArea.Click click = SpellArea.click(party, x, y);
        switch (click.command()) {
            case CASTER -> party.setMagicCaster(click.index());
            case SYMBOL -> party.addSymbol(click.index());
            case DELETE -> party.deleteSymbol();
            case CAST -> castSpell();
            default -> { }
        }
    }

    /** DM's F0408: the caster casts, if they have entered any symbols. */
    private void castSpell() {
        int caster = party.magicCaster();
        Champion c = party.members().get(caster);
        if (c.symbols().isEmpty()) {
            return;
        }
        String spell = spellName(c.symbols());
        boolean cast = party.cast();
        if (debug) {
            System.out.println(c.name() + " casts " + spell + (cast ? "" : " (fails)"));
        }
    }

    private static String spellName(String symbols) {
        StringBuilder sb = new StringBuilder();
        for (char s : symbols.toCharArray()) {
            sb.append(sb.length() == 0 ? "" : " ").append(Spells.name(s));
        }
        return sb.toString();
    }

    /** A movement key was let go: its arrow stops being lit. */
    public void keyReleased() {
        arrows.setPressed(null);
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
        if (step != null && !party.canStep(step)) {
            return; // DM's F380 drops a move while movement is disabled
        }
        boolean moved = true;
        if (step != null) {
            DungeonMap.StepResult result = party.step(step);
            moved = result != null;
            if (!moved && party.blockedByCreatures()) {
                if (debug) {
                    System.out.println("Creatures block the way: " + party.map().groups().stream()
                            .filter(gr -> Math.abs(gr.x() - party.x()) + Math.abs(gr.y() - party.y()) == 1)
                            .map(Object::toString).toList());
                }
            } else if (!moved) {
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

    /**
     * DM's damage burst on each hurt champion's box; the caller's onDamage
     * repaints once it has expired. A killing blow shows no burst, as in DM:
     * the champion's things fall, the scream plays, and if nobody is left
     * the game is over.
     */
    private void showDamage(int[] damage) {
        onDamage.run();
        long until = clock.getAsLong() + DAMAGE_SHOWN_MS;
        for (int i = 0; i < damage.length; i++) {
            if (damage[i] > 0 && party.members().get(i).health() > 0) {
                bars.showDamage(i, damage[i], until);
                if (debug) {
                    System.out.printf("%s takes %d damage (health %d)%n",
                            party.members().get(i).name(), damage[i], party.members().get(i).health());
                }
            }
        }
        buryTheDead();
    }

    private boolean gameOver;

    /** True once the whole party has died: the screen shows THE END and ignores input. */
    public boolean gameOver() {
        return gameOver;
    }

    /** True once the fuse sequence is over: the end screen shows and ignores input (Esc still opens the menu). */
    public boolean gameWon() {
        return party.endgame() != null && party.endgame().won();
    }

    /** The game takes no input once it is over, and none during the fuse sequence (DM's F446 clears the inputs). */
    private boolean inputBlocked() {
        return gameOver || party.endgame() != null;
    }

    /**
     * One window tick of the fuse sequence: its next step, then a game tick
     * if the step asks for one (DM's F445). The sheet and menus close as it
     * starts.
     */
    private boolean endgameTick() {
        if (sheet.isOpen()) {
            sheet.close();
            actions.close();
        }
        if (gameWon()) {
            return false;
        }
        if (party.endgame().next()) {
            step();
        } else {
            takeMessages();
        }
        return true;
    }

    /** DM's F319 for whoever just died: their things fall, the scream, their sheet closes; all dead ends the game. */
    private void buryTheDead() {
        List<Champion> dead = party.bury();
        if (dead.isEmpty()) {
            return;
        }
        sounds.play(screamSound);
        if (dead.contains(sheet.champion())) {
            sheet.close();
        }
        if (debug) {
            dead.forEach(c -> System.out.println(c.name() + " has died"));
        }
        if (party.allDead()) {
            gameOver = true;
            sheet.close();
            party.setHeld(null);
            if (debug) {
                System.out.println("The party is dead: THE END");
            }
        }
    }

    /**
     * DM's endgame (F444): THE END on a cleared screen, shown through a
     * palette that is dark blue everywhere but white (colour 15), so the
     * whole screen is dark blue with white lettering.
     */
    private void drawTheEnd(Graphics2D g) {
        g.setColor(new Color(END_BLUE));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        BufferedImage end = art.image(THE_END);
        if (end == null) {
            PixelFont.draw(g, "THE END", 142, 98, Color.WHITE);
            return;
        }
        int white = Art.PALETTE[15].getRGB();
        BufferedImage tinted = new BufferedImage(end.getWidth(), end.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < end.getHeight(); y++) {
            for (int x = 0; x < end.getWidth(); x++) {
                tinted.setRGB(x, y, end.getRGB(x, y) == white ? white : END_BLUE);
            }
        }
        g.drawImage(tinted, 120, 95, null);
    }

    private static final int THE_END = 6;
    /** DM fades every colour but white to dark blue for the ending (ST 0x002). */
    private static final int END_BLUE = 0x000044;

    /** The champion mirror's frame (wall decoration 43's front picture), as F444 draws it. */
    private static final int MIRROR_FRAME = 346;

    /**
     * DM's F444 for a won game: on darkest grey, each champion in a mirror
     * frame (48 rows apart), their name and title in gold and each base
     * skill above level 1 in light grey, in the font's scroll lettering.
     */
    private void drawEndScreen(Graphics2D g) {
        g.setColor(Art.PALETTE[12]);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        BufferedImage frame = art.keyed(MIRROR_FRAME, 10);
        for (int i = 0; i < party.members().size(); i++) {
            Champion c = party.members().get(i);
            int y = i * 48;
            if (frame != null) {
                g.drawImage(frame, 11, 7 + y, null);
            }
            BufferedImage portrait = keyedPortrait(c.portrait());
            if (portrait != null) {
                g.drawImage(portrait, 27, 13 + y, null);
            }
            y += 14;
            endgameText(g, c.name(), 87, y, Art.PALETTE[9]);
            int x = 87 + 6 * c.name().length();
            char first = c.title().isEmpty() ? ' ' : c.title().charAt(0);
            if (first != ',' && first != ';' && first != '-') {
                x += 6;
            }
            endgameText(g, c.title(), x, y++, Art.PALETTE[9]);
            for (int skill = 0; skill < Champion.BASE_SKILLS.size(); skill++) {
                int level = c.lastingSkillLevel(skill);
                if (level > 1) {
                    y += 8;
                    endgameText(g, Champion.LEVEL_NAMES.get(level - 2) + " " + Champion.BASE_SKILLS.get(skill), 105, y,
                            Art.PALETTE[13]);
                }
            }
        }
    }

    /** DM's F443: text in the scroll lettering (A-Z moved 64 codes down), on darkest grey. */
    private void endgameText(Graphics2D g, String text, int x, int y, Color colour) {
        DmFont font = art.font();
        if (font != null) {
            font.draw(g, CharacterSheet.scrollGlyphs(text), x, y, colour, Art.PALETTE[12]);
        } else {
            PixelFont.draw(g, text, x, y - 4, colour);
        }
    }

    /** A portrait with its dark grey (colour 1) see-through, as F444 blits it. */
    private BufferedImage keyedPortrait(int n) {
        BufferedImage src = art.portrait(n);
        if (src == null) {
            return null;
        }
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int clear = Art.PALETTE[1].getRGB();
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                out.setRGB(x, y, argb == clear ? 0 : argb);
            }
        }
        return out;
    }

    public void render(Graphics2D g) {
        if (gameOver && !menu.isOpen()) {
            drawTheEnd(g);
            return;
        }
        if (gameWon() && !menu.isOpen()) {
            drawEndScreen(g);
            return;
        }
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        ChampionMirror viewed = sheet.candidate();
        int shields = (party.shieldDefense() > 0 ? ChampionBars.PARTY_SHIELD : 0)
                | (party.spellShieldDefense() > 0 ? ChampionBars.SPELL_SHIELD : 0)
                | (party.fireShieldDefense() > 0 ? ChampionBars.FIRE_SHIELD : 0);
        bars.draw(g, party.members(), sheet.champion(), viewed == null ? null : viewed.champion(),
                clock.getAsLong(), shields);
        formation.draw(g, party);
        automap.drawButton(g, art);
        if (menu.isOpen()) {
            menu.draw(g, saves::header,
                    new GameMenu.Settings(party.difficulty(), party.godMode(), party.deepSleep(), party.lockMaster()));
        } else if (automap.isOpen()) {
            automap.draw(g, party, art);
        } else if (sheet.isOpen()) {
            sheet.draw(g, party);
        } else if (party.sleeping()) {
            drawSleep(g);
        } else {
            drawView(g);
        }
        if (bumped) {
            g.setColor(new Color(200, 0, 0));
            g.setStroke(new BasicStroke(2));
            Rectangle v = ViewRenderer.VIEWPORT;
            g.drawRect(v.x + 1, v.y + 1, v.width - 2, v.height - 2);
        }
        arrows.draw(g);
        if (party.sleeping()) {
            Rectangle a = MovementPanel.AREA;
            ActionArea.shade(g, a.x, a.y, a.width, a.height); // DM's F456 disabled menus
        }
        spells.draw(g, party, party.sleeping());
        actions.draw(g, party, sheet.candidate() != null || party.sleeping());
        takeMessages();
        messages.draw(g);
        if (debug) {
            PixelFont.draw(g, party.x() + "," + party.y() + " " + party.facing(), 236, 190, Color.YELLOW);
        }
        drawHeldItem(g);
    }

    /** DM's F379 sleep screen: a black viewport with WAKE UP in cyan. */
    private static void drawSleep(Graphics2D g) {
        Rectangle v = ViewRenderer.VIEWPORT;
        g.setColor(Color.BLACK);
        g.fillRect(v.x, v.y, v.width, v.height);
        PixelFont.draw(g, "WAKE UP", v.x + 93, v.y + 69, Art.PALETTE[4]);
    }

    /** Screen-sized buffer the dungeon view is drawn into before {@link Darkness} dims it. */
    private final BufferedImage viewBuffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);

    /**
     * The dungeon view in the palette the party's light calls for (DM's
     * F337). Only the viewport darkens; the rest of the screen keeps the
     * bright palette, as in DM.
     */
    private void drawView(Graphics2D g) {
        int palette = party.paletteIndex();
        int[] sets = CreatureArt.replacementSets(party.map().creatureTypes());
        int[] colour9 = sets[0] < 0 ? null : CreatureArt.levels(sets[0]);
        int[] colour10 = sets[1] < 0 ? null : CreatureArt.levels(sets[1]);
        if (palette == 0 && colour9 == null && colour10 == null) {
            view.draw(g, party);
            return;
        }
        Graphics2D vg = viewBuffer.createGraphics();
        try {
            vg.setColor(Color.BLACK);
            vg.fillRect(0, 0, WIDTH, HEIGHT);
            view.draw(vg, party);
        } finally {
            vg.dispose();
        }
        Rectangle v = ViewRenderer.VIEWPORT;
        Darkness.apply(viewBuffer, v, palette, colour9, colour10);
        g.drawImage(viewBuffer.getSubimage(v.x, v.y, v.width, v.height), v.x, v.y, null);
    }

    /** The held item replaces the mouse pointer: its icon centred on the pointer, over everything else. */
    private void drawHeldItem(Graphics2D g) {
        Item held = party.held();
        if (held == null || pointer == null || sheet.isOpen() && sheet.pressingEye()) {
            return; // DM's F352 hides the pointer while the eye is held
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
}
