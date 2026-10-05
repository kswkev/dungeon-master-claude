package dm.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sprint 16: DM's actions, blows, throws and shots, and what they do to creatures and doors. */
class CombatTest {

    private static final Item SWORD = ItemCatalog.item(Item.Category.WEAPON, 10);
    private static final Item DAGGER = ItemCatalog.item(Item.Category.WEAPON, 8);
    private static final Item BOW = ItemCatalog.item(Item.Category.WEAPON, 25);
    private static final Item ARROW = ItemCatalog.item(Item.Category.WEAPON, 27);

    private DungeonMap map;
    private Party party;
    private Champion elija;

    /** A corridor from (1,1) to (5,1), the party on (1,1) facing east with a strong, nimble Elija. */
    @BeforeEach
    void setUp() {
        map = DungeonMap.fromAscii(0, "#######", "#.....#", "#######");
        party = new Party(map, 1, 1, Direction.EAST);
        party.setRandom(new Random(3));
        elija = recruit(0);
    }

    private Champion recruit(int portrait) {
        Champion c = Champion.parse(ChampionTest.ELIJA, portrait);
        c.setStat(Champion.Stat.STRENGTH, 120);
        c.setStat(Champion.Stat.DEXTERITY, 120);
        party.recruit(new ChampionMirror(0, 0, Direction.SOUTH, c));
        party.takeMessages(); // not "NAME RESURRECTED."
        return c;
    }

    /** A single creature in the middle of (x, y), added after the party arrived so it waits until provoked. */
    private Group creature(CreatureType type, int x, int y, int health) {
        Group g = new Group(type, x, y, Group.CENTRED, new int[] {health, 0, 0, 0}, 1, Direction.WEST, List.of());
        map.addGroup(g);
        return g;
    }

    private void tickUntilReady(Champion c) {
        for (int i = 0; i < 200 && c.actionDisabled(); i++) {
            party.tick();
        }
    }

    @Test
    void anEmptyHandPunchesKicksAndShouts() {
        assertEquals(List.of(Actions.PUNCH, Actions.KICK, Actions.WAR_CRY), party.actions(0));
    }

    @Test
    void aSwordOffersMoreAsTheSkillGrows() {
        elija.replace(Slot.ACTION_HAND, SWORD);
        List<Integer> expected = new ArrayList<>(List.of(Actions.SWING));
        if (elija.skillLevel(Actions.skill(Actions.PARRY)) >= 2) {
            expected.add(Actions.PARRY);
        }
        if (elija.skillLevel(Actions.skill(Actions.CHOP)) >= 3) {
            expected.add(Actions.CHOP);
        }
        assertEquals(expected, party.actions(0));
    }

    @Test
    void magicIsLeftForTheSpells() {
        elija.replace(Slot.ACTION_HAND, ItemCatalog.item(Item.Category.WEAPON, 45)); // the complete Firestaff
        assertTrue(party.actions(0).isEmpty(), "invoke, fuse and fluxcage are all spells");
    }

    @Test
    void aChampionRecoveringFromAnActionCannotActAgainYet() {
        party.act(0, Actions.WAR_CRY); // nothing to frighten
        assertTrue(elija.actionDisabled());
        assertTrue(party.actions(0).isEmpty());
        assertEquals(Actions.defense(Actions.WAR_CRY), elija.actionDefense(), "its defense counts meanwhile");
        tickUntilReady(elija);
        assertFalse(elija.actionDisabled());
        assertEquals(0, elija.actionDefense(), "and goes when the champion can act again");
        assertEquals(3, party.actions(0).size());
    }

    @Test
    void aPunchAtNothingCostsNoTimeAndNoDefense() {
        party.act(0, Actions.PUNCH);
        assertFalse(elija.actionDisabled(), "1 tick, halved for a miss");
        assertEquals(0, elija.actionDefense(), "not DM's BUG0_54, which kept the punch's -10 for good");
    }

    @Test
    void anotherActionWhileRecoveringAddsHalfTheWait() {
        party.act(0, Actions.WAR_CRY);
        long until = elija.enabledAt();
        Combat.disable(party, elija, 2);
        assertEquals(until + 1, elija.enabledAt(), "shorter: half of its own ticks more");
        long now = party.time();
        long current = elija.enabledAt();
        Combat.disable(party, elija, 40);
        assertEquals(now + 40 + ((current - now) >> 1), elija.enabledAt(), "longer: plus half of what was left");
    }

