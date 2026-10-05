package dm.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** The party: its champions, and its position and facing on the current map. */
public final class Party implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Relative moves offered by the movement arrow panel. */
    public enum Move {
        FORWARD(0), RIGHT(1), BACKWARD(2), LEFT(3);

        /** Clockwise quarter-turns from the facing direction. */
        final int turns;

        Move(int turns) {
            this.turns = turns;
        }
    }

    public static final int MAX_MEMBERS = 4;
    /**
     * Formation positions, numbered like DM's cells, clockwise from the front
     * left (relative to the way the party faces). New recruits fill them in
     * this order.
     */
    public static final int FRONT_LEFT = 0;
    public static final int FRONT_RIGHT = 1;
    public static final int BACK_RIGHT = 2;
    public static final int BACK_LEFT = 3;
    /**
     * Damage for walking into a wall. In DM this is 1 point reduced by torso
     * and leg armour; armour values aren't modelled yet, so it is a flat 1.
     */
    public static final int BUMP_DAMAGE = 1;
    /**
     * DM's attack strength for falling into a pit (F0324 with 20): each
     * champion takes half of it plus a random amount below that half, so
     * 10-19 points. In DM leg and foot armour soften it; armour isn't
     * modelled yet.
     */
    public static final int FALL_ATTACK = 20;
    /** Falls and teleports chained in one move before the party is taken to be stuck in a loop. */
    private static final int MAX_HOPS = 8;

    /** Every level of the dungeon; stairs, pits and teleporters move the party between them. */
    private final Dungeon dungeon;
    private DungeonMap map;
    private Random random = new Random();
    private final List<Champion> members = new ArrayList<>();
    private final Champion[] positions = new Champion[MAX_MEMBERS];
    private int x;
    private int y;
    private Direction facing;
    /** The item on the mouse pointer (DM's leader hand), shared by the whole party. */
    private Item held;
    /** DM's game clock, one per game tick, and when the party last moved (rest speeds recovery). */
    private long time;
    private long lastMove;

    /** A party on a single map; its stairs lead nowhere and block like walls. */
    public Party(DungeonMap map, int x, int y, Direction facing) {
        this(List.of(map), 0, x, y, facing);
    }

    /** A party on {@code maps.get(mapIndex)}, able to take stairs to the other maps. */
    public Party(List<DungeonMap> maps, int mapIndex, int x, int y, Direction facing) {
        this.dungeon = new Dungeon(maps);
        this.map = maps.get(mapIndex);
        this.x = x;
        this.y = y;
        this.facing = facing;
        map.placeParty(this);
        dungeon.creatures().partyArrived(this, map);
    }

    // ---- options (the game menu's, not DM's) --------------------------------------

    /** The difficulty; null in games saved before it existed, which play as NORMAL. */
    private Difficulty difficulty = Difficulty.NORMAL;
    private boolean godMode;

    public Difficulty difficulty() {
        return difficulty == null ? Difficulty.NORMAL : difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    /**
     * God mode: no champion's health, stamina, mana, food or water ever goes
     * down, nobody is wounded, and every spell cast succeeds at no cost.
     */
    public boolean godMode() {
        return godMode;
    }

    public void setGodMode(boolean on) {
        godMode = on;
        for (Champion c : members) {
            c.setGodMode(on);
        }
    }

    /** Deep sleep: lying down to sleep restores every living champion's health, stamina and mana at once. */
    private boolean deepSleep;

    public boolean deepSleep() {
        return deepSleep;
    }

    public void setDeepSleep(boolean on) {
        deepSleep = on;
    }

    /** Lock master: keyholes, locks and coin slots open without their key or coin ({@link DungeonMap#clickWall}). */
    private boolean lockMaster;

    public boolean lockMaster() {
        return lockMaster;
    }

    public void setLockMaster(boolean on) {
        lockMaster = on;
    }

    // ---- spells (Sprint 19) --------------------------------------------------------

    /** DM's G514: the member whose symbols the spell area shows. */
    private int magicCaster;
    /** DM's party magical light (F337 adds it to the torches'): light spells add to it, darkness takes away. */
    private int magicalLight;
    /** DM's party shield, fire shield and spell shield defenses. */
    private int shieldDefense;
    private int fireShieldDefense;
    private int spellShieldDefense;
    /** The light and shield spells' events, waiting to run out (null in games saved before Sprint 19). */
    private List<Magic.PartySpell> partySpells = new ArrayList<>();

    /**
     * The member casting spells (DM's magic caster): the one chosen, or the
     * first living member if they have died; -1 with nobody alive.
     */
    public int magicCaster() {
        if (magicCaster < members.size() && members.get(magicCaster).health() > 0) {
            return magicCaster;
        }
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).health() > 0) {
                magicCaster = i;
                return i;
            }
        }
        return -1;
    }

    /** DM's F394: member {@code member} becomes the caster, if alive. */
    public boolean setMagicCaster(int member) {
        if (member < 0 || member >= members.size() || members.get(member).health() == 0) {
            return false;
        }
        magicCaster = member;
        return true;
    }

    /** DM's F399: the caster enters the symbol in column {@code column} (0-5) of their current row. Returns whether they could pay for it. */
    public boolean addSymbol(int column) {
        int caster = magicCaster();
        return caster >= 0 && Magic.addSymbol(this, caster, column);
    }

    /** DM's F400: the caster takes back their last symbol. */
    public boolean deleteSymbol() {
        int caster = magicCaster();
        return caster >= 0 && Magic.deleteSymbol(this, caster);
    }

    /**
     * DM's F408: the caster casts the spell their symbols make (see
     * {@link Magic#cast}). Returns whether a spell was cast; messages tell
     * why not.
     */
    public boolean cast() {
        int caster = magicCaster();
        return caster >= 0 && Magic.cast(this, caster) == Magic.Result.CAST;
    }

    /** DM's magical light: what light spells add to the torches' light (darkness makes it negative). */
    public int magicalLight() {
        return magicalLight;
    }

    void addMagicalLight(int amount) {
        magicalLight += amount;
    }

    /** DM's party shield: added to every body part's defense (F313). */
    public int shieldDefense() {
        return shieldDefense;
    }

    /** DM's fire shield: taken off every fire attack on a champion (F321). */
    public int fireShieldDefense() {
        return fireShieldDefense;
    }

    /** DM's spell shield: taken off every magic attack on a champion (F321). */
    public int spellShieldDefense() {
        return spellShieldDefense;
    }

    void addShield(int kind, int amount) {
        switch (kind) {
            case Magic.PARTY_SHIELD -> shieldDefense += amount;
            case Magic.FIRE_SHIELD -> fireShieldDefense += amount;
            case Magic.SPELL_SHIELD -> spellShieldDefense += amount;
            default -> throw new IllegalArgumentException("not a shield: " + kind);
        }
    }

    /** DM's event counts (Sprint 20): invisibility (71), thieves' eye (73) and magic footprints (79) spells running. */
    private int invisibility;
    private int thievesEye;
    private int footprints;
    /** DM's first and last scent index: the scents in [first, last) show footprints. */
    private int firstFootprint;
    private int lastFootprint;

    /** True while an invisibility spell runs: only creatures that see the invisible can see the party (F200). */
    public boolean invisible() {
        return invisibility > 0;
    }

    /** True while a thieves' eye runs: the wall (or door) straight ahead has a hole to see through. */
    public boolean thievesEye() {
        return thievesEye > 0;
    }

    void addSpellCount(int kind, int amount) {
        switch (kind) {
            case Magic.INVISIBILITY -> invisibility += amount;
            case Magic.THIEVES_EYE -> thievesEye += amount;
            case Magic.FOOTPRINTS -> footprints += amount;
            default -> throw new IllegalArgumentException("not a counted spell: " + kind);
        }
    }

    /**
     * F0412's magic footprints: they start at the next scent and, as the
     * party walks, take in every scent left while a footprints spell runs.
     */
    void startFootprints(int power) {
        footprints++;
        firstFootprint = scents.size();
        lastFootprint = power < 3 ? firstFootprint : 0;
    }

    /** DM's F316 for the oldest scent: the footprints' indexes move down with the rest. */
    private void deleteOldestScent() {
        scents.remove(0);
        if (firstFootprint > 0) {
            firstFootprint--;
        }
        if (lastFootprint > 0) {
            lastFootprint--;
        }
    }

    /** DM's F172: whether square (sx, sy) of {@code m} shows the party's magic footprints. */
    public boolean footprintsAt(DungeonMap m, int sx, int sy) {
        int ordinal = scentOrdinal(m, sx, sy);
        return ordinal > 0 && ordinal - 1 >= firstFootprint && ordinal - 1 < lastFootprint;
    }

    private List<Magic.PartySpell> partySpells() {
        if (partySpells == null) {
            partySpells = new ArrayList<>();
        }
        return partySpells;
    }

    void addPartySpell(Magic.PartySpell e) {
        partySpells().add(e);
    }

    /** Runs the party spells' events that are due. Returns whether any did. */
    private boolean tickPartySpells() {
        boolean any = false;
        for (Magic.PartySpell e : new ArrayList<>(partySpells())) {
            if (e.time() <= time) {
                partySpells().remove(e);
                Magic.expire(this, e);
                any = true;
            }
        }
        return any;
    }

    /** Replaces the random numbers behind fall damage and the creatures' decisions, for tests. */
    public void setRandom(Random random) {
        this.random = random;
    }

    Random random() {
        return random;
    }

    /** The dungeon the party is in: every map, and the creatures' timeline. */
    public Dungeon dungeon() {
        return dungeon;
    }

    /** The map the party is on. */
    public DungeonMap map() {
        return map;
    }

    /** The current dungeon level, 0 for the first. */
    public int level() {
        return map.level();
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public Direction facing() {
        return facing;
    }

    public List<Champion> members() {
        return Collections.unmodifiableList(members);
    }

    /** The item being carried on the mouse pointer, or null. */
    public Item held() {
        return held;
    }

    public void setHeld(Item item) {
        held = item;
    }

    public boolean isFull() {
        return members.size() >= MAX_MEMBERS;
    }

    /** Resurrects the mirror's champion into the party; false if the party is full or the mirror is empty. */
    public boolean recruit(ChampionMirror mirror) {
        if (!recruitQuietly(mirror)) {
            return false;
        }
        message(mirror.champion().name() + " RESURRECTED.", members.size() - 1);
        return true;
    }

    private boolean recruitQuietly(ChampionMirror mirror) {
        if (isFull() || mirror.taken()) {
            return false;
        }
        Champion c = mirror.champion();
        members.add(c);
        c.face(facing);
        c.setFood(1500 + random.nextInt(256)); // DM's F280
        c.setWater(1500 + random.nextInt(256));
        c.setGodMode(godMode);
        for (int p = 0; p < MAX_MEMBERS; p++) {
            if (positions[p] == null) {
                positions[p] = mirror.champion();
                break;
            }
        }
        mirror.markTaken();
        return true;
    }

    /**
     * DM's Reincarnate: the mirror's champion joins as {@link #recruit} does,
     * under a new name and title, with no skills and 12 more statistic points
     * ({@link Champion#reincarnate}). False if the party is full, the mirror
     * is empty, or the name is blank or already a member's (DM's rename
     * panel won't take those).
     */
    public boolean reincarnate(ChampionMirror mirror, String name, String title) {
        String n = name.stripTrailing();
        if (isFull() || mirror.taken() || !nameFree(n)) {
            return false;
        }
        Champion c = mirror.champion();
        c.reincarnate(n, title.stripTrailing(), random);
        recruitQuietly(mirror);
        message(c.name() + " REINCARNATED.", members.size() - 1);
        return true;
    }

    /** Whether {@code name} could be a reincarnated champion's: not blank, and no member already has it. */
    public boolean nameFree(String name) {
        String n = name.stripTrailing();
        return !n.isEmpty() && members.stream().noneMatch(m -> m.name().equals(n));
    }

    /** The champion standing in formation position {@code position}, or null. */
    public Champion at(int position) {
        return positions[position];
    }

    /** The formation position of {@code champion}, or -1 if not in the party. */
    public int positionOf(Champion champion) {
        for (int p = 0; p < MAX_MEMBERS; p++) {
            if (positions[p] == champion) {
                return p;
            }
        }
        return -1;
    }

    /** Swaps whoever stands in two formation positions; either may be empty, which moves a champion. */
    public void swap(int a, int b) {
        Champion tmp = positions[a];
        positions[a] = positions[b];
        positions[b] = tmp;
    }

    /** The mirror straight ahead on the adjacent wall, if there is one with a champion still in it. */
    public ChampionMirror facingMirror() {
        ChampionMirror m = map.mirrorAt(x + facing.dx, y + facing.dy, facing.opposite());
        return m == null || m.taken() ? null : m;
    }

    /**
     * Walking into a wall hurts the two champions on the side that hit it:
     * the front row going forward, the back row going backward, and the left
     * or right pair when sidestepping. Returns the damage each member took,
     * indexed like {@link #members()}.
     */
    public int[] bump(Move move) {
        int[] hit = switch (move) {
            case FORWARD -> new int[] {FRONT_LEFT, FRONT_RIGHT};
            case BACKWARD -> new int[] {BACK_LEFT, BACK_RIGHT};
            case LEFT -> new int[] {FRONT_LEFT, BACK_LEFT};
            case RIGHT -> new int[] {FRONT_RIGHT, BACK_RIGHT};
        };
        int[] damage = new int[members.size()];
        for (int p : hit) {
            if (positions[p] != null) {
                damage[members.indexOf(positions[p])] = positions[p].takeDamage(BUMP_DAMAGE);
            }
        }
        return damage;
    }

    /**
     * DM's F284: the party turns to {@code d}, and every champion turns by
     * the same amount (a champion who had turned to an attacker stays turned
     * relative to the party).
     */
    private void turnTo(Direction d) {
        int delta = (d.ordinal() - facing.ordinal()) & 3;
        for (Champion c : members) {
            c.face(Direction.fromIndex(c.facing().ordinal() + delta));
        }
        facing = d;
    }

    public void turnLeft() {
        turnTo(facing.turnLeft());
    }

    public void turnRight() {
        turnTo(facing.turnRight());
    }

    /** Whether the last blocked step was stopped by creatures rather than a wall (no bump then, as in DM). */
    private boolean blockedByCreatures;

    public boolean blockedByCreatures() {
        return blockedByCreatures;
    }

    /** Attempts a step; returns false (and stays put) if the target square blocks. */
    public boolean move(Move move) {
        return step(move) != null;
    }

    /**
     * Attempts a step and runs the floor sensors on the squares left and
     * entered. Returns null if the move was blocked.
     *
     * Stepping onto stairs takes the party to the level above or below, as
     * in DM: onto the square beside the matching stairs there, facing away
     * from them. Stairs with no matching stairs on the next level block.
     * Wherever the party ends up, open pits and teleporters then act on it
     * ({@link #settle()}).
     */
    public DungeonMap.StepResult step(Move move) {
        payForStep();
        Direction d = Direction.fromIndex(facing.ordinal() + move.turns);
        int nx = x + d.dx;
        int ny = y + d.dy;
        blockedByCreatures = map.hasCreatures(nx, ny);
        if (blockedByCreatures) { // DM: the group turns on the party
            dungeon.creatures().react(this, map, nx, ny, CreatureAI.PARTY_ADJACENT);
            return null;
        }
        if (!map.isPassable(nx, ny)) {
            return null;
        }
        Square target = map.get(nx, ny);
        Dungeon.Location stairs = null;
        if (target.type() == SquareType.STAIRS) {
            stairs = dungeon.stairsPartner(map, nx, ny, target.stairsUp());
            if (stairs == null) {
                return null;
            }
        }
        lastMove = time;
        DungeonMap.StepResult result = moveTo(map, nx, ny);
        if (stairs != null) {
            DungeonMap.StairsExit exit = stairs.map().stairsExit(stairs.x(), stairs.y());
            turnTo(exit.facing());
            result = result.and(moveTo(stairs.map(), exit.x(), exit.y()));
        }
        return result.and(settle());
    }

    /**
     * Puts the party on (nx, ny) of {@code to}, running the floor sensors on
     * the square it left and the one it arrived on.
     */
    private DungeonMap.StepResult moveTo(DungeonMap to, int nx, int ny) {
        DungeonMap from = map;
        int fromX = x;
        int fromY = y;
        x = nx;
        y = ny;
        leaveScent(from, fromX, fromY, to, nx, ny);
        Group squashed = to.groupAt(nx, ny);
        if (squashed != null) { // DM deletes a group the party lands on (by teleporter)
            to.removeGroup(squashed);
            dungeon.creatures().deleteEvents(to, nx, ny);
            for (Item item : squashed.possessions()) {
                to.dropItem(nx, ny, random.nextInt(4), item);
            }
        }
        if (to == from) {
            return from.partyMoved(this, fromX, fromY);
        }
        map = to;
        DungeonMap.StepResult left = from.partyLeft(fromX, fromY);
        dungeon.creatures().partyLeft(from);
        dungeon.creatures().partyArrived(this, to);
        return left.and(to.partyMoved(this, -1, -1))
                .and(new DungeonMap.StepResult(false, false, true));
    }

    // ---- scent (DM's party scents, which creatures follow) --------------------

    /** A square the party walked on, and how strongly it still smells of it. */
    private static final class Scent implements Serializable {
        private static final long serialVersionUID = 1L;
        final DungeonMap map;
        final int x;
        final int y;
        int strength;

        Scent(DungeonMap map, int x, int y) {
            this.map = map;
            this.x = x;
            this.y = y;
        }

        boolean at(DungeonMap m, int sx, int sy) {
            return map == m && x == sx && y == sy;
        }
    }

    /** DM keeps the last 24 squares the party walked on. */
    private static final int MAX_SCENTS = 24;
    private final List<Scent> scents = new ArrayList<>();
    private long lastPartyMoveTime;

    /**
     * DM's F267 for the party: the square left smells stronger the longer
     * the party stood on it (up to 80), and the new square starts at 24.
     */
    private void leaveScent(DungeonMap from, int fromX, int fromY, DungeonMap to, int toX, int toY) {
        if (members.isEmpty()) {
            return;
        }
        while (scents.size() >= MAX_SCENTS) {
            deleteOldestScent();
        }
        if (!scents.isEmpty()) {
            addScentStrength(from, fromX, fromY, (int) (time - lastPartyMoveTime), false);
        }
        lastPartyMoveTime = time;
        scents.add(new Scent(to, toX, toY));
        if (footprints > 0) {
            lastFootprint = scents.size();
        }
        addScentStrength(to, toX, toY, 24, true);
    }

    /** DM's F316. */
    private void addScentStrength(DungeonMap m, int sx, int sy, int cycles, boolean merge) {
        Integer value = null;
        for (int i = scents.size() - 1; i >= 0; i--) {
            Scent s = scents.get(i);
            if (s.at(m, sx, sy)) {
                if (value == null) {
                    value = merge ? Math.max(s.strength, cycles) : Math.min(80, s.strength + cycles);
                }
                s.strength = value;
            }
        }
    }

    /** DM's F331 (part): every scent but the party's own square fades by 1; the oldest goes when it's gone. */
    private void fadeScents() {
        for (int i = 0; i + 1 < scents.size(); i++) {
            Scent s = scents.get(i);
            if (!s.at(map, x, y)) {
                s.strength = Math.max(0, s.strength - 1);
                if (s.strength == 0 && i == 0) {
                    deleteOldestScent();
                }
            }
        }
    }

    /** DM's F315: 1 + the index of the latest scent on (sx, sy) of {@code m}, or 0. */
    int scentOrdinal(DungeonMap m, int sx, int sy) {
        for (int i = scents.size() - 1; i >= 0; i--) {
            if (scents.get(i).at(m, sx, sy)) {
                return i + 1;
            }
        }
        return 0;
    }

    int scentStrength(int index) {
        return scents.get(index).strength;
    }

    /** The square of scent {@code index}, or null if there is none. */
    int[] scentAt(int index) {
        return index < scents.size() ? new int[] {scents.get(index).x, scents.get(index).y} : null;
    }

    /** The member standing in absolute cell {@code cell} of the party's square (DM's F285), or -1. */
    int memberInCell(int cell) {
        Champion c = positions[(cell - facing.ordinal()) & 3];
        return c == null ? -1 : members.indexOf(c);
    }

    /** The absolute cell (0 NW, 1 NE, 2 SE, 3 SW) of the party's square that {@code c} stands on (DM's champion cell). */
    int cellOf(Champion c) {
        return (Math.max(0, positionOf(c)) + facing.ordinal()) & 3;
    }

    // ---- actions (DM's action area, Sprint 16) ------------------------------------

    /**
     * The actions member {@code member} can take with what is in their
     * action hand, as DM's action menu lists them; empty when they can't act
     * (dead, recovering from their last action, or holding something with no
     * actions). Item magic isn't offered yet.
     */
    public List<Integer> actions(int member) {
        return member < 0 || member >= members.size() ? List.of() : Combat.actionsFor(members.get(member));
    }

    /**
     * Member {@code member} performs {@code action}, one of {@link #actions}
     * (DM's F391 and F407). Returns what DM's action area then shows: the
     * damage a blow did (0 for none), or {@link #CANT_REACH} or
     * {@link #NEED_AMMO}.
     */
    public int act(int member, int action) {
        if (!actions(member).contains(action)) {
            return 0;
        }
        return Combat.act(this, member, action);
    }

    /** {@link #act}'s result when a back-row champion can't reach past the one in front. */
    public static final int CANT_REACH = Combat.CANT_REACH;
    /** {@link #act}'s result when a bow or sling has nothing to shoot. */
    public static final int NEED_AMMO = Combat.NEED_AMMO;

    /**
     * DM's F329: the leader throws the item on the pointer from the left or
     * right of the party's front, the way it faces. Returns false if there
     * was nothing to throw or nobody to throw it.
     */
    public boolean throwHeld(boolean right) {
        Champion thrower = leader();
        return held != null && thrower != null && Combat.throwFrom(this, members.indexOf(thrower), null, right ? 1 : 0);
    }

    /**
     * DM's F325 for member {@code member}: stamina spent; spending more than
     * is left hurts by half the shortfall (shown with the next tick).
     */
    void spendStamina(int member, int amount) {
        Champion c = members.get(member);
        int damage = c.decrementStamina(amount);
        if (damage > 0) {
            dungeon.creatures().hurtChampion(this, member, damage, 0, 0);
        }
    }

    /**
     * The rope's CLIMB DOWN (DM's F407 with F267): the party steps onto the
     * pit ahead and, if it is open, climbs down to the level below unhurt,
     * which tires every champion (a little more for a heavy load). Returns
     * false if there is no pit ahead, or creatures hover over it.
     */
    boolean climbDown() {
        int ax = x + facing.dx;
        int ay = y + facing.dy;
        if (map.get(ax, ay).type() != SquareType.PIT || map.hasCreatures(ax, ay)) {
            return false;
        }
        lastMove = time;
        moveTo(map, ax, ay);
        Dungeon.Location below = map.dropsThrough(x, y) ? map.below(x, y) : null;
        if (below != null) {
            moveTo(below.map(), below.x(), below.y());
            for (int i = 0; i < members.size(); i++) {
                Champion c = members.get(i);
                if (c.health() > 0) {
                    spendStamina(i, c.load() * 25 / c.maxLoad() + 1);
                }
            }
        }
        dungeon.creatures().changed(this);
        return true;
    }

    /** Lets everyone recovering from an action whose time is up act again (DM's event 11). Returns whether anyone did. */
    private boolean enableActions() {
        boolean any = false;
        for (Champion c : members) {
            if (c.actionDisabled() && c.enabledAt() <= time) {
                Combat.enable(c);
                any = true;
            }
        }
        return any;
    }

    /**
     * DM's F390: each champion but the leader turns to face the hardest blow
     * they took since the last tick. Returns whether anyone turned.
     */
    private boolean faceAttackers() {
        boolean turned = false;
        Champion lead = leader();
        for (Champion c : members) {
            if (c != lead && c.maxDamageReceived() > 0 && c.facing() != c.maxDamageDirection()) {
                c.face(c.maxDamageDirection());
                turned = true;
            }
            c.clearMaxDamageReceived();
        }
        return turned;
    }

    // ---- experience (DM's F304) ------------------------------------------------

    /** When a creature last attacked the party (DM's G361); fighting skills learn faster right after. */
    private long lastCreatureAttackTime = -200;

    void creatureAttacked() {
        lastCreatureAttackTime = time;
        wakeUp(); // F230: any blow aimed at a sleeper wakes the party
    }

    // ---- sleeping ----------------------------------------------------------------

    /** DM's G300: the party is asleep, from the sheet's ZZZ icon until woken. */
    private boolean sleeping;

    public boolean sleeping() {
        return sleeping;
    }

    /**
     * The party lies down to sleep. While asleep time effects come four
     * times as often with mana, stamina and health regained twice as fast,
     * every skill counts as level 1, dexterity and armour are halved, and
     * creatures walk silently. With {@link #deepSleep()} on, every living
     * champion's health, stamina and mana are full at once. False with no
     * one alive to sleep.
     */
    public boolean sleep() {
        if (members.stream().noneMatch(c -> c.health() > 0)) {
            return false;
        }
        if (deepSleep) {
            for (Champion c : members) {
                if (c.health() > 0) {
                    c.refresh();
                }
            }
        }
        setSleeping(true);
        return true;
    }

    /** DM's F314: the party wakes (clicking the view, Return, or being attacked). */
    public void wakeUp() {
        if (sleeping) {
            setSleeping(false);
        }
    }

    private void setSleeping(boolean asleep) {
        sleeping = asleep;
        for (Champion c : members) {
            c.setAsleep(asleep);
        }
    }

    /** A line for DM's message area: text, in member {@code member}'s colour (-1 for the default cyan). */
    public record Message(String text, int member) {
    }

    private transient List<Message> messages;

    /** Messages printed since the last call, oldest first; the screen shows them in its message area. */
    public List<Message> takeMessages() {
        List<Message> out = messages == null ? List.of() : messages;
        messages = null;
        return out;
    }

    void message(String text, int member) {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        messages.add(new Message(text, member));
    }

    /**
     * DM's F304: member {@code member} earns {@code amount} experience in
     * {@code skill} (and its base skill, for a hidden one). Fighting skills
     * (swing to shoot) learn half as fast with no creature attack in the last
     * 150 ticks and twice as fast within 25; deeper maps multiply it by their
     * difficulty, and the game's {@link #difficulty()} scales the result. A new base skill level raises statistics, health, stamina
     * and mana as DM does and is announced in the message area.
     */
    public void addSkillExperience(int member, int skill, int amount) {
        Champion c = members.get(member);
        boolean fighting = skill >= Champion.SWING && skill <= Champion.SHOOT;
        if (fighting && lastCreatureAttackTime < time - 150) {
            amount >>= 1;
        }
        if (amount == 0) {
            return;
        }
        if (map.difficulty() != 0) {
            amount *= map.difficulty();
        }
        int base = skill >= Champion.SWING ? (skill - Champion.SWING) >> 2 : skill;
        int before = c.baseLevel(base, false);
        if (skill >= Champion.SWING && lastCreatureAttackTime > time - 25) {
            amount <<= 1;
        }
        amount = difficulty().experience(amount, random);
        c.addExperience(skill, amount);
        if (c.temporaryExperience(skill) < 32000) {
            c.addTemporaryExperience(skill, Math.max(1, Math.min(amount >> 3, 100)));
        }
        if (skill >= Champion.SWING) {
            c.addExperience(base, amount);
        }
        int after = c.baseLevel(base, false);
        if (after > before) {
            levelUp(c, base, after);
            message(c.name() + " JUST GAINED A " + Champion.BASE_SKILLS.get(base) + " LEVEL!", member);
        }
    }

    /** F304's gains for reaching level {@code level} in base skill {@code base}. */
    private void levelUp(Champion c, int base, int level) {
        int minor = random.nextInt(2);
        int major = 1 + random.nextInt(2);
        int vitality = random.nextInt(2);
        if (base != Champion.PRIEST) {
            vitality &= level; // 0 on even levels
        }
        c.raiseMaxStat(Champion.Stat.VITALITY, vitality);
        int stamina = c.rawMaxStamina();
        c.raiseMaxStat(Champion.Stat.ANTI_FIRE, random.nextInt(2) & ~level); // 0 on odd levels
        int health = level;
        switch (base) {
            case Champion.FIGHTER -> {
                stamina >>= 4;
                health *= 3;
                c.raiseMaxStat(Champion.Stat.STRENGTH, major);
                c.raiseMaxStat(Champion.Stat.DEXTERITY, minor);
            }
            case Champion.NINJA -> {
                stamina /= 21;
                health <<= 1;
                c.raiseMaxStat(Champion.Stat.STRENGTH, minor);
                c.raiseMaxStat(Champion.Stat.DEXTERITY, major);
            }
            default -> { // wizard and priest
                if (base == Champion.WIZARD) {
                    stamina >>= 5;
                    c.raiseMaxMana(level + (level >> 1));
                    c.raiseMaxStat(Champion.Stat.WISDOM, major);
                } else {
                    stamina /= 25;
                    c.raiseMaxMana(level);
                    health += (health + 1) >> 1;
                    c.raiseMaxStat(Champion.Stat.WISDOM, minor);
                }
                c.raiseMaxMana(Math.min(random.nextInt(4), level - 1));
                c.raiseMaxStat(Champion.Stat.ANTI_MAGIC, random.nextInt(3));
            }
        }
        c.raiseMaxHealth(health + random.nextInt((health >> 1) + 1));
        c.raiseMaxStamina(stamina + random.nextInt((stamina >> 1) + 1));
    }

    // ---- poison --------------------------------------------------------------

    /** DM's poison events come every 36 ticks. */
    static final int POISON_PERIOD = 36;

    /**
     * DM's F322: poison of strength {@code attack} works on member
     * {@code member}: attack / 64 damage now (at least 1), and again 36 ticks
     * later with attack - 1, until it runs out. Returns the damage done now.
     */
    int poison(int member, int attack) {
        Champion c = members.get(member);
        if (c.health() == 0) {
            return 0;
        }
        int damage = c.takeDamage(Math.max(1, attack >> 6));
        if (attack - 1 > 0) {
            c.poisons().add(new Champion.Poison(attack - 1, time + POISON_PERIOD));
        }
        return damage;
    }

    /** Runs the poison events due now. Returns damage per member, or null. */
    private int[] tickPoison() {
        int[] damage = null;
        for (int i = 0; i < members.size(); i++) {
            Champion c = members.get(i);
            List<Champion.Poison> due = new ArrayList<>();
            c.poisons().removeIf(p -> p.due() <= time && due.add(p));
            for (Champion.Poison p : due) {
                int d = poison(i, p.attack());
                if (d > 0) {
                    damage = damage == null ? new int[members.size()] : damage;
                    damage[i] += d;
                }
            }
        }
        return damage;
    }

    /**
     * Lets the square under the party act on it, as many times as it takes:
     * an open pit (not an imaginary one) drops it to the same spot one level
     * down, hurting every champion; an open teleporter that moves the party
     * sends it to its target, turned as the teleporter says (one that
     * targets its own square is a spinner: it only turns). Called after
     * every step, and by the game whenever a sensor may have opened a pit or
     * teleporter under the party.
     */
    public DungeonMap.StepResult settle() {
        DungeonMap.StepResult result = DungeonMap.StepResult.NOTHING;
        for (int hop = 0; hop < MAX_HOPS; hop++) {
            Dungeon.Location below = map.dropsThrough(x, y) ? map.below(x, y) : null;
            if (below != null) {
                result = result.and(moveTo(below.map(), below.x(), below.y()))
                        .and(new DungeonMap.StepResult(false, false, false, true, false, fall()));
                continue;
            }
            Teleporter t = map.activeTeleporter(x, y, Teleporter.Kind.PARTY);
            Dungeon.Location to = t == null ? null : map.destination(t);
            if (to == null) {
                break;
            }
            turnTo(t.turn(facing));
            boolean spinner = to.map() == map && to.x() == x && to.y() == y;
            result = result.and(spinner ? DungeonMap.StepResult.NOTHING : moveTo(to.map(), to.x(), to.y()))
                    .and(new DungeonMap.StepResult(false, t.audible(), false, false, true, null));
            if (spinner) {
                break; // a teleporter onto itself only turns the party
            }
        }
        return result;
    }

    // ---- upkeep -------------------------------------------------------------

    /**
     * What a game tick did: whether anything visible changed, the damage
     * each member took (indexed like {@link #members()}, or null), the DM
     * sounds the creatures made, and whether a sensor under one clicked.
     */
    public record Tick(boolean changed, int[] damage, List<Integer> sounds, boolean click) {
        public static final Tick NOTHING = new Tick(false, null, List.of(), false);

        public Tick(boolean changed, int[] damage) {
            this(changed, damage, List.of(), false);
        }
    }

    /** The game clock: game ticks since the start. */
    public long time() {
        return time;
    }

    /**
     * Advances DM's game clock by one tick. Every {@link Upkeep#PERIOD} ticks
     * each living champion gets hungrier and thirstier, and regains stamina,
     * mana and health ({@link Upkeep#applyTimeEffects}). Returns whether
     * anything changed, and the damage each member took (from stamina spent
     * below zero), indexed like {@link #members()}.
     */
    public Tick tick() {
        time++;
        boolean burnt = time % Light.BURN_PERIOD == 0 && burnTorches();
        for (DungeonMap m : dungeon.maps()) {
            m.reenableGenerators(time);
        }
        boolean spells = tickPartySpells();
        boolean smoked = Flight.tickExplosions(this);
        boolean landed = Flight.tick(this);
        boolean enabled = enableActions();
        CreatureAI.Outcome creatures = dungeon.creatures().tick(this);
        boolean turned = faceAttackers();
        int[] damage = add(creatures.damage(), tickPoison());
        boolean changed = burnt || spells || smoked || enabled || turned || creatures.changed() || damage != null;
        if (time % (sleeping ? Upkeep.SLEEPING_PERIOD : Upkeep.PERIOD) == 0 && !members.isEmpty()) {
            fadeScents();
            int[] upkeep = new int[members.size()];
            boolean hurt = false;
            for (int i = 0; i < members.size(); i++) {
                Champion c = members.get(i);
                if (c.health() > 0) {
                    upkeep[i] = c.takeDamage(Upkeep.applyTimeEffects(c, time, lastMove, sleeping, difficulty(), random));
                    hurt |= upkeep[i] > 0;
                    if (!sleeping && c.facing() != facing && lastCreatureAttackTime < time - 60) {
                        c.face(facing); // F331: with no attack for a while, the champion turns back
                        c.clearMaxDamageReceived();
                    }
                }
            }
            damage = add(damage, hurt ? upkeep : null);
            changed = true;
        }
        boolean click = creatures.click() || landed;
        if (!changed && creatures.sounds().isEmpty() && !click) {
            return Tick.NOTHING;
        }
        return new Tick(changed, damage, List.copyOf(creatures.sounds()), click);
    }

    /** Adds two damage arrays (either may be null). */
    private static int[] add(int[] a, int[] b) {
        if (a == null) {
            return b;
        }
        if (b != null) {
            for (int i = 0; i < Math.min(a.length, b.length); i++) {
                a[i] += b[i];
            }
        }
        return a;
    }

    /** The hand slots DM scans for torches, in its order: action hand, then ready hand. */
    private static final Slot[] HANDS = {Slot.ACTION_HAND, Slot.READY_HAND};

    /** F338: every torch in a champion's hand loses a charge. Returns whether any did. */
    private boolean burnTorches() {
        boolean changed = false;
        for (Champion c : members) {
            for (Slot hand : HANDS) {
                Item item = c.items().get(hand);
                if (Light.isTorch(item) && item.charges() > 0) {
                    c.replace(hand, item.withCharges(item.charges() - 1));
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * Which of DM's six dungeon palettes the view is drawn with, 0 (bright)
     * to {@link Light#DARKEST}: a difficulty-0 map (Level 1) is always lit;
     * elsewhere the light comes from torches in the champions' hands,
     * Illumulets worn on their necks and light spells (F337).
     */
    public int paletteIndex() {
        if (map.difficulty() == 0) {
            return 0;
        }
        List<Item> hands = new ArrayList<>();
        int magical = magicalLight;
        for (Champion c : members) {
            for (Slot hand : HANDS) {
                hands.add(c.items().get(hand));
            }
            if (Light.isIllumulet(c.items().get(Slot.NECK))) {
                magical += Light.illumulet();
            }
        }
        return Light.palette(Light.amount(hands, magical));
    }

    /**
     * DM's F366: every move attempt, blocked or not, tires each living
     * champion by 1, or more when heavily laden. A champion with no stamina
     * left is hurt instead.
     */
    private void payForStep() {
        for (Champion c : members) {
            if (c.health() > 0) {
                c.takeDamage(c.decrementStamina(Upkeep.stepCost(load(c), c.maxLoad())));
            }
        }
    }

    /** A champion's load, counting the item on the pointer for the leader as DM does. */
    public int load(Champion c) {
        int load = c.load();
        if (held != null && leader() == c) {
            load += held.weight();
        }
        return load;
    }

    /** DM's leader, whose hand is the pointer: the first living member, or null. */
    public Champion leader() {
        for (Champion c : members) {
            if (c.health() > 0) {
                return c;
            }
        }
        return null;
    }

    // ---- death --------------------------------------------------------------

    /** Champions already laid to rest; their bones lie where they fell. */
    private final List<Champion> buried = new ArrayList<>();

    /** Junk type of the bones a dead champion leaves (DM's C05_JUNK_BONES). */
    static final int BONES = 5;

    /**
     * DM's F318: the order a dead champion's things fall in, so that the hands
     * end up on top of the pile. (DM's quiver and backpack rows mapped to ours.)
     */
    private static final Slot[] DROP_ORDER = {
            Slot.FEET, Slot.LEGS, Slot.QUIVER_4, Slot.QUIVER_2, Slot.QUIVER_3, Slot.QUIVER_1,
            Slot.POUCH_2, Slot.POUCH_1, Slot.TORSO,
            Slot.BACKPACK_1, Slot.BACKPACK_10, Slot.BACKPACK_11, Slot.BACKPACK_12, Slot.BACKPACK_13,
            Slot.BACKPACK_14, Slot.BACKPACK_15, Slot.BACKPACK_16, Slot.BACKPACK_17,
            Slot.BACKPACK_2, Slot.BACKPACK_3, Slot.BACKPACK_4, Slot.BACKPACK_5, Slot.BACKPACK_6,
            Slot.BACKPACK_7, Slot.BACKPACK_8, Slot.BACKPACK_9,
            Slot.NECK, Slot.HEAD, Slot.READY_HAND, Slot.ACTION_HAND};

    public boolean isDead(Champion c) {
        return c.health() == 0;
    }

    /**
     * DM's F319 for every member whose health has run out since the last
     * call: everything they carried falls onto their cell of the party's
     * square, their bones on top (the bones remember which member they were,
     * as DM's do, for a resurrection at an altar later), and they leave the
     * formation. Returns the newly dead.
     */
    public List<Champion> bury() {
        List<Champion> dead = new ArrayList<>();
        for (Champion c : members) {
            if (c.health() > 0 || buried.contains(c)) {
                continue;
            }
            buried.add(c);
            dead.add(c);
            int position = positionOf(c);
            int cell = Direction.fromIndex(Math.max(position, 0) + facing.ordinal()).ordinal();
            for (Slot slot : DROP_ORDER) {
                Item item = c.take(slot);
                if (item != null) {
                    map.dropItem(x, y, cell, item);
                }
            }
            map.dropItem(x, y, cell, ItemCatalog.item(Item.Category.JUNK, BONES, members.indexOf(c)));
            c.setSymbols("", 0, 0); // F319: a dead champion's spell is forgotten
            if (position >= 0) {
                positions[position] = null;
            }
        }
        return dead;
    }

    /** True once every member of a party that had any is dead: the game is over. */
    public boolean allDead() {
        return !members.isEmpty() && leader() == null;
    }

    /**
     * Puts the held item in {@code c}'s mouth (DM's F349): food is eaten, a
     * waterskin loses a draught, a potion leaves an empty flask. Returns
     * false, changing nothing, if the item can't be eaten or drunk.
     */
    public boolean feed(Champion c) {
        if (held == null || c.health() == 0) {
            return false;
        }
        Item before = held;
        int shield = c.shieldDefense();
        Item after = Upkeep.consume(c, before, random);
        if (after == before) {
            return false;
        }
        held = after;
        int gained = c.shieldDefense() - shield;
        if (gained > 0) { // a YA potion: DM's event 72 takes it away after its square in ticks
            addPartySpell(new Magic.PartySpell(time + (long) gained * gained, Magic.CHAMPION_SHIELD, gained,
                    members.indexOf(c)));
        }
        return true;
    }

    /**
     * Every living champion drinks their fill from a fountain (an addition
     * to DM, where fountains only refill waterskins and flasks). Returns
     * false if nobody could drink.
     */
    public boolean drinkFromFountain() {
        boolean drank = false;
        for (Champion c : members) {
            if (c.health() > 0) {
                c.setWater(Champion.MAX_FOOD);
                drank = true;
            }
        }
        return drank;
    }

    /** Fall damage for each champion, indexed like {@link #members()}. */
    private int[] fall() {
        int[] damage = new int[members.size()];
        int half = FALL_ATTACK / 2;
        for (int i = 0; i < damage.length; i++) {
            if (members.get(i).health() > 0) {
                damage[i] = members.get(i).takeDamage(half + random.nextInt(half));
            }
        }
        return damage;
    }
}
