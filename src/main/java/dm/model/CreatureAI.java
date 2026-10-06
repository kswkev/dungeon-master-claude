package dm.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * DM's creature behaviour, ported from ReDMCSB's GROUP.C (F0175-F0209, with
 * F0207 attacks and F0230 damage). It runs on DM's own timeline: every group
 * has events waiting for a game tick, and processing one decides what the
 * group does and when its next event comes.
 * <ul>
 *   <li>37, update group behaviour: a wandering group looks for the party,
 *       sniffs for it (or its scent trail), or walks somewhere at random;
 *       an approaching group runs at a party it can see, or walks to where
 *       it saw it last; a fleeing group runs away.</li>
 *   <li>38-41, update creature behaviour: an attacking creature turns to the
 *       party, steps to a front cell of its square, and strikes.</li>
 *   <li>32-36, update aspect: the pictures change (attack pose, mirroring,
 *       a little jitter) between behaviour updates.</li>
 *   <li>29-31, reactions: the party bumped into the group, or a closing door
 *       or a projectile hurt it.</li>
 * </ul>
 * Only groups on the party's map think; a group elsewhere just takes a
 * random step now and then, as in DM. The original's labels survive as the
 * states of {@link #processEvent}, so it can be checked against the source.
 *
 * <p>The champions' side of a fight ({@link Combat}, {@link Flight}) comes
 * here to hurt creatures (F190, with DM's drops and fear) and to frighten
 * them (F401).
 *
 * <p>Not yet: creature spells and projectiles (creatures that cast fight in
 * melee when adjacent, and approach otherwise), fluxcages, invisibility,
 * freeze life and sleeping.
 */
public final class CreatureAI implements Serializable {

    private static final long serialVersionUID = 1L;

    // DM's event types.
    static final int REACTION_DANGER = 29;
    static final int REACTION_ADJACENT = 31;
    static final int ASPECT_GROUP = 32;
    static final int ASPECT_CREATURE_0 = 33;
    static final int BEHAVIOUR_GROUP = 37;
    static final int BEHAVIOUR_CREATURE_0 = 38;
    static final int BEHAVIOUR_CREATURE_3 = 41;

    /** Reactions passed straight to {@link #react} (DM's CM1-CM3). */
    public static final int PARTY_ADJACENT = -1;
    public static final int HIT_BY_PROJECTILE = -2;
    public static final int DANGER_ON_SQUARE = -3;

    /** DM's G024: wound probability index to the body part wounded (feet, legs, torso, head). */
    private static final int[] WOUND_MASKS = {32, 16, 8, 4};
    /** DM's G050: how much a shield protects each body part. */
    private static final int[] WOUND_DEFENSE_FACTOR = {5, 5, 4, 6, 3, 1};
    /** DM's G023: the cells a creature attacks, by attack direction and side. */
    private static final int[][] ATTACK_ORDER = {
            {0, 1, 3, 2}, {1, 0, 2, 3}, {1, 2, 0, 3}, {2, 1, 3, 0},
            {3, 2, 0, 1}, {2, 3, 1, 0}, {0, 3, 1, 2}, {3, 0, 2, 1}};
    /** DM's door info attribute: creatures can see through the door (G254, by door design). */
    static final int[] DOOR_SEE_THROUGH = {1, 0, 0, 1};

    /** DM's sounds (sound index, as in its table). */
    public static final int SOUND_BUZZ = 17;
    public static final int SOUND_CHAMPION_0_DAMAGED = 9;
    public static final int SOUND_COMBAT = 16;
    public static final int SOUND_METALLIC_THUD = 0;
    public static final int SOUND_WOODEN_THUD = 4;

    /** A group event waiting on DM's timeline. */
    static final class Event implements Serializable {
        private static final long serialVersionUID = 1L;
        DungeonMap map;
        int x;
        int y;
        int type;
        long time;
        int ticks;
        int priority;
        long order;

        Event(DungeonMap map, int x, int y) {
            this.map = map;
            this.x = x;
            this.y = y;
        }
    }

    /** What the creatures did this tick, for the screen: who got hurt and what was heard. */
    public static final class Outcome {
        boolean changed;
        int[] damage;
        final List<Sounds.Heard> sounds = new ArrayList<>();
        boolean click;

        /** Whether anything the player can see may have changed (a creature moved, turned or struck). */
        public boolean changed() {
            return changed;
        }

        /** Damage per member, indexed like {@link Party#members()}, or null. */
        public int[] damage() {
            return damage;
        }

        /** The DM sounds the party heard, in order, loud or soft. */
        public List<Sounds.Heard> sounds() {
            return sounds;
        }

        /** Whether an audible floor sensor clicked under a creature. */
        public boolean click() {
            return click;
        }
    }

    private final Dungeon dungeon;
    private final List<Event> events = new ArrayList<>();
    private long order;

    // Per-call context (DM's globals).
    private transient Party party;
    private transient Random random;
    private transient long now;
    private transient Outcome out;
    private transient DungeonMap map;
    private transient int groupX;
    private transient int groupY;
    private transient int distanceToParty;
    private transient int primaryDirToParty;
    private transient int secondaryDirToParty;
    /** DM's G363, set as a side effect of {@link #directionsTo}. */
    private transient int secondaryDir;
    private transient boolean[] testedDirections;
    private transient boolean blockedByParty;
    private transient long lastDirectionSetTime = -1;
    private transient Group lastDirectionSetGroup;

    CreatureAI(Dungeon dungeon) {
        this.dungeon = dungeon;
    }

    private void begin(Party party) {
        this.party = party;
        this.random = party.random();
        this.now = party.time();
        if (out == null) {
            out = new Outcome();
        }
    }

    /**
     * Hands over what the creatures did since the last tick. Only
     * {@link #tick} calls it: what reactions, doors and generators do
     * between ticks is kept and reported with the next tick.
     */
    private Outcome finish() {
        Outcome done = out;
        out = null;
        return done;
    }

    private int rnd(int n) {
        return n <= 0 ? 0 : random.nextInt(n);
    }

    // ---- the timeline --------------------------------------------------------

    private void addEvent(Event e) {
        e.order = order++;
        events.add(e);
    }

    /** DM's F208: schedules a behaviour event, or the aspect event before it if that comes first. */
    private void addGroupEvent(Event e, long aspectTime) {
        if (aspectTime < e.time) {
            e.type -= 5;
            e.ticks = (int) (e.time - aspectTime);
            e.time = aspectTime;
        } else {
            e.ticks = (int) (aspectTime - e.time);
        }
        addEvent(e);
    }

    /** DM's F181: forgets every event of the group on (x, y). */
    void deleteEvents(DungeonMap m, int x, int y) {
        events.removeIf(e -> e.map == m && e.x == x && e.y == y && e.type >= REACTION_DANGER
                && e.type <= BEHAVIOUR_CREATURE_3);
    }

    int pendingEvents() {
        return events.size();
    }

    /** DM's F234: earlier first; at the same time the higher type, then the higher priority, then the older. */
    private static boolean before(Event a, Event b) {
        if (a.time != b.time) {
            return a.time < b.time;
        }
        if (a.type != b.type) {
            return a.type > b.type;
        }
        if (a.priority != b.priority) {
            return a.priority > b.priority;
        }
        return a.order < b.order;
    }

    /** Runs every group event due by the party's game time. */
    Outcome tick(Party party) {
        begin(party);
        for (int guard = 0; guard < 2000; guard++) {
            Event due = null;
            for (Event e : events) {
                if (e.time <= now && (due == null || before(e, due))) {
                    due = e;
                }
            }
            if (due == null) {
                break;
            }
            events.remove(due);
            processEvent(due.map, due.x, due.y, due.type, due.ticks);
        }
        return finish();
    }

    /**
     * Something happened to the group on (x, y) of the party's map (DM's
     * CM1-CM3): the party walked into it ({@link #PARTY_ADJACENT}), or a
     * closing door hurt it ({@link #DANGER_ON_SQUARE}).
     */
    Outcome react(Party party, DungeonMap m, int x, int y, int reaction) {
        begin(party);
        processEvent(m, x, y, reaction, 0);
        return out;
    }

    /**
     * DM's F195: the party has arrived on {@code m}; every group there
     * becomes active and starts wandering.
     */
    void partyArrived(Party party, DungeonMap m) {
        begin(party);
        for (Group g : m.groups()) {
            deleteEvents(m, g.x(), g.y());
            activate(g);
            startWandering(m, g);
        }
    }

    /** DM's F194: the party has left {@code m}; its groups calm down. */
    void partyLeft(DungeonMap m) {
        for (Group g : m.groups()) {
            deactivate(g);
        }
    }

    /** DM's F183. */
    private void activate(Group g) {
        g.priorX = g.homeX = g.x();
        g.priorY = g.homeY = g.y();
        g.lastMoveTime = now - 127;
        g.face(g.facing());
        for (int i = 0; i < 4; i++) {
            g.setAspect(i, 0);
        }
        aspectUpdateTime(g, -1, false);
    }

    /** DM's F184. */
    private static void deactivate(Group g) {
        g.face(g.facing());
        if (g.behaviour() >= 4) {
            g.setBehaviour(Group.WANDER);
        }
        for (int i = 0; i < 4; i++) {
            g.setAspect(i, 0);
        }
    }

    // ---- Lord Chaos and the fluxcages (GROUP2.C F221-F225) --------------------------

    /** F221: a fluxcage on (x, y), which is never a wall or stairs. */
    private static boolean fluxcageOn(DungeonMap m, int x, int y) {
        SquareType type = m.get(x, y).type();
        return type != SquareType.WALL && type != SquareType.STAIRS
                && m.explosionsAt(x, y).stream().anyMatch(e -> e.type() == Explosion.FLUXCAGE);
    }

    /** F222: Lord Chaos's group on (x, y), or null. */
    private static Group lordChaos(DungeonMap m, int x, int y) {
        Group g = m.groupAt(x, y);
        return g != null && g.type() == CreatureType.LORD_CHAOS ? g : null;
    }

    /**
     * F224 after a fluxcage appears on (x, y): if Lord Chaos stands next to
     * it, the fluxcages on his other three sides are counted, and with two
     * of them (three in all) he senses danger and moves away.
     */
    void fluxcaged(Party party, DungeonMap m, int x, int y) {
        begin(party);
        for (Direction d : new Direction[] {Direction.NORTH, Direction.WEST, Direction.EAST, Direction.SOUTH}) {
            int lx = x + d.dx;
            int ly = y + d.dy;
            if (lordChaos(m, lx, ly) == null) {
                continue;
            }
            int count = 0;
            for (Direction side : Direction.values()) {
                if (lx + side.dx != x || ly + side.dy != y) {
                    count += fluxcageOn(m, lx + side.dx, ly + side.dy) ? 1 : 0;
                }
            }
            if (count == 2) {
                processEvent(m, lx, ly, DANGER_ON_SQUARE, 0);
            }
            return;
        }
    }

    /**
     * DM's F225, the Firestaff's FUSE on (x, y) of {@code m}: a harm
     * non-material blast at 255. If Lord Chaos stands there, each side
     * without a fluxcage is tried in a random order; the first open one (a
     * corridor, teleporter, pit or door) lets him escape there. Walls and
     * stairs hold him like fluxcages. Returns true when no side is open: he
     * is caught, and the fuse sequence begins.
     */
    boolean fuse(Party party, DungeonMap m, int x, int y) {
        begin(party);
        if (x < 0 || y < 0 || x >= m.width() || y >= m.height()) {
            return false;
        }
        Flight.explode(party, m, Explosion.HARM_NON_MATERIAL, 255, x, y, Group.CENTRED);
        Group g = lordChaos(m, x, y);
        if (g == null) {
            return false;
        }
        boolean[] cages = {fluxcageOn(m, x - 1, y), fluxcageOn(m, x + 1, y),
                fluxcageOn(m, x, y - 1), fluxcageOn(m, x, y + 1)};
        int count = 0;
        for (boolean c : cages) {
            count += c ? 1 : 0;
        }
        while (count++ < 4) {
            int nx = x;
            int ny = y;
            int side = rnd(4);
            for (int tries = 0; tries < 4; tries++) {
                if (!cages[side]) {
                    cages[side] = true;
                    switch (side) {
                        case 0 -> nx--;
                        case 1 -> nx++;
                        case 2 -> ny--;
                        default -> ny++;
                    }
                    break;
                }
                side = (side + 1) & 3;
            }
            SquareType type = m.get(nx, ny).type();
            if (type == SquareType.CORRIDOR || type == SquareType.TELEPORTER || type == SquareType.PIT
                    || type == SquareType.DOOR) {
                if (moveGroup(m, g, x, y, nx, ny) == MOVED) {
                    deleteEvents(m, x, y);
                    startWandering(m, g);
                }
                return false;
            }
        }
        return true;
    }

    /** DM's F180. */
    private void startWandering(DungeonMap m, Group g) {
        if (g.behaviour() >= 4) {
            g.setBehaviour(Group.WANDER);
        }
        Event e = new Event(m, g.x(), g.y());
        e.time = now + 1;
        e.type = BEHAVIOUR_GROUP;
        e.priority = CreatureType.IMMOBILE - g.type().movementTicks();
        addEvent(e);
    }

    // ---- F209: processing an event ---------------------------------------------

    // The labels of ReDMCSB's F209, as states.
    private static final int START = 0;
    private static final int SET_ATTACK = 44;
    private static final int SET_APPROACH = 54;
    private static final int MOVE_RANDOM = 58;
    private static final int MOVE_GROUP = 61;
    private static final int AFTER_RANDOM_MOVE = 62;
    private static final int SET_DIRECTION_GROUP = 73;
    private static final int AFTER_SET_DIRECTION = 74;
    private static final int RUN_TOWARD_PARTY = 81;
    private static final int WALK_TOWARD_TARGET = 82;
    private static final int SINGLE_MOVE_TOWARD = 84;
    private static final int SINGLE_MOVE = 85;
    private static final int DOUBLE_MOVE = 89;
    private static final int FLEE = 94;
    private static final int SET_WANDER = 96;
    private static final int NEXT_MOVE_TIME = 133;
    private static final int SET_EVENT_37 = 134;
    private static final int ASPECT_TIME = 135;
    private static final int ADD = 136;

    private void processEvent(DungeonMap m, int ex, int ey, int eventType, int ticks) {
        boolean onPartyMap = m == party.map();
        if (!onPartyMap && eventType != BEHAVIOUR_GROUP && eventType != ASPECT_GROUP
                && eventType != BEHAVIOUR_CREATURE_0 && eventType != ASPECT_CREATURE_0) {
            return;
        }
        Group g = m.groupAt(ex, ey);
        if (g == null) {
            return;
        }
        CreatureType info = g.type();
        map = m;
        Event next = new Event(m, ex, ey);
        next.time = now;
        next.priority = CreatureType.IMMOBILE - info.movementTicks();
        if (!onPartyMap) {
            int dir = rnd(4);
            testedDirections = new boolean[4];
            if (isMovementPossible(g, ex, ey, dir, false)) {
                Direction d = Direction.fromIndex(dir);
                if (moveGroup(m, g, ex, ey, ex + d.dx, ey + d.dy) == STOP) {
                    return;
                }
                next.x = g.x();
                next.y = g.y();
            }
            next.type = BEHAVIOUR_GROUP;
            int maps = Math.abs(dungeon.maps().indexOf(m) - dungeon.maps().indexOf(party.map()));
            next.time += Math.max(maps << 4, info.movementTicks() << 1);
            addEvent(next);
            return;
        }
        boolean archenemy = info.archenemy();
        int ticksSinceLastMove = (int) ((now - g.lastMoveTime) & 0xFF);
        int movementTicks = info.movementTicks() == CreatureType.IMMOBILE ? 100 : info.movementTicks();
        if (party.lifeFrozen() && !archenemy) { // DM 1.2: frozen, it ignores reactions and tries again in 4 ticks
            if (eventType < 0) {
                return;
            }
            next.type = eventType;
            next.ticks = ticks;
            next.time += 4;
            addEvent(next);
            return;
        }
        if (eventType < 0) { // a reaction: it comes 1 tick later, or later for a creature that just moved
            next.type = eventType + ASPECT_GROUP;
            int delay = ((movementTicks + 2) >> 2) - ticksSinceLastMove;
            next.time += eventType == PARTY_ADJACENT || delay < 1 ? 1 : delay;
            addEvent(next);
            return;
        }
        int behaviour = g.behaviour();
        int last = g.count() - 1; // DM's group count
        CreatureType.Size size = info.size();
        int partyX = party.x();
        int partyY = party.y();
        int distX = Math.abs(ex - partyX);
        int distY = Math.abs(ey - partyY);
        groupX = ex;
        groupY = ey;
        testedDirections = new boolean[4];
        distanceToParty = distX + distY;
        primaryDirToParty = directionsTo(ex, ey, partyX, partyY);
        secondaryDirToParty = secondaryDir;
        long nextAspectTime = 0;
        boolean notUpdateBehaviour = true;
        boolean newDirFound = false;
        boolean approachAfterReaction = false;
        boolean allowFake = false;
        int distanceToVisibleParty = 0;
        int primary = 0;
        int direction = 0;
        int destX = 0;
        int destY = 0;
        int targetX = 0;
        int targetY = 0;
        int creature;

        int state = START;
        while (true) {
            switch (state) {
                case START -> {
                    if (eventType <= REACTION_ADJACENT) { // reactions 29-31
                        eventType -= ASPECT_GROUP;
                        if (eventType == PARTY_ADJACENT) {
                            if (behaviour != Group.ATTACK && behaviour != Group.FLEE) {
                                deleteEvents(m, ex, ey);
                                state = SET_ATTACK;
                                continue;
                            }
                            g.targetX = partyX;
                            g.targetY = partyY;
                            return;
                        }
                        if (eventType == HIT_BY_PROJECTILE) {
                            if (behaviour == Group.ATTACK || behaviour == Group.FLEE) {
                                return;
                            }
                            if (rnd(4) != 0) {
                                if (distanceToVisibleParty(g, -1, ex, ey) == 0) {
                                    newDirFound = false;
                                    state = SET_DIRECTION_GROUP;
                                    continue;
                                }
                                if (rnd(4) != 0) {
                                    return;
                                }
                            }
                        }
                        approachAfterReaction = behaviour == Group.ATTACK;
                        newDirFound = false;
                        state = MOVE_RANDOM;
                        continue;
                    }
                    if (eventType < BEHAVIOUR_GROUP) { // aspect events 32-36
                        next.type = eventType + 5;
                        if (distanceToVisibleParty(g, -1, ex, ey) != 0) {
                            if (behaviour != Group.ATTACK && behaviour != Group.FLEE) {
                                if (distanceToParty <= 1) {
                                    state = SET_ATTACK;
                                    continue;
                                }
                                if (behaviour == Group.WANDER) {
                                    state = SET_APPROACH;
                                    continue;
                                }
                            }
                            g.targetX = partyX;
                            g.targetY = partyY;
                        }
                        if (behaviour == Group.ATTACK) {
                            int i = eventType - ASPECT_CREATURE_0;
                            nextAspectTime = aspectUpdateTime(g, i, i >= 0 && g.attacking(i));
                            state = ADD;
                            continue;
                        }
                        if (distX > 3 || distY > 3) {
                            nextAspectTime = now + ((info.animationTicks() >> 4) & 15);
                            state = ADD;
                            continue;
                        }
                        state = ASPECT_TIME;
                        continue;
                    }
                    notUpdateBehaviour = false; // behaviour events 37-41
                    if (ticks != 0) {
                        nextAspectTime = now;
                    }
                    if (eventType == BEHAVIOUR_GROUP) {
                        if (behaviour == Group.WANDER) {
                            distanceToVisibleParty = distanceToVisibleParty(g, -1, ex, ey);
                            if (distanceToVisibleParty != 0) {
                                state = distanceToVisibleParty <= attackRange(info) && (distX == 0 || distY == 0)
                                        ? SET_ATTACK : SET_APPROACH;
                                continue;
                            }
                            primary = smelledPartyDirectionOrdinal(info, ex, ey);
                            if (primary != 0) {
                                primary--;
                                allowFake = false;
                                state = SINGLE_MOVE;
                                continue;
                            }
                            newDirFound = false;
                            state = rnd(2) != 0 ? MOVE_RANDOM : AFTER_RANDOM_MOVE;
                            continue;
                        }
                        if (behaviour == Group.APPROACH) {
                            distanceToVisibleParty = distanceToVisibleParty(g, -1, ex, ey);
                            if (distanceToVisibleParty != 0) {
                                state = distanceToVisibleParty <= attackRange(info) && (distX == 0 || distY == 0)
                                        ? SET_ATTACK : RUN_TOWARD_PARTY;
                            } else {
                                state = WALK_TOWARD_TARGET;
                            }
                            continue;
                        }
                        state = behaviour == Group.FLEE ? FLEE : NEXT_MOVE_TIME;
                        continue;
                    }
                    // events 38-41: one creature's behaviour
                    if (behaviour == Group.FLEE) {
                        if (last > 0) {
                            stopAttacking(g, m, ex, ey);
                        }
                        state = FLEE;
                        continue;
                    }
                    creature = eventType - BEHAVIOUR_CREATURE_0;
                    if (g.attacking(creature)) {
                        nextAspectTime = aspectUpdateTime(g, creature, false);
                        int t = info.attackTicks();
                        next.time += t + rnd(4) - 1;
                        if (t > 15) {
                            next.time += rnd(8) - 2;
                        }
                    } else {
                        if (creature > last) {
                            return;
                        }
                        primary = primaryDirToParty;
                        distanceToVisibleParty = distanceToVisibleParty(g, creature, ex, ey);
                        if (distanceToVisibleParty != 0) {
                            g.targetX = partyX;
                            g.targetY = partyY;
                        }
                        int criteria;
                        if (last == 0 && size != CreatureType.Size.FULL && ((criteria = rnd(65536)) & 0xC0) == 0) {
                            if (!g.centred()) {
                                if ((criteria & 0x38) != 0) {
                                    g.setCells(Group.CENTRED); // 7/8: to the middle of the square
                                } else {
                                    criteria = ((g.cells() & 3) + ((criteria & 1) != 0 ? 1 : -1)) & 3;
                                }
                                out.changed = true;
                            }
                            if ((criteria & 0x38) == 0 && distanceToVisibleParty != 1
                                    && size == CreatureType.Size.QUARTER) {
                                g.setCells(criteria & 3);
                                out.changed = true;
                            }
                        }
                        if (distanceToVisibleParty != 0
                                && (info.sideAttack() || g.facing(creature).ordinal() == primary)) {
                            int range = attackRange(info);
                            if (distanceToVisibleParty <= range && (distX == 0 || distY == 0) && range <= rnd(16) + 1) {
                                int cell;
                                if (range == 1
                                        && (!info.prefersBackRow() || rnd(4) == 0 || !info.attacksAnyChampion())
                                        && size == CreatureType.Size.QUARTER && !g.centred()
                                        && (cell = g.cellOf(creature)) != primary && cell != ((primary + 1) & 3)) {
                                    // Not on a cell facing the party: step across the square first.
                                    if (last == 0 && rnd(2) != 0) {
                                        g.setCells(Group.CENTRED);
                                    } else {
                                        cell += (primary & 1) == (cell & 1) ? -1 : 1;
                                        cell &= 3;
                                        if (creatureOrdinalInCell(g, cell) == 0
                                                || rnd(2) != 0 && creatureOrdinalInCell(g, cell = (cell + 2) & 3) == 0) {
                                            g.setCell(creature, cell);
                                        }
                                    }
                                    out.changed = true;
                                    next.time += Math.max(1, (info.movementTicks() >> 1) + rnd(2));
                                    next.type = eventType;
                                    state = ASPECT_TIME;
                                    continue;
                                }
                                nextAspectTime = aspectUpdateTime(g, creature, creatureAttacks(g, ex, ey, creature,
                                        distanceToVisibleParty));
                                next.time += (info.animationTicks() & 15) + rnd(2);
                            } else {
                                g.setBehaviour(Group.APPROACH);
                                if (last > 0) {
                                    stopAttacking(g, m, ex, ey);
                                }
                                state = RUN_TOWARD_PARTY;
                                continue;
                            }
                        } else if (distanceToVisibleParty(g, -1, ex, ey) != 0) {
                            g.targetX = partyX;
                            g.targetY = partyY;
                            setDirection(g, primary, creature, last > 0 && size == CreatureType.Size.HALF);
                            next.time += 2;
                            nextAspectTime = next.time;
                        } else {
                            g.setBehaviour(Group.APPROACH);
                            if (last > 0) {
                                stopAttacking(g, m, ex, ey);
                            }
                            state = WALK_TOWARD_TARGET;
                            continue;
                        }
                    }
                    next.type = eventType;
                    state = ADD;
                }
                case SET_ATTACK -> {
                    if (eventType == HIT_BY_PROJECTILE) {
                        deleteEvents(m, ex, ey);
                    }
                    g.targetX = partyX;
                    g.targetY = partyY;
                    g.setBehaviour(Group.ATTACK);
                    for (int i = last; i >= 0; i--) {
                        Event e = new Event(m, ex, ey);
                        e.priority = next.priority;
                        if (g.facing(i).ordinal() != primaryDirToParty && (i == 0 || rnd(2) == 0)) {
                            setDirection(g, primaryDirToParty, i, last > 0 && size == CreatureType.Size.HALF);
                            e.time = now + rnd(4) + 2; // time to turn
                        } else {
                            e.time = now + 1;
                        }
                        if (notUpdateBehaviour) {
                            e.time += Math.min((info.attackTicks() >> 1) + rnd(4), ticks);
                        }
                        e.type = BEHAVIOUR_CREATURE_0 + i;
                        addGroupEvent(e, aspectUpdateTime(g, i, false));
                    }
                    out.changed = true;
                    return;
                }
                case SET_APPROACH -> {
                    g.setBehaviour(Group.APPROACH);
                    g.targetX = partyX;
                    g.targetY = partyY;
                    next.time += 1;
                    state = SET_EVENT_37;
                }
                case MOVE_RANDOM -> {
                    direction = rnd(4);
                    int reference = direction;
                    state = AFTER_RANDOM_MOVE;
                    do {
                        Direction d = Direction.fromIndex(direction);
                        destX = ex + d.dx;
                        destY = ey + d.dy;
                        if ((g.priorX != destX || g.priorY != destY || rnd(4) == 0) // 1/4: back where it came from
                                && isMovementPossible(g, ex, ey, direction, false)) {
                            state = MOVE_GROUP;
                            break;
                        }
                        if (blockedByParty) {
                            if (eventType != DANGER_ON_SQUARE && (g.behaviour() != Group.FLEE
                                    || firstPossibleDirectionOrdinal(g, ex, ey, false) == 0 || rnd(2) != 0)) {
                                state = SET_ATTACK;
                                break;
                            }
                            g.targetX = partyX;
                            g.targetY = partyY;
                        }
                        direction = (direction + 1) & 3;
                    } while (direction != reference);
                }
                case MOVE_GROUP -> {
                    int wait = (movementTicks >> 1) - ticksSinceLastMove;
                    newDirFound = wait <= 0;
                    if (newDirFound) {
                        int moved = moveGroup(m, g, ex, ey, destX, destY);
                        if (moved == STOP) {
                            return;
                        }
                        if (moved == BLOCKED) { // DM retries the move 5 ticks later
                            next.type = BEHAVIOUR_GROUP;
                            next.time = now + 5;
                            addEvent(next);
                            return;
                        }
                        next.x = g.x();
                        next.y = g.y();
                        g.priorX = ex;
                        g.priorY = ey;
                        g.lastMoveTime = now;
                    } else {
                        movementTicks = wait;
                        ticksSinceLastMove = -1;
                    }
                    state = AFTER_RANDOM_MOVE;
                }
                case AFTER_RANDOM_MOVE -> {
                    if (!newDirFound && ticksSinceLastMove != -1 && archenemy
                            && (eventType == DANGER_ON_SQUARE || rnd(4) == 0)) {
                        primary = rnd(4);
                        secondaryDir = (primary + 1) & 3;
                        state = DOUBLE_MOVE;
                        continue;
                    }
                    if (newDirFound || (rnd(4) == 0 || distanceToVisibleParty <= info.smellRange())
                            && eventType != DANGER_ON_SQUARE) {
                        state = SET_DIRECTION_GROUP;
                    } else {
                        state = AFTER_SET_DIRECTION;
                    }
                }
                case SET_DIRECTION_GROUP -> {
                    if (!newDirFound && ticksSinceLastMove >= 0) { // look around for the party
                        direction = rnd(4);
                    }
                    setDirectionGroup(g, direction, last, size);
                    state = AFTER_SET_DIRECTION;
                }
                case AFTER_SET_DIRECTION -> {
                    if (eventType == DANGER_ON_SQUARE || eventType == HIT_BY_PROJECTILE) {
                        if (!newDirFound) {
                            return;
                        }
                        if (approachAfterReaction) {
                            g.setBehaviour(Group.APPROACH);
                        }
                        stopAttacking(g, m, ex, ey);
                    }
                    state = NEXT_MOVE_TIME;
                }
                case RUN_TOWARD_PARTY -> {
                    movementTicks = (movementTicks + 1) >> 1; // running takes half the time
                    targetX = g.targetX = partyX;
                    targetY = g.targetY = partyY;
                    allowFake = true;
                    state = SINGLE_MOVE_TOWARD;
                }
                case WALK_TOWARD_TARGET -> {
                    targetX = g.targetX;
                    targetY = g.targetY;
                    if (ex == targetX && ey == targetY) { // got there, but the party has gone
                        newDirFound = false;
                        g.setBehaviour(Group.WANDER);
                        state = SET_DIRECTION_GROUP;
                        continue;
                    }
                    allowFake = true;
                    state = SINGLE_MOVE_TOWARD;
                }
                case SINGLE_MOVE_TOWARD -> {
                    primary = directionsTo(ex, ey, targetX, targetY);
                    state = SINGLE_MOVE;
                }
                case SINGLE_MOVE -> {
                    if (isMovementPossible(g, ex, ey, direction = primary, allowFake)
                            || isMovementPossible(g, ex, ey, direction = secondaryDir, allowFake && rnd(2) != 0)
                            || isMovementPossible(g, ex, ey, direction = (direction + 2) & 3, false)
                            || rnd(4) == 0 && isMovementPossible(g, ex, ey, direction = (primary + 2) & 3, false)) {
                        Direction d = Direction.fromIndex(direction);
                        destX = ex + d.dx;
                        destY = ey + d.dy;
                        state = MOVE_GROUP;
                        continue;
                    }
                    if (archenemy) {
                        state = DOUBLE_MOVE;
                        continue;
                    }
                    setDirectionGroup(g, primary, last, size);
                    state = NEXT_MOVE_TIME;
                }
                case DOUBLE_MOVE -> { // Lord Chaos jumps two squares
                    firstPossibleDirectionOrdinal(g, ex, ey, false);
                    if (isDoubleMovePossible(g, ex, ey, direction = primary)
                            || isDoubleMovePossible(g, ex, ey, direction = secondaryDir)) {
                        Direction d = Direction.fromIndex(direction);
                        destX = ex + 2 * d.dx;
                        destY = ey + 2 * d.dy;
                        sound(SOUND_BUZZ, m, destX, destY);
                        state = MOVE_GROUP;
                        continue;
                    }
                    setDirectionGroup(g, primary, last, size);
                    state = NEXT_MOVE_TIME;
                }
                case FLEE -> {
                    allowFake = true;
                    distanceToVisibleParty = distanceToVisibleParty(g, -1, ex, ey);
                    if (distanceToVisibleParty != 0) {
                        targetX = g.targetX = partyX;
                        targetY = g.targetY = partyY;
                    } else {
                        g.delayFleeing = (g.delayFleeing - 1) & 0xFF;
                        if (g.delayFleeing == 0) { // not afraid any more
                            state = SET_WANDER;
                            continue;
                        }
                        if (rnd(2) != 0) {
                            if (firstPossibleDirectionOrdinal(g, ex, ey, false) == 0
                                    && distanceToParty <= 1) {
                                state = SET_WANDER;
                                continue;
                            }
                            targetX = g.homeX; // back home, where it was when the party arrived
                            targetY = g.homeY;
                            state = SINGLE_MOVE_TOWARD;
                            continue;
                        }
                        targetX = g.targetX;
                        targetY = g.targetY;
                    }
                    primary = (directionsTo(ex, ey, targetX, targetY) + 2) & 3;
                    secondaryDir = (secondaryDir + 2) & 3;
                    movementTicks -= movementTicks >> 2;
                    state = SINGLE_MOVE;
                }
                case SET_WANDER -> {
                    newDirFound = false;
                    g.setBehaviour(Group.WANDER);
                    state = SET_DIRECTION_GROUP;
                }
                case NEXT_MOVE_TIME -> {
                    next.time += Math.max(1, rnd(4) + movementTicks - 1);
                    state = SET_EVENT_37;
                }
                case SET_EVENT_37 -> {
                    next.type = BEHAVIOUR_GROUP;
                    state = ASPECT_TIME;
                }
                case ASPECT_TIME -> {
                    if (nextAspectTime == 0) {
                        nextAspectTime = aspectUpdateTime(g, -1, false);
                    }
                    state = ADD;
                }
                case ADD -> {
                    if (notUpdateBehaviour) {
                        next.time += ticks;
                    } else {
                        nextAspectTime += ticks;
                    }
                    if (m.groupAt(next.x, next.y) == g) {
                        addGroupEvent(next, nextAspectTime);
                    }
                    return;
                }
                default -> throw new IllegalStateException("state " + state);
            }
        }
    }

    /** How far the creature attacks from: 1 hand to hand, more for those that cast spells (F207). */
    private static int attackRange(CreatureType info) {
        return info.attackRange();
    }

    // ---- looking, smelling and moving ----------------------------------------

    /**
     * DM's F228: the direction to go from (sx, sy) toward (dx, dy), and in
     * {@link #secondaryDir} the next best one.
     */
    private int directionsTo(int sx, int sy, int dx, int dy) {
        if (sx == dx) {
            secondaryDir = (rnd(65536) & 2) + 1; // east or west
            return sy > dy ? 0 : 2;
        }
        if (sy == dy) {
            secondaryDir = rnd(65536) & 2; // north or south
            return sx > dx ? 3 : 1;
        }
        for (int dir = 0; ; dir++) {
            if (isVisible(dir, sx, sy, dx, dy)) {
                secondaryDir = (dir + 1) & 3;
                if (!isVisible(secondaryDir, sx, sy, dx, dy)) {
                    secondaryDir = (dir + 3) & 3;
                    if (dir != 0 || !isVisible(secondaryDir, sx, sy, dx, dy)) {
                        secondaryDir = ((rnd(65536) & 2) + dir + 1) & 3;
                        return dir;
                    }
                }
                if (rnd(2) != 0) {
                    int swap = secondaryDir;
                    secondaryDir = dir;
                    return swap;
                }
                return dir;
            }
        }
    }

    /**
     * DM's F227: whether (destX, destY) lies in the quarter of the map seen
     * looking {@code dir} from (srcX, srcY). The coordinates are swapped so
     * every direction becomes the test for west.
     */
    private static boolean isVisible(int dir, int srcX, int srcY, int destX, int destY) {
        int t;
        switch (dir) {
            case 2 -> { // south: swap srcX/destY and destX/srcY
                t = srcX;
                srcX = destY;
                destY = t;
                t = destX;
                destX = srcY;
                srcY = t;
            }
            case 1 -> { // east: swap srcX/destX and destY/srcY
                t = srcX;
                srcX = destX;
                destX = t;
                t = destY;
                destY = srcY;
                srcY = t;
            }
            case 0 -> { // north: swap srcX/srcY and destX/destY
                t = srcX;
                srcX = srcY;
                srcY = t;
                t = destX;
                destX = destY;
                destY = t;
            }
            default -> { }
        }
        srcX -= destX - 1;
        srcY -= destY;
        return srcX > 0 && Math.abs(srcY) <= srcX;
    }

    /** DM's F200: how far away the party is if the group (or creature {@code creature}) can see it, else 0. */
    private int distanceToVisibleParty(Group g, int creature, int x, int y) {
        CreatureType info = g.type();
        if (party.invisible() && !info.seesInvisible()) {
            return 0;
        }
        boolean sees = info.sideAttack();
        if (!sees) {
            List<Integer> dirs = new ArrayList<>();
            if (creature < 0) {
                for (int i = g.count() - 1; i >= 0; i--) {
                    int dir = g.facing(i).ordinal();
                    if (!dirs.contains(dir)) {
                        dirs.add(dir);
                    }
                }
            } else {
                dirs.add(g.facing(creature).ordinal());
            }
            for (int i = dirs.size() - 1; i >= 0 && !sees; i--) {
                sees = isVisible(dirs.get(i), x, y, party.x(), party.y());
            }
        }
        if (!sees) {
            return 0;
        }
        int sight = info.sightRange();
        if (!info.nightVision()) {
            sight -= party.paletteIndex() >> 1;
        }
        if (distanceToParty > Math.max(1, sight)) {
            return 0;
        }
        return unblockedDistance(x, y, party.x(), party.y(), true);
    }

    /** DM's F197 (view) and F198 (smell): what stops a creature seeing or smelling through a square. */
    private boolean blocks(int x, int y, boolean view) {
        Square sq = map.get(x, y);
        switch (sq.type()) {
            case WALL -> {
                return true;
            }
            case FAKEWALL -> {
                return (sq.raw() & 0x04) == 0;
            }
            case DOOR -> {
                if (!view) {
                    return false;
                }
                int state = map.doorState(x, y);
                return (state == 3 || state == 4) && DOOR_SEE_THROUGH[map.doorStyle(x, y)] == 0;
            }
            default -> {
                return false;
            }
        }
    }

    /** DM's F199: the distance from source to destination, or 0 if the line between them is blocked. */
    private int unblockedDistance(int sx, int sy, int dx, int dy, boolean view) {
        if (Math.abs(sx - dx) + Math.abs(sy - dy) <= 1) {
            return 1;
        }
        int distX = Math.abs(dx - sx);
        int distY = Math.abs(dy - sy);
        boolean xSmaller = distX < distY;
        boolean equal = distX == distY;
        int px = dx;
        int py = dy;
        int stepX = px - sx > 0 ? -1 : 1;
        int stepY = py - sy > 0 ? -1 : 1;
        int largest;
        int valueC;
        if (xSmaller) {
            largest = py - sy;
            valueC = largest != 0 ? ((px - sx) << 6) / largest : 128;
        } else {
            largest = px - sx;
            valueC = largest != 0 ? ((py - sy) << 6) / largest : 128;
        }
        do {
            if (equal) {
                if (blocks(px + stepX, py, view) && blocks(px, py + stepY, view)
                        || blocks(px = px + stepX, py = py + stepY, view)) {
                    return 0;
                }
            } else {
                int valueA;
                int valueB;
                if (xSmaller) {
                    largest = py - sy;
                    valueA = Math.abs((largest != 0 ? ((px + stepX - sx) << 6) / largest : 128) - valueC);
                    largest = py + stepY - sy;
                    valueB = Math.abs((largest != 0 ? ((px - sx) << 6) / largest : 128) - valueC);
                } else {
                    largest = px + stepX - sx;
                    valueA = Math.abs((largest != 0 ? ((py - sy) << 6) / largest : 128) - valueC);
                    largest = px - sx;
                    valueB = Math.abs((largest != 0 ? ((py + stepY - sy) << 6) / largest : 128) - valueC);
                }
                if (valueA < valueB) {
                    px += stepX;
                } else {
                    py += stepY;
                }
                if (blocks(px, py, view) && (valueA != valueB || blocks(px = px + stepX, py = py - stepY, view))) {
                    return 0;
                }
            }
        } while (Math.abs(px - sx) + Math.abs(py - sy) > 1);
        return Math.abs(sx - dx) + Math.abs(sy - dy);
    }

    /** DM's F201: the direction ordinal (1-4) toward a party the group smells, or 0. */
    private int smelledPartyDirectionOrdinal(CreatureType info, int x, int y) {
        int smell = info.smellRange();
        if (smell == 0) {
            return 0;
        }
        if (((smell + 1) >> 1) >= distanceToParty && unblockedDistance(x, y, party.x(), party.y(), false) != 0) {
            secondaryDir = secondaryDirToParty;
            return primaryDirToParty + 1;
        }
        int scent = party.scentOrdinal(map, x, y);
        if (scent != 0 && party.scentStrength(scent - 1) + rnd(4) > 30 - (smell << 1)) {
            // DM follows the trail to the scent laid after this one.
            int[] to = party.scentAt(scent);
            if (to != null) {
                return directionsTo(x, y, to[0], to[1]) + 1;
            }
        }
        return 0;
    }

    /** DM's F202: whether the group on (x, y) can step {@code dir}. */
    private boolean isMovementPossible(Group g, int x, int y, int dir, boolean allowFake) {
        testedDirections[dir] = true;
        blockedByParty = false;
        CreatureType info = g.type();
        if (info.movementTicks() == CreatureType.IMMOBILE) {
            return false;
        }
        Direction d = Direction.fromIndex(dir);
        int nx = x + d.dx;
        int ny = y + d.dy;
        if (!map.inBounds(nx, ny)) {
            return false;
        }
        Square sq = map.get(nx, ny);
        switch (sq.type()) {
            case WALL, STAIRS -> {
                return false;
            }
            case PIT -> {
                if (map.isPitOpen(nx, ny) && !(sq.pitImaginary() && allowFake) && !info.levitates()) {
                    return false;
                }
            }
            case FAKEWALL -> {
                if ((sq.raw() & 0x04) == 0 && !((sq.raw() & 0x01) != 0 && allowFake)) {
                    return false;
                }
            }
            case TELEPORTER -> {
                Teleporter t = map.teleporterAt(nx, ny);
                if (map.isTeleporterOpen(nx, ny) && info.wariness() >= 10 && t != null
                        && t.moves(Teleporter.Kind.CREATURE)) {
                    Dungeon.Location to = map.destination(t);
                    if (to != null && !to.map().allowsCreature(info)) {
                        return false;
                    }
                }
            }
            default -> { }
        }
        if (info.archenemy()) { // only Lord Chaos is held back by a fluxcage
            for (Explosion e : map.explosionsAt(nx, ny)) {
                if (e.type() == Explosion.FLUXCAGE) {
                    return false;
                }
            }
        }
        if (party.map() == map && party.x() == nx && party.y() == ny) {
            blockedByParty = true;
            return false;
        }
        if (sq.type() == SquareType.DOOR) {
            int state = map.doorState(nx, ny);
            int clearance = map.doorOpensVertically(nx, ny) ? info.height() : 1;
            if (state > clearance && state != DungeonMap.DOOR_BROKEN && !info.nonMaterial()) {
                return false;
            }
        }
        return map.groupAt(nx, ny) == null;
    }

    /** DM's F203: the first direction not yet tried that the group can take, as an ordinal, or 0. */
    private int firstPossibleDirectionOrdinal(Group g, int x, int y, boolean allowFake) {
        for (int dir = 0; dir < 4; dir++) {
            if (!testedDirections[dir] && isMovementPossible(g, x, y, dir, allowFake)) {
                return dir + 1;
            }
        }
        return 0;
    }

    /** DM's F204 (no fluxcages yet). */
    private boolean isDoubleMovePossible(Group g, int x, int y, int dir) {
        Direction d = Direction.fromIndex(dir);
        return isMovementPossible(g, x + d.dx, y + d.dy, dir, false);
    }

    /** DM's F205: turns creature {@code i}, a quarter turn at a time if it must turn round. */
    private void setDirection(Group g, int dir, int i, boolean twoHalfSquare) {
        if (twoHalfSquare && now == lastDirectionSetTime && g == lastDirectionSetGroup) {
            return;
        }
        if (((g.facing(i).ordinal() - dir) & 3) == 2) {
            dir = ((rnd(65536) & 2) + dir + 1) & 3;
        }
        g.setFacing(i, dir);
        if (twoHalfSquare) {
            g.setFacing(i ^ 1, dir);
            lastDirectionSetTime = now;
            lastDirectionSetGroup = g;
        }
        out.changed = true;
    }

    /** DM's F206: turns at least the first creature of the group, and each other one half the time. */
    private void setDirectionGroup(Group g, int dir, int last, CreatureType.Size size) {
        boolean twoHalfSquare = last > 0 && size == CreatureType.Size.HALF;
        int i = twoHalfSquare ? last - 1 : last;
        do {
            if (i == 0 || rnd(2) != 0) {
                setDirection(g, dir, i, twoHalfSquare);
            }
        } while (i-- > 0);
    }

    /** DM's F182. */
    private void stopAttacking(Group g, DungeonMap m, int x, int y) {
        for (int i = 0; i < 4; i++) {
            g.setAspect(i, g.aspect(i) & ~Group.ASPECT_ATTACKING);
        }
        deleteEvents(m, x, y);
        out.changed = true;
    }

    /** DM's F176: the ordinal of the creature standing on {@code cell}, or 0. */
    static int creatureOrdinalInCell(Group g, int cell) {
        if (g.centred()) {
            return 1;
        }
        int last = g.count() - 1;
        if (g.type().size() == CreatureType.Size.HALF) {
            if ((g.facing(0).ordinal() & 1) == (cell & 1)) {
                cell = (cell + 3) & 3;
            }
            for (int i = last; i >= 0; i--) {
                int c = g.cellOf(i);
                if (c == cell || c == ((cell + 1) & 3)) {
                    return i + 1;
                }
            }
            return 0;
        }
        for (int i = last; i >= 0; i--) {
            if (g.cellOf(i) == cell) {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * DM's F179: picks the creatures' next look (attack pose, mirrored or
     * not, a little jitter) and returns when the look changes again.
     */
    private long aspectUpdateTime(Group g, int creature, boolean attacking) {
        CreatureType info = g.type();
        boolean whole = creature < 0;
        int i = whole ? g.count() - 1 : creature;
        do {
            int aspect = g.aspect(i) & (Group.ASPECT_ATTACKING | Group.ASPECT_FLIP);
            int offset = info.xJitter();
            if (offset != 0) {
                offset = rnd(offset);
                if (rnd(2) != 0) {
                    offset = -offset & 7;
                }
                aspect |= offset;
            }
            offset = info.yJitter();
            if (offset != 0) {
                offset = rnd(offset);
                if (rnd(2) != 0) {
                    offset = -offset & 7;
                }
                aspect |= offset << 3;
            }
            if (attacking) {
                if (info.flipsToAttack()) {
                    if ((aspect & Group.ASPECT_ATTACKING) != 0 && info == CreatureType.ANIMATED_ARMOUR) {
                        if (rnd(2) != 0) {
                            aspect ^= Group.ASPECT_FLIP;
                            sound(SOUND_COMBAT, map, groupX, groupY);
                        }
                    } else if ((aspect & Group.ASPECT_ATTACKING) == 0 || !info.flipsDuringAttack()) {
                        aspect = rnd(2) != 0 ? aspect | Group.ASPECT_FLIP : aspect & ~Group.ASPECT_FLIP;
                    }
                } else {
                    aspect &= ~Group.ASPECT_FLIP;
                }
                aspect |= Group.ASPECT_ATTACKING;
            } else {
                if (info.flipsWhenIdle()) {
                    aspect = rnd(2) != 0 ? aspect | Group.ASPECT_FLIP : aspect & ~Group.ASPECT_FLIP;
                } else {
                    aspect &= ~Group.ASPECT_FLIP;
                }
                aspect &= ~Group.ASPECT_ATTACKING;
            }
            if (aspect != g.aspect(i)) {
                out.changed |= party != null && map == party.map();
            }
            g.setAspect(i, aspect);
        } while (whole && i-- > 0);
        int animation = info.animationTicks();
        return now + (attacking ? (animation >> 8) & 15 : (animation >> 4) & 15) + rnd(2);
    }

    // ---- attacking the party ----------------------------------------------------

    /**
     * DM's F207: creature {@code i}, {@code distance} squares from the
     * party, strikes at it. One with a range over 1 casts instead (always
     * from afar, half the time up close): its spell flies from its cell
     * toward the party. Returns whether it attacked.
     */
    private boolean creatureAttacks(Group g, int x, int y, int i, int distance) {
        party.creatureAttacked();
        CreatureType info = g.type();
        int targetCell = g.centred() ? rnd(2) : ((g.cellOf(i) + 5 - primaryDirToParty) & 2) >> 1;
        targetCell = (targetCell + primaryDirToParty) & 3;
        int champion = -1;
        boolean casts = info.attackRange() > 1 && (distance > 1 || rnd(2) != 0);
        if (casts) {
            int spell = creatureSpell(info);
            if (spell >= 0) { // DM's BUG0_13: Lord Order and the Grey Lord have none (and aren't in the dungeon)
                int kineticEnergy = (info.attack() >> 2) + 1;
                kineticEnergy += rnd(kineticEnergy);
                kineticEnergy += rnd(kineticEnergy);
                Flight.launchSpell(party, spell, map, x, y, targetCell, Direction.fromIndex(primaryDirToParty),
                        Math.max(20, Math.min(kineticEnergy, 255)), info.dexterity(), 8);
            }
        } else if (info.attacksAnyChampion()) {
            champion = rnd(4);
            int tries = 0;
            while (tries < 4 && !alive(champion)) {
                champion = (champion + 1) & 3;
                tries++;
            }
            if (tries == 4) {
                return false;
            }
        } else {
            champion = targetChampion(x, y, targetCell);
            if (champion < 0) {
                return false;
            }
        }
        if (casts) {
            // the spell is on its way
        } else if (info == CreatureType.GIGGLER) {
            steal(g, champion);
        } else {
            int damage = championDamage(g, champion) + 1;
            // The champion turns to face the hardest blow (F390 does it each tick).
            party.members().get(champion).receivedBlow(damage, Direction.fromIndex(primaryDirToParty + 2));
        }
        if (info.attackSound() >= 0) {
            sound(info.attackSound(), map, x, y);
        }
        out.changed = true;
        return true;
    }

    /** F207's spells by creature type, or -1 for one that has none. */
    private int creatureSpell(CreatureType info) {
        return switch (info) {
            case VEXIRK, LORD_CHAOS -> rnd(2) != 0 ? Explosion.FIREBALL : switch (rnd(4)) {
                case 0 -> Explosion.HARM_NON_MATERIAL;
                case 1 -> Explosion.LIGHTNING_BOLT;
                case 2 -> Explosion.POISON_CLOUD;
                default -> Explosion.OPEN_DOOR;
            };
            case SWAMP_SLIME -> Explosion.SLIME;
            case WIZARD_EYE -> rnd(8) != 0 ? Explosion.LIGHTNING_BOLT : Explosion.OPEN_DOOR;
            case MATERIALIZER -> rnd(2) != 0 ? Explosion.POISON_CLOUD : Explosion.FIREBALL;
            case DEMON, RED_DRAGON -> Explosion.FIREBALL;
            default -> -1;
        };
    }

    private boolean alive(int member) {
        return member < party.members().size() && party.members().get(member).health() > 0;
    }

    /** DM's F286 with F229: the member standing in the first cell the attacker reaches, or -1. */
    private int targetChampion(int x, int y, int cell) {
        if (party.members().isEmpty() || Math.abs(x - party.x()) + Math.abs(y - party.y()) > 1) {
            return -1;
        }
        int index = directionsTo(party.x(), party.y(), x, y) << 1;
        if ((index & 2) == 0) {
            cell++;
        }
        index += (cell >> 1) & 1;
        for (int c : ATTACK_ORDER[index]) {
            int member = party.memberInCell(c);
            if (member >= 0) {
                return member;
            }
        }
        return -1;
    }

    /** DM's F230: the creature's blow on member {@code member}, with DM's hit roll, wounds and poison. Returns the damage. */
    private int championDamage(Group g, int member) {
        if (!alive(member)) {
            return 0;
        }
        Champion c = party.members().get(member);
        CreatureType info = g.type();
        int difficulty = party.map().difficulty() << 1;
        party.addSkillExperience(member, Champion.PARRY, info.experience());
        if (!((c.dexterity(party.load(c), random) < rnd(32) + info.dexterity() + difficulty - 16 || rnd(4) == 0)
                && !c.isLucky(60, random))) {
            return 0; // dodged
        }
        int woundTest = rnd(65536);
        int allowedWound;
        if ((woundTest & 0x70) != 0) {
            woundTest &= 15;
            int probabilities = info.woundProbabilities();
            int index = 0;
            while (woundTest > (probabilities & 15)) {
                probabilities >>= 4;
                index++;
            }
            allowedWound = WOUND_MASKS[index];
        } else {
            allowedWound = woundTest & 1; // 0 (the ready hand) or 1 (the action hand)
        }
        int attack = rnd(16) + info.attack() + difficulty - (c.skillLevel(Champion.PARRY) << 1);
        if (attack <= 1) {
            if (rnd(2) != 0) {
                return 0;
            }
            attack = rnd(4) + 2;
        }
        attack >>= 1;
        attack += rnd(attack) + rnd(4);
        attack += rnd(attack);
        attack >>= 2;
        attack += rnd(4) + 1;
        if (rnd(2) != 0) {
            attack -= rnd((attack >> 1) + 1) - 1;
        }
        attack = party.difficulty().creatureDamage(attack, random);
        int damage = hurt(member, attack, allowedWound, info.attackType());
        if (damage > 0) {
            sound(SOUND_CHAMPION_0_DAMAGED + member, party.map(), party.x(), party.y());
            int poison = info.poisonAttack();
            if (poison != 0 && rnd(2) != 0) {
                poison = party.difficulty().creatureDamage(c.statisticAdjustedAttack(Champion.Stat.VITALITY, poison), random);
                if (poison > 0) {
                    addDamage(member, party.poison(member, poison));
                }
            }
        }
        return damage;
    }

    /**
     * DM's F321: an attack of {@code attack} on member {@code member},
     * lessened by armour on the body parts it may wound; it may wound one of
     * them. Returns the damage done.
     */
    int hurt(int member, int attack, int allowedWounds, int attackType) {
        if (attack <= 0 || !alive(member)) {
            return 0;
        }
        Champion c = party.members().get(member);
        if (attackType != 0) { // not a normal attack: armour counts
            int defense = 0;
            int wounds = 0;
            for (int part = 0; part < 6; part++) {
                if ((allowedWounds & (1 << part)) != 0) {
                    wounds++;
                    defense += woundDefense(c, part, attackType == 4);
                }
            }
            if (wounds != 0) {
                defense /= wounds;
            }
            boolean scale = true;
            switch (attackType) {
                case 6 -> { // psychic
                    int wisdom = 115 - c.stat(Champion.Stat.WISDOM);
                    attack = wisdom <= 0 ? 0 : attack * wisdom >> 6;
                    scale = false;
                }
                case 5 -> { // magic
                    attack = c.statisticAdjustedAttack(Champion.Stat.ANTI_MAGIC, attack) - party.spellShieldDefense();
                    scale = false;
                }
                case 1 -> attack = c.statisticAdjustedAttack(Champion.Stat.ANTI_FIRE, attack) - party.fireShieldDefense(); // fire
                case 2 -> defense >>= 1; // self
                default -> { }
            }
            if (scale) {
                if (attack <= 0) {
                    return 0;
                }
                attack = attack * (130 - defense) >> 6;
            }
            if (attack <= 0) {
                return 0;
            }
            int adjusted = c.statisticAdjustedAttack(Champion.Stat.VITALITY, rnd(128) + 10);
            if (attack > adjusted) {
                do {
                    c.addWounds((1 << rnd(8)) & allowedWounds);
                } while (attack > (adjusted <<= 1) && adjusted != 0);
            }
            party.wakeUp(); // a blow that gets through armour wakes the party
        }
        int taken = c.takeDamage(attack);
        addDamage(member, taken);
        return attack;
    }

    /** {@link #hurt}, for tests. */
    int hurtForTest(Party party, int member, int attack, int allowedWounds, int attackType) {
        begin(party);
        int damage = hurt(member, attack, allowedWounds, attackType);
        out = null;
        return damage;
    }

    private void addDamage(int member, int amount) {
        if (out.damage == null) {
            out.damage = new int[party.members().size()];
        }
        out.damage[member] += amount;
        out.changed = true;
    }

    /** DM's F313: how well body part {@code part} (DM's slots 0-5) is protected, 0-100. */
    private int woundDefense(Champion c, int part, boolean sharp) {
        int shields = 0;
        for (int hand = 0; hand <= 1; hand++) {
            Item item = c.items().get(Champion.WOUND_SLOTS.get(hand));
            if (item != null && item.isShield()) {
                shields += ((c.strength(Champion.WOUND_SLOTS.get(hand), random) + ItemCatalog.armourDefense(item, sharp))
                        * WOUND_DEFENSE_FACTOR[part]) >> (hand == part ? 4 : 5);
            }
        }
        int defense = rnd((c.stat(Champion.Stat.VITALITY) >> 3) + 1);
        if (sharp) {
            defense >>= 1;
        }
        defense += c.actionDefense() + c.shieldDefense() + party.shieldDefense() + shields;
        if (part > 1) {
            defense += ItemCatalog.armourDefense(c.items().get(Champion.WOUND_SLOTS.get(part)), sharp);
        }
        if ((c.wounds() & (1 << part)) != 0) {
            defense -= 8 + rnd(4);
        }
        if (c.asleep()) {
            defense >>= 1;
        }
        return Math.max(0, Math.min(defense >> 1, 100));
    }

    /**
     * DM's F193: a Giggler's "attack" steals what the champion holds. In DM
     * the slots it tries are all the ready hand (its table was never filled
     * in), so that is all it takes. It may then run off with it.
     */
    private void steal(Group g, int member) {
        Champion c = party.members().get(member);
        boolean stolen = false;
        int percentage = 100 - c.dexterity(party.load(c), random);
        rnd(8);
        while (percentage > 0 && !c.isLucky(percentage, random)) {
            Item item = c.take(Slot.READY_HAND);
            if (item != null) {
                stolen = true;
                g.possessions().add(item);
            }
            percentage -= 20;
        }
        if (rnd(8) == 0 || stolen && rnd(2) != 0) {
            g.delayFleeing = rnd(64) + 20;
            g.setBehaviour(Group.FLEE);
        }
    }

    /** DM's F064: a sound on the party's map is heard loud or soft by its distance (#37), or not at all. */
    private void sound(int dmSound, DungeonMap m, int x, int y) {
        Sounds.Heard heard = m == party.map() ? Sounds.hear(dmSound, x - party.x(), y - party.y()) : null;
        if (heard != null) {
            out.sounds.add(heard);
        }
    }

    // ---- moving groups ------------------------------------------------------------

    /**
     * DM's F267 for a group: moves it from (fx, fy) to (tx, ty) on
     * {@code m}, through teleporters that take creatures and down open
     * pits, and checks the floor sensors it leaves and lands on. Returns
     * {@link #MOVED}, {@link #BLOCKED} when the party or another group
     * stands where it would land (it stays put), or {@link #STOP} when the
     * caller must stop (DM's non-zero result): the group died or isn't
     * allowed where it landed, was teleported, or changed maps; it then has
     * fresh events (or none).
     */
    private int moveGroup(DungeonMap m, Group g, int fx, int fy, int tx, int ty) {
        if (m == party.map() && Flight.groupMoves(party, m, g, fx, fy, tx, ty)) {
            return STOP; // F266: killed by the projectiles it walked into
        }
        DungeonMap to = m;
        int x = tx;
        int y = ty;
        boolean teleported = false;
        boolean killed = false;
        CreatureType info = g.type();
        for (int hop = 0; hop < 16; hop++) {
            Square sq = to.get(x, y);
            if (sq.type() == SquareType.TELEPORTER) {
                Teleporter t = to.activeTeleporter(x, y, Teleporter.Kind.CREATURE);
                Dungeon.Location target = t == null ? null : to.destination(t);
                if (target == null) {
                    break;
                }
                boolean self = target.map() == to && target.x() == x && target.y() == y;
                to = target.map();
                x = target.x();
                y = target.y();
                if (t.audible()) {
                    sound(SOUND_BUZZ, to, x, y);
                }
                rotate(g, t);
                teleported = true;
                if (self) {
                    break;
                }
            } else if (sq.type() == SquareType.PIT && to.dropsThrough(x, y) && !info.levitates()) {
                Dungeon.Location below = to.below(x, y);
                if (below == null) {
                    break;
                }
                to = below.map();
                x = below.x();
                y = below.y();
                int outcome = damageAll(g, 20, to, x, y, false);
                if (outcome == KILLED_ALL) {
                    killed = true;
                    break;
                }
                if (outcome == KILLED_SOME) {
                    dropMovingCreatureFixedPossessions(info, to, x, y);
                }
            } else {
                break;
            }
        }
        if (killed || !to.allowsCreature(info)) {
            dropMovingCreatureFixedPossessions(info, to, x, y);
            dropGroupPossessions(g, to, x, y);
            m.removeGroup(g);
            deleteEvents(m, fx, fy);
            out.click |= m.groupLeft(fx, fy).click();
            out.changed = true;
            return STOP;
        }
        boolean occupied = party.map() == to && party.x() == x && party.y() == y
                || to.groupAt(x, y) != null && to.groupAt(x, y) != g;
        if (occupied) {
            return BLOCKED;
        }
        if (info.movementSound() >= 0 && !party.sleeping()) { // DM's F514: sleepers hear no footsteps
            sound(info.movementSound(), to, x, y);
        }
        boolean wasOnPartyMap = m == party.map();
        if (to != m) {
            m.removeGroup(g);
            g.moveTo(x, y);
            to.addGroup(g);
        } else {
            g.moveTo(x, y);
        }
        out.click |= m.groupLeft(fx, fy).click();
        if (!info.levitates()) {
            out.click |= to.groupArrived(x, y).click();
        }
        out.changed |= wasOnPartyMap || to == party.map();
        if (to != m || teleported) {
            deleteEvents(m, fx, fy);
            if (to == party.map() && !wasOnPartyMap) {
                activate(g);
            } else if (to != party.map() && wasOnPartyMap) {
                deactivate(g);
            }
            startWandering(to, g);
            return STOP;
        }
        return MOVED;
    }

    private static final int MOVED = 0;
    private static final int STOP = 1;
    private static final int BLOCKED = 2;

    /** DM's F262 for a group: a teleporter turns its creatures, and the cells they stand on. */
    private static void rotate(Group g, Teleporter t) {
        int before = g.facing(0).ordinal();
        int after = t.absolute() ? t.rotation() : (before + t.rotation()) & 3;
        if (!g.centred()) {
            int relative = (4 + after - before) & 3;
            for (int i = 0; i < g.count(); i++) {
                if (g.type().size() == CreatureType.Size.QUARTER) {
                    relative = t.absolute() ? t.rotation() : 0;
                }
                if (relative != 0) {
                    g.setCell(i, g.cellOf(i) + relative);
                }
            }
        }
        for (int i = 0; i < g.count(); i++) {
            g.setFacing(i, t.absolute() ? t.rotation() : g.facing(i).ordinal() + t.rotation());
        }
    }

    /** DM's damage outcomes (F190, F191). */
    static final int KILLED_NONE = 0;
    static final int KILLED_SOME = 1;
    static final int KILLED_ALL = 2;

    /** DM's G392: the cells of creatures that died while their group was moving, whose drops wait for where it lands. */
    private transient List<Integer> movingDeathCells;

    /**
     * DM's F191: an attack of about {@code attack} on every creature in the
     * group on (x, y) of {@code m}. Returns {@link #KILLED_NONE},
     * {@link #KILLED_SOME} or {@link #KILLED_ALL}. With {@code notMoving} a
     * group that dies is removed here with its drops; a moving group (one
     * falling down a pit) is left to its caller.
     */
    int damageAll(Group g, int attack, DungeonMap m, int x, int y, boolean notMoving) {
        movingDeathCells = new ArrayList<>();
        if (attack <= 0) {
            return KILLED_NONE;
        }
        int seed = (attack >> 3) + 1;
        attack -= seed;
        seed <<= 1;
        boolean all = true;
        boolean some = false;
        for (int i = g.count() - 1; i >= 0; i--) {
            int outcome = damageCreature(g, i, attack + rnd(seed), m, x, y, notMoving);
            all &= outcome != KILLED_NONE;
            some |= outcome != KILLED_NONE;
        }
        return all ? KILLED_ALL : some ? KILLED_SOME : KILLED_NONE;
    }

    /**
     * DM's F190: hurts creature {@code i} of the group on (x, y). One that
     * dies leaves a puff of smoke on its cell (DM's smoke explosion, bigger
     * for bigger creatures) and drops its type's fixed possessions; if others
     * are left and the group was attacking, they may lose heart and flee.
     * The last one dying takes the group with it (when {@code notMoving}),
     * dropping everything it carried. Returns {@link #KILLED_NONE},
     * {@link #KILLED_SOME} or {@link #KILLED_ALL}.
     */
    private int damageCreature(Group g, int i, int damage, DungeonMap m, int x, int y, boolean notMoving) {
        CreatureType info = g.type();
        if (info.archenemy()) {
            return KILLED_NONE;
        }
        if (g.health(i) > damage) {
            if (damage > 0) {
                g.setHealth(i, g.health(i) - damage);
            }
            return KILLED_NONE;
        }
        int cell = g.centred() ? Group.CENTRED : g.cellOf(i);
        int outcome;
        if (g.count() == 1) {
            if (notMoving) {
                dropGroupPossessions(g, m, x, y);
                deleteGroup(m, g);
            }
            outcome = KILLED_ALL;
        } else {
            if (info.dropsFixedPossessions()) {
                if (notMoving) {
                    dropFixedPossessions(info, m, x, y, cell);
                } else if (movingDeathCells != null) {
                    movingDeathCells.add(cell);
                }
            }
            if (g.behaviour() == Group.ATTACK && party != null && m == party.map()) {
                int fear = info.fearResistance();
                if (fear != 15) {
                    fear += g.count() - 2;
                    if (fear < rnd(16)) { // seeing one die frightens the rest
                        g.delayFleeing = rnd(100 - (fear << 2)) + 20;
                        g.setBehaviour(Group.FLEE);
                    }
                }
            }
            g.remove(i);
            outcome = KILLED_SOME;
        }
        int size = switch (info.size()) {
            case QUARTER -> 110;
            case HALF -> 190;
            case FULL -> 255;
        };
        m.explosionList().add(new Explosion(Explosion.SMOKE, x, y, cell, size, now + 1)); // F213, silent for smoke
        if (party != null && m == party.map()) {
            out.changed = true;
        }
        return outcome;
    }

    /** DM's F189: the group leaves the dungeon (its square's sensors feel it go) with its events. */
    private void deleteGroup(DungeonMap m, Group g) {
        m.removeGroup(g);
        deleteEvents(m, g.x(), g.y());
        out.click |= m.groupLeft(g.x(), g.y()).click();
        out.changed = true;
    }

    /**
     * DM's F186: what one dead creature of type {@code info} always carries
     * falls on (x, y): its own cell, or a random one a quarter of the time
     * (always for a centred creature); "maybe" items only half the time.
     * A metallic thud if a weapon fell, a wooden one otherwise.
     */
    private void dropFixedPossessions(CreatureType info, DungeonMap m, int x, int y, int cell) {
        int[][] possessions = info.fixedPossessions();
        if (possessions.length == 0) {
            return;
        }
        boolean weapon = false;
        for (int[] p : possessions) {
            if (p[2] != 0 && rnd(2) != 0) {
                continue;
            }
            Item.Category category = Item.Category.values()[p[0]];
            weapon |= category == Item.Category.WEAPON;
            int at = cell == Group.CENTRED || rnd(4) == 0 ? rnd(4) : cell;
            m.dropItem(x, y, at, ItemCatalog.item(category, p[1]));
        }
        sound(weapon ? SOUND_METALLIC_THUD : SOUND_WOODEN_THUD, m, x, y);
    }

    /** DM's F187: the fixed possessions of creatures that died as their group moved, dropped where it landed. */
    private void dropMovingCreatureFixedPossessions(CreatureType info, DungeonMap m, int x, int y) {
        if (movingDeathCells == null) {
            return;
        }
        while (!movingDeathCells.isEmpty()) {
            dropFixedPossessions(info, m, x, y, movingDeathCells.remove(movingDeathCells.size() - 1));
        }
    }

    /**
     * DM's F188: a dead group's things fall on (x, y): each creature's fixed
     * possessions (if its type drops them), then what the group carried,
     * each on a random cell.
     */
    private void dropGroupPossessions(Group g, DungeonMap m, int x, int y) {
        CreatureType info = g.type();
        if (info.dropsFixedPossessions()) {
            for (int i = g.count() - 1; i >= 0; i--) {
                dropFixedPossessions(info, m, x, y, g.centred() ? Group.CENTRED : g.cellOf(i));
            }
        }
        if (!g.possessions().isEmpty()) {
            boolean weapon = false;
            for (Item item : g.possessions()) {
                weapon |= item.category() == Item.Category.WEAPON;
                m.dropItem(x, y, rnd(4), item);
            }
            g.possessions().clear();
            sound(weapon ? SOUND_METALLIC_THUD : SOUND_WOODEN_THUD, m, x, y);
        }
    }

    /**
     * A door closing on the group on (x, y) (DM's door event): it is hurt
     * (attack 5) and, if it lives, reacts by trying to get out of the way.
     * Returns whether any of it is left.
     */
    boolean crushedByDoor(Party party, DungeonMap m, int x, int y) {
        Group g = m.groupAt(x, y);
        if (g == null) {
            return false;
        }
        begin(party);
        map = m;
        boolean alive = damageAll(g, 5, m, x, y, true) != KILLED_ALL;
        if (alive && m == party.map()) {
            processEvent(m, x, y, DANGER_ON_SQUARE, 0);
        }
        out.changed = true;
        return alive;
    }

    // ---- the champions fight back (Sprint 16) ----------------------------------

    /**
     * DM's F190 for a champion's blow or a projectile: creature {@code i}
     * of group {@code g} takes {@code damage}. Returns the outcome
     * ({@link #KILLED_NONE}, {@link #KILLED_SOME} or {@link #KILLED_ALL});
     * a group that dies is gone, its things on the floor.
     */
    int hitCreature(Party party, DungeonMap m, Group g, int i, int damage) {
        begin(party);
        map = m;
        movingDeathCells = null;
        return damageCreature(g, i, damage, m, g.x(), g.y(), true);
    }

    /**
     * F401's fright: an action of {@code amount} (plus the champion's
     * influence) against the group's fear resistance. A frightened group
     * stops attacking and flees for a while. Returns whether it was.
     */
    boolean frighten(Party party, DungeonMap m, Group g, int amount) {
        begin(party);
        map = m;
        CreatureType info = g.type();
        int fear = info.fearResistance();
        if (fear > rnd(amount) || fear == 15) {
            return false;
        }
        if (g.behaviour() == Group.ATTACK) {
            stopAttacking(g, m, g.x(), g.y());
            startWandering(m, g);
        }
        g.setBehaviour(Group.FLEE);
        g.delayFleeing = ((16 - fear) << 2) / info.movementTicks();
        out.changed = true;
        return true;
    }

    /**
     * DM's F177: the creature a champion standing on {@code cell} of the
     * party's square reaches in the group on (x, y), as an index, or -1:
     * the first of DM's ordered cells (F229) that has a creature.
     */
    int meleeTarget(Party party, DungeonMap m, int x, int y, int cell) {
        Group g = m.groupAt(x, y);
        if (g == null) {
            return -1;
        }
        begin(party);
        map = m;
        for (int c : orderedCells(x, y, party.x(), party.y(), cell)) {
            int ordinal = creatureOrdinalInCell(g, c);
            if (ordinal != 0) {
                return ordinal - 1;
            }
        }
        return -1;
    }

    /** DM's F229: the cells of the target square (x, y) in the order an attacker on (ax, ay)'s {@code cell} reaches them. */
    private int[] orderedCells(int x, int y, int ax, int ay, int cell) {
        int index = directionsTo(x, y, ax, ay) << 1;
        if ((index & 2) == 0) {
            cell++;
        }
        index += (cell >> 1) & 1;
        return ATTACK_ORDER[index];
    }

    /** F321 for a projectile hitting member {@code member}: the damage done, shown with the next tick. */
    int hurtChampion(Party party, int member, int attack, int allowedWounds, int attackType) {
        begin(party);
        return hurt(member, attack, allowedWounds, attackType);
    }

    /**
     * DM's F324 for an explosion on the party's square: every champion takes
     * about {@code attack} (give or take an eighth), through F321.
     */
    void hurtParty(Party party, int attack, int allowedWounds, int attackType) {
        begin(party);
        int spread = (attack >> 3) + 1;
        int reduced = attack - spread;
        spread <<= 1;
        for (int i = 0; i < party.members().size(); i++) {
            hurt(i, Math.max(1, reduced + rnd(spread)), allowedWounds, attackType);
        }
    }

    /** DM's F322 for a projectile's poison: {@code attack}, lessened by the champion's vitality. */
    void poisonChampion(Party party, int member, int attack) {
        begin(party);
        Champion c = party.members().get(member);
        attack = c.statisticAdjustedAttack(Champion.Stat.VITALITY, attack);
        if (attack > 0) {
            addDamage(member, party.poison(member, attack));
        }
    }

    /** DM's F191 for an explosion: about {@code attack} on every creature of group {@code g}. Returns the outcome. */
    int blastGroup(Party party, DungeonMap m, Group g, int attack) {
        begin(party);
        map = m;
        movingDeathCells = null;
        return damageAll(g, attack, m, g.x(), g.y(), true);
    }

    /** DM's F192: a poison attack against a creature type's poison resistance (0 if immune). */
    static int resistedPoisonAttack(CreatureType type, int attack, Random random) {
        int resistance = type.poisonResistance();
        if (attack == 0 || resistance == 15) {
            return 0;
        }
        return ((attack + random.nextInt(4)) << 3) / (resistance + 1);
    }

    /** A DM sound made on (x, y) of {@code m}, heard with the next tick if the party is near. */
    void soundAt(Party party, int dmSound, DungeonMap m, int x, int y) {
        begin(party);
        sound(dmSound, m, x, y);
    }

    /** Something visible changed between ticks (a door broke, a projectile was thrown): the next tick repaints. */
    void changed(Party party) {
        begin(party);
        out.changed = true;
    }

    /**
     * An open pit or teleporter has just appeared under the group on (x, y)
     * of {@code m} (DM's pit and teleporter events): a walking group falls,
     * and a teleporter that takes creatures sends it on.
     */
    void settle(Party party, DungeonMap m, int x, int y) {
        Group g = m.groupAt(x, y);
        if (g == null) {
            return;
        }
        boolean falls = m.dropsThrough(x, y) && !g.type().levitates();
        if (falls || m.activeTeleporter(x, y, Teleporter.Kind.CREATURE) != null) {
            begin(party);
            map = m;
            moveGroup(m, g, x, y, x, y);
        }
    }

    /**
     * DM's event 5 reaching a generator (sensor type 6) on (x, y), then
     * F185: a new group of its creature type appears there, 1-4 strong
     * (or a random number), with DM's health (base x multiplier, or x the
     * map's difficulty, plus a little), facing a random way, and starts
     * wandering. The generator then rests for its delay, or for good if
     * once-only. Returns whether a group appeared.
     */
    boolean generate(Party party, DungeonMap m, FloorSensor s) {
        CreatureType type = CreatureType.of(s.data());
        int x = s.x();
        int y = s.y();
        if (type == null || m.groupAt(x, y) != null || party.map() == m && party.x() == x && party.y() == y) {
            return false; // DM would retry the placement later; this generator just waits for its next trigger
        }
        begin(party);
        map = m;
        int last = s.value();
        last = (last & 8) != 0 ? rnd(last & 7) : last - 1;
        last = Math.max(0, Math.min(last, 3));
        int multiplier = (s.action() >> 4) & 15;
        if (multiplier == 0) {
            multiplier = m.difficulty();
        }
        Direction facing = Direction.fromIndex(rnd(4));
        int[] health = new int[4];
        int cells = last > 0 ? 0 : Group.CENTRED;
        int cell = last > 0 ? rnd(4) : 0;
        int base = type.baseHealth();
        for (int i = last; i >= 0; i--) {
            health[i] = base * multiplier + rnd((base >> 2) + 1);
            if (last > 0) {
                cells = (cells & ~(3 << (i * 2))) | ((cell++ & 3) << (i * 2));
                if (type.size() == CreatureType.Size.HALF) {
                    cell++;
                }
                cell &= 3;
            }
        }
        Group g = new Group(type, x, y, cells, health, last + 1, facing, List.of());
        m.addGroup(g);
        sound(SOUND_BUZZ, m, x, y);
        if (s.audible()) {
            sound(SOUND_BUZZ, m, x, y);
        }
        if (s.onceOnly()) {
            s.disable();
        } else {
            int ticks = s.action() >> 8;
            if (ticks != 0) {
                s.disableUntil(now + (ticks > 127 ? (ticks - 126) << 6 : ticks));
            }
        }
        out.changed = true;
        boolean falls = m.dropsThrough(x, y) && !type.levitates();
        if (falls || m.activeTeleporter(x, y, Teleporter.Kind.CREATURE) != null) {
            if (moveGroup(m, g, x, y, x, y) == STOP) {
                return true;
            }
        } else if (!type.levitates()) {
            out.click |= m.groupArrived(x, y).click();
        }
        if (m == party.map()) {
            activate(g);
        }
        startWandering(m, g);
        return true;
    }
}