    @Test
    void blowsHurtTheCreatureAheadAndTeachTheChampion() {
        Group mummy = creature(CreatureType.MUMMY, 2, 1, 1000);
        elija.replace(Slot.ACTION_HAND, SWORD);
        long swing = elija.experience(Champion.SWING);
        int best = 0;
        for (int i = 0; i < 20; i++) {
            best = Math.max(best, party.act(0, Actions.SWING));
            tickUntilReady(elija);
        }
        assertTrue(best > 0, "some blows land, and the area shows their damage");
        assertTrue(mummy.health(0) < 1000);
        assertTrue(elija.experience(Champion.SWING) > swing);
        assertEquals(Group.ATTACK, mummy.behaviour(), "a hit creature turns on the party");
    }

    @Test
    void aKilledCreatureLeavesSmokeAndWhatItCarried() {
        Group skeleton = creature(CreatureType.SKELETON, 2, 1, 1);
        skeleton.possessions().add(DAGGER);
        elija.replace(Slot.ACTION_HAND, SWORD);
        for (int i = 0; i < 50 && map.groupAt(2, 1) != null; i++) {
            party.act(0, Actions.SWING);
            tickUntilReady(elija);
        }
        assertNull(map.groupAt(2, 1), "dead");
        List<Item> dropped = new ArrayList<>();
        for (int cell = 0; cell < 4; cell++) {
            dropped.addAll(map.itemsAt(2, 1, cell));
        }
        assertTrue(dropped.contains(DAGGER), "the group's own possessions");
        assertTrue(dropped.contains(ItemCatalog.item(Item.Category.WEAPON, 9)), "a skeleton's falchion");
        assertTrue(dropped.contains(ItemCatalog.item(Item.Category.ARMOUR, 30)), "and wooden shield");
    }

    @Test
    void oneOfSeveralDyingDropsItsOwnThings() {
        Group two = new Group(CreatureType.SKELETON, 2, 1, 0 | (1 << 2), new int[] {5, 50, 0, 0}, 2,
                Direction.WEST, List.of());
        map.addGroup(two);
        int outcome = party.dungeon().creatures().hitCreature(party, map, two, 0, 10);
        assertEquals(CreatureAI.KILLED_SOME, outcome);
        assertEquals(1, two.count());
        assertEquals(50, two.health(0), "the other moves down a place");
        assertFalse(map.explosionsAt(2, 1).isEmpty(), "a puff of smoke");
        int items = 0;
        for (int cell = 0; cell < 4; cell++) {
            items += map.itemsAt(2, 1, cell).size();
        }
        assertEquals(2, items, "its falchion and shield");
    }

    @Test
    void aChampionBehindAnotherCannotReach() {
        Champion second = recruit(1);
        party.swap(Party.FRONT_RIGHT, Party.BACK_LEFT); // behind Elija
        creature(CreatureType.MUMMY, 2, 1, 1000);
        assertEquals(Party.CANT_REACH, party.act(1, Actions.KICK));
        assertEquals(party.time() + (Actions.disabledTicks(Actions.KICK) >> 1), second.enabledAt(),
                "the try takes half the time");
    }

    @Test
    void aWarCryCanSendCreaturesFleeing() {
        CreatureType timid = null;
        for (CreatureType t : CreatureType.values()) {
            if (t.fearResistance() == 0) {
                timid = t;
            }
        }
        assertNotNull(timid);
        Group g = creature(timid, 2, 1, 100);
        party.act(0, Actions.WAR_CRY);
        assertEquals(Group.FLEE, g.behaviour(), "no fear resistance at all");
        assertTrue(elija.experience(Champion.INFLUENCE) > 0);
    }

    @Test
    void kickingAWoodenDoorBreaksItAMomentLater() {
        DungeonMap doors = DungeonMap.fromAscii(0, "#####", "#.D.#", "#####");
        doors.setDoorStyle(2, 1, 1 | DungeonMap.DOOR_MELEE_DESTRUCTIBLE);
        party = new Party(doors, 1, 1, Direction.EAST);
        party.setRandom(new Random(3));
        recruit(0);
        party.act(0, Actions.KICK);
        assertEquals(DungeonMap.DOOR_CLOSED, doors.doorState(2, 1));
        doors.tickDoors();
        doors.tickDoors();
        assertEquals(DungeonMap.DOOR_BROKEN, doors.doorState(2, 1));
        assertTrue(doors.isPassable(2, 1));
    }

