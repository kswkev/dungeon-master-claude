package dm.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DM's creature AI on small hand-made maps. The AI is random, so each test
 * runs a few seeds and checks what must hold whatever the dice say.
 */
class CreatureAITest {

    private static Party partyOf(DungeonMap map, int x, int y, Direction facing, long seed, int champions) {
        Party p = new Party(List.of(map), 0, x, y, facing);
        p.setRandom(new Random(seed));
        for (int i = 0; i < champions; i++) {
            p.recruit(new ChampionMirror(0, 0, Direction.SOUTH, Champion.parse(ChampionTest.ELIJA, i)));
        }
        return p;
    }

    private static Group mummy(int x, int y, Direction facing) {
        return new Group(CreatureType.MUMMY, x, y, Group.CENTRED, new int[] {200, 0, 0, 0}, 1, facing, List.of());
    }

    private static int totalDamage(Party p, int ticks) {
        int total = 0;
        for (int t = 0; t < ticks; t++) {
            Party.Tick tick = p.tick();
            if (tick.damage() != null) {
                for (int d : tick.damage()) {
                    total += d;
                }
            }
        }
        return total;
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3})
    void aMummyThatSeesThePartyComesAndAttacks(long seed) {
        DungeonMap map = DungeonMap.fromAscii(0, "#########", "#.......#", "#########");
        Group g = mummy(5, 1, Direction.WEST);
        map.addGroup(g);
        Party p = partyOf(map, 1, 1, Direction.EAST, seed, 2);
        int damage = totalDamage(p, 400);
        assertEquals(2, g.x(), "it closes in");
        assertEquals(Group.ATTACK, g.behaviour());
        assertEquals(Direction.WEST, g.facing());
        assertTrue(damage > 0, "and hits");
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3})
    void aClosedDoorKeepsItOut(long seed) {
        DungeonMap map = DungeonMap.fromAscii(0, "#########", "#...D...#", "#########");
        Group g = mummy(6, 1, Direction.WEST);
        map.addGroup(g);
        Party p = partyOf(map, 2, 1, Direction.EAST, seed, 1);
        assertEquals(0, totalDamage(p, 600));
        assertTrue(g.x() > 4, "still behind the door");
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3})
    void walkingCreaturesStayOutOfOpenPits(long seed) {
        DungeonMap map = DungeonMap.fromAscii(0, "#########", "#...O...#", "#########");
        Group g = mummy(6, 1, Direction.WEST);
        map.addGroup(g);
        Party p = partyOf(map, 2, 1, Direction.EAST, seed, 1);
        totalDamage(p, 600);
        assertTrue(g.x() > 4);
        assertEquals(1, map.groups().size(), "it didn't fall");
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3})
    void aCreatureOutOfSightAndSmellLeavesThePartyAlone(long seed) {
        DungeonMap map = DungeonMap.fromAscii(0,
                "############",
                "#..........#",
                "##########.#",
                "#..........#",
                "############");
        Group g = mummy(1, 3, Direction.EAST);
        map.addGroup(g);
        Party p = partyOf(map, 1, 1, Direction.EAST, seed, 1);
        assertEquals(0, totalDamage(p, 60));
        assertFalse(g.behaviour() == Group.ATTACK);
    }

    @Test
    void bumpingIntoAGroupMakesItAttack() {
        DungeonMap map = DungeonMap.fromAscii(0, "######", "#....#", "######");
        Group g = mummy(3, 1, Direction.NORTH);
        map.addGroup(g);
        Party p = partyOf(map, 2, 1, Direction.EAST, 7, 1);
        p.tick();
        assertTrue(p.step(Party.Move.FORWARD) == null, "blocked");
        p.tick();
        p.tick();
        assertEquals(Group.ATTACK, g.behaviour());
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 4})
    void quarterSquareCreaturesStepToTheFrontToStrike(long seed) {
        DungeonMap map = DungeonMap.fromAscii(0, "######", "#....#", "######");
        // Two skeletons on the far side of their square (cells NE and SE), the party to the west.
        Group g = new Group(CreatureType.SKELETON, 3, 1, 1 | (2 << 2), new int[] {500, 500, 0, 0}, 2,
                Direction.WEST, List.of());
        map.addGroup(g);
        Party p = partyOf(map, 2, 1, Direction.EAST, seed, 2);
        int damage = totalDamage(p, 300);
        assertTrue(damage > 0);
        boolean front = false;
        for (int i = 0; i < g.count(); i++) {
            front |= g.cellOf(i) == 0 || g.cellOf(i) == 3; // the west cells, facing the party
        }
        assertTrue(front, "a skeleton moved up to the cells facing the party");
    }

    @Test
    void waspsPoison() {
        boolean poisoned = false;
        for (long seed = 1; seed <= 6 && !poisoned; seed++) {
            DungeonMap map = DungeonMap.fromAscii(0, "######", "#....#", "######");
            map.addGroup(new Group(CreatureType.GIANT_WASP, 3, 1, Group.CENTRED, new int[] {500, 0, 0, 0}, 1,
                    Direction.WEST, List.of()));
            Party p = partyOf(map, 2, 1, Direction.EAST, seed, 1);
            for (int t = 0; t < 300 && !poisoned; t++) {
                p.tick();
                poisoned = p.members().get(0).poisoned();
            }
        }
        assertTrue(poisoned);
    }

    @Test
    void poisonWearsOffDamagingAsItGoes() {
        DungeonMap map = DungeonMap.fromAscii(0, "####", "#..#", "####");
        Party p = partyOf(map, 1, 1, Direction.EAST, 1, 1);
        Champion c = p.members().get(0);
        int now = p.poison(0, 3);
        assertEquals(1, now, "at least 1 at once");
        assertTrue(c.poisoned());
        int later = totalDamage(p, 3 * Party.POISON_PERIOD);
        assertEquals(2, later, "then 1 for each of the 2 links left");
        assertFalse(c.poisoned());
    }

    @Test
    void armourSoftensBlows() {
        int bare = 0;
        int armoured = 0;
        for (long seed = 1; seed <= 40; seed++) {
            bare += blow(seed, false);
            armoured += blow(seed, true);
        }
        assertTrue(armoured < bare, armoured + " vs " + bare);
    }

    /** One sharp attack of 40 that may wound the torso, on a champion with or without a mithral aketon. */
    private static int blow(long seed, boolean armour) {
        DungeonMap map = DungeonMap.fromAscii(0, "####", "#..#", "####");
        Party p = partyOf(map, 1, 1, Direction.EAST, seed, 1);
        Champion c = p.members().get(0);
        c.take(Slot.TORSO);
        if (armour) {
            c.place(Slot.TORSO, ItemCatalog.item(Item.Category.ARMOUR, 34));
        }
        return p.dungeon().creatures().hurtForTest(p, 0, 40, Champion.WOUND_TORSO, 4);
    }

    @Test
    void scentsFollowTheParty() {
        DungeonMap map = DungeonMap.fromAscii(0, "#######", "#.....#", "#######");
        Party p = partyOf(map, 1, 1, Direction.EAST, 1, 1);
        assertTrue(p.move(Party.Move.FORWARD));
        assertTrue(p.move(Party.Move.FORWARD));
        assertTrue(p.scentOrdinal(map, 2, 1) > 0);
        int[] next = p.scentAt(p.scentOrdinal(map, 2, 1));
        assertNotNull(next);
        assertEquals(3, next[0], "the scent after (2,1) leads on to (3,1)");
    }

    @Test
    void groupsAreSavedWithTheirEvents() throws Exception {
        DungeonMap map = DungeonMap.fromAscii(0, "#########", "#.......#", "#########");
        map.addGroup(mummy(5, 1, Direction.WEST));
        Party p = partyOf(map, 1, 1, Direction.EAST, 1, 1);
        totalDamage(p, 20);
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes)) {
            out.writeObject(p);
        }
        Party loaded;
        try (java.io.ObjectInputStream in = new java.io.ObjectInputStream(
                new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
            loaded = (Party) in.readObject();
        }
        assertTrue(loaded.dungeon().creatures().pendingEvents() > 0);
        loaded.setRandom(new Random(1));
        totalDamage(loaded, 400);
        assertEquals(2, loaded.map().groups().get(0).x(), "it carries on after loading");
    }
}
