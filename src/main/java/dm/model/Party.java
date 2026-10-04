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
     * How many squares a thrown item flies. In DM it depends on the
     * thrower's strength and the item's weight, which aren't modelled yet.
     */
    public static final int THROW_RANGE = 4;
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
        if (isFull() || mirror.taken()) {
            return false;
        }
        Champion c = mirror.champion();
        members.add(c);
        c.setFood(1500 + random.nextInt(256)); // DM's F280
        c.setWater(1500 + random.nextInt(256));
        for (int p = 0; p < MAX_MEMBERS; p++) {
            if (positions[p] == null) {
                positions[p] = mirror.champion();
                break;
            }
        }
        mirror.markTaken();
        return true;
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

    public void turnLeft() {
        facing = facing.turnLeft();
    }

    public void turnRight() {
        facing = facing.turnRight();
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
            facing = exit.facing();
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
            scents.remove(0);
        }
        if (!scents.isEmpty()) {
            addScentStrength(from, fromX, fromY, (int) (time - lastPartyMoveTime), false);
        }
        lastPartyMoveTime = time;
        scents.add(new Scent(to, toX, toY));
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
                    scents.remove(0);
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
            facing = t.turn(facing);
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
        boolean smoked = false;
        for (DungeonMap m : dungeon.maps()) {
            m.reenableGenerators(time);
            smoked |= m.tickSmoke() && m == map;
        }
        CreatureAI.Outcome creatures = dungeon.creatures().tick(this);
        int[] damage = add(creatures.damage(), tickPoison());
        boolean changed = burnt || smoked || creatures.changed() || damage != null;
        if (time % Upkeep.PERIOD == 0 && !members.isEmpty()) {
            fadeScents();
            int[] upkeep = new int[members.size()];
            boolean hurt = false;
            for (int i = 0; i < members.size(); i++) {
                Champion c = members.get(i);
                if (c.health() > 0) {
                    upkeep[i] = c.takeDamage(Upkeep.applyTimeEffects(c, time, lastMove));
                    hurt |= upkeep[i] > 0;
                }
            }
            damage = add(damage, hurt ? upkeep : null);
            changed = true;
        }
        if (!changed && creatures.sounds().isEmpty() && !creatures.click()) {
            return Tick.NOTHING;
        }
        return new Tick(changed, damage, List.copyOf(creatures.sounds()), creatures.click());
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
     * elsewhere the light comes from torches in the champions' hands and
     * Illumulets worn on their necks (F337).
     */
    public int paletteIndex() {
        if (map.difficulty() == 0) {
            return 0;
        }
        List<Item> hands = new ArrayList<>();
        int magical = 0;
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
        Item after = Upkeep.consume(c, before, random);
        if (after == before) {
            return false;
        }
        held = after;
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