    @Test
    void onlyDestructibleDoorsBreakAndOnlyToAStrongEnoughBlow() {
        DungeonMap doors = DungeonMap.fromAscii(0, "#####", "#DDD#", "#####");
        doors.setDoorStyle(1, 1, 1); // wood, but not breakable by blows
        doors.setDoorStyle(2, 1, 2 | DungeonMap.DOOR_MELEE_DESTRUCTIBLE); // iron: defense 230
        doors.setDoorStyle(3, 1, 1 | DungeonMap.DOOR_MAGIC_DESTRUCTIBLE);
        assertFalse(doors.breakDoor(1, 1, 255, false, 0));
        assertFalse(doors.breakDoor(2, 1, 229, false, 0));
        assertTrue(doors.breakDoor(2, 1, 230, false, 0));
        assertEquals(DungeonMap.DOOR_BROKEN, doors.doorState(2, 1));
        assertFalse(doors.breakDoor(3, 1, 100, false, 0), "only magic breaks this one");
        assertTrue(doors.breakDoor(3, 1, 100, true, 0));
    }

    @Test
    void throwingTheActionHandTakesTheNextWeaponFromTheQuiver() {
        elija.replace(Slot.ACTION_HAND, DAGGER);
        elija.replace(Slot.QUIVER_1, DAGGER);
        assertTrue(party.actions(0).contains(Actions.THROW));
        party.act(0, Actions.THROW);
        assertNull(elija.items().get(Slot.ACTION_HAND));
        assertEquals(1, map.projectiles().size());
        tickUntilReady(elija);
        assertSame(DAGGER, elija.items().get(Slot.ACTION_HAND), "refilled when the champion can act again");
        assertNull(elija.items().get(Slot.QUIVER_1));
    }

    @Test
    void aBowNeedsArrowsInTheOtherHand() {
        elija.replace(Slot.ACTION_HAND, BOW);
        assertEquals(List.of(Actions.SHOOT), party.actions(0));
        assertEquals(Party.NEED_AMMO, party.act(0, Actions.SHOOT));
        assertTrue(map.projectiles().isEmpty());
        tickUntilReady(elija);

        elija.replace(Slot.READY_HAND, ARROW);
        elija.replace(Slot.QUIVER_2, ARROW);
        assertEquals(0, party.act(0, Actions.SHOOT));
        assertEquals(1, map.projectiles().size());
        assertSame(ARROW, map.projectiles().get(0).item());
        tickUntilReady(elija);
        assertSame(ARROW, elija.items().get(Slot.READY_HAND), "the next arrow from the quiver");
        assertNull(elija.items().get(Slot.QUIVER_2));
    }

    @Test
    void aThrownSwordHurtsTheCreatureItHitsAndDropsAtItsFeet() {
        Group mummy = creature(CreatureType.MUMMY, 4, 1, 1000);
        Flight.launch(party, SWORD, map, 1, 1, Direction.EAST.ordinal(), Direction.EAST, 200, 200, 5);
        for (int i = 0; i < 20 && !map.projectiles().isEmpty(); i++) {
            party.tick();
        }
        assertTrue(map.projectiles().isEmpty());
        assertTrue(mummy.health(0) < 1000);
        boolean found = false;
        for (int cell = 0; cell < 4; cell++) {
            found |= map.itemsAt(4, 1, cell).contains(SWORD);
        }
        assertTrue(found, "it drops where it hit");
    }

    @Test
    void ghostsLetThrownThingsFlyThrough() {
        Group ghost = creature(CreatureType.GHOST, 3, 1, 1000);
        Flight.launch(party, SWORD, map, 1, 1, Direction.EAST.ordinal(), Direction.EAST, 200, 200, 5);
        for (int i = 0; i < 20 && !map.projectiles().isEmpty(); i++) {
            party.tick();
        }
        assertEquals(1000, ghost.health(0));
        assertTrue(map.hasItems(5, 1), "on to the far wall");
    }

    @Test
    void somethingFlyingBackHitsTheChampionInItsWay() {
        int health = elija.health();
        // Elija stands front-left facing east: the north-east cell.
        Flight.launch(party, SWORD, map, 2, 1, 1, Direction.WEST, 200, 200, 5);
        party.tick(); // across (2,1)
        party.tick(); // into the party's square
        party.tick(); // and into Elija
        assertTrue(map.projectiles().isEmpty());
        assertTrue(elija.health() < health);
        assertEquals(List.of(SWORD), map.itemsAt(1, 1, 1));
    }

    @Test
    void aFlippedCoinComesUpHeadsOrTails() {
        elija.replace(Slot.ACTION_HAND, ItemCatalog.item(Item.Category.JUNK, 6)); // a copper coin
        assertEquals(List.of(Actions.FLIP), party.actions(0));
        party.act(0, Actions.FLIP);
        String text = party.takeMessages().get(0).text();
        assertTrue(text.equals("IT COMES UP HEADS.") || text.equals("IT COMES UP TAILS."), text);
    }
}
