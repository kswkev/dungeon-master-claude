package dm.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Creatures and the dungeon: doors, pits, teleporters, pressure plates and generators. */
class CreatureWorldTest {

    private static Party party(List<DungeonMap> maps, int x, int y, Direction facing) {
        Party p = new Party(maps, 0, x, y, facing);
        p.setRandom(new Random(3));
        p.recruit(new ChampionMirror(0, 0, Direction.SOUTH, Champion.parse(ChampionTest.ELIJA, 0)));
        return p;
    }

    private static Group mummy(int x, int y, int health) {
        return new Group(CreatureType.MUMMY, x, y, Group.CENTRED, new int[] {health, 0, 0, 0}, 1, Direction.NORTH,
                List.of(ItemCatalog.item(Item.Category.WEAPON, 10)));
    }

    private static FloorSensor sensor(int x, int y, int type, FloorSensor.Effect effect, int tx, int ty) {
        return new FloorSensor(x, y, type, effect, false, false, false, tx, ty, -1);
    }

    @Test
    void aClosingDoorCrushesACreatureAndBouncesOffIt() {
        DungeonMap map = DungeonMap.fromAscii(0, "#######", "#..d..#", "#######");
        Group g = mummy(3, 1, 12);
        map.addGroup(g);
        Party p = party(List.of(map), 1, 1, Direction.EAST);
        map.moveDoor(3, 1, false);
        boolean thud = false;
        for (int t = 0; t < 40 && map.groupAt(3, 1) != null; t++) {
            thud |= map.tickDoors().thud();
            assertTrue(map.doorState(3, 1) < DungeonMap.DOOR_CLOSED, "it never shuts on the mummy");
        }
        assertTrue(thud);
        assertNull(map.groupAt(3, 1), "crushed to death (5 a blow against 12 health)");
        assertTrue(map.hasItems(3, 1), "its sword is left behind");
        assertEquals(0, p.dungeon().creatures().pendingEvents());
    }

    @Test
    void aGhostIsntHurtByDoors() {
        DungeonMap map = DungeonMap.fromAscii(0, "#######", "#..d..#", "#######");
        Group ghost = new Group(CreatureType.GHOST, 3, 1, Group.CENTRED, new int[] {12, 0, 0, 0}, 1, Direction.NORTH,
                List.of());
        map.addGroup(ghost);
        party(List.of(map), 1, 1, Direction.EAST);
        map.moveDoor(3, 1, false);
        for (int t = 0; t < 10; t++) {
            map.tickDoors();
        }
        assertEquals(DungeonMap.DOOR_CLOSED, map.doorState(3, 1));
        assertEquals(12, ghost.health(0));
    }

    @Test
    void aPitOpeningUnderACreatureDropsItALevel() {
        DungeonMap upper = DungeonMap.fromAscii(0, "#######", "#.....#", "#######");
        DungeonMap lower = DungeonMap.fromAscii(1, "#######", "#.....#", "#######");
        Square[][] squares = new Square[7][3];
        for (int x = 0; x < 7; x++) {
            for (int y = 0; y < 3; y++) {
                squares[x][y] = upper.get(x, y);
            }
        }
        squares[4][1] = new Square(2 << 5); // a closed pit
        upper = new DungeonMap(0, squares);
        Group g = mummy(4, 1, 200);
        upper.addGroup(g);
        upper.addSensor(sensor(2, 1, FloorSensor.TYPE_PARTY, FloorSensor.Effect.SET, 4, 1));
        Party p = party(List.of(upper, lower), 1, 1, Direction.EAST);
        assertTrue(p.move(Party.Move.FORWARD), "onto the plate: the pit opens");
        assertTrue(upper.groups().isEmpty());
        assertSame(g, lower.groupAt(4, 1), "it fell");
        assertTrue(g.health(0) < 200, "and was hurt by the fall");
    }

    @Test
    void aCreatureTeleporterMovesGroupsButNotTheParty() {
        DungeonMap map = DungeonMap.fromAscii(0, "########", "#......#", "########");
        Square[][] squares = new Square[8][3];
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 3; y++) {
                squares[x][y] = map.get(x, y);
            }
        }
        squares[3][1] = new Square((5 << 5) | 8); // an open, invisible teleporter
        map = new DungeonMap(0, squares);
        map.addTeleporter(new Teleporter(3, 1, 0, 6, 1, 0, false, Teleporter.SCOPE_CREATURES, false));
        Group g = mummy(4, 1, 200);
        g.face(Direction.WEST);
        map.addGroup(g);
        Party p = party(List.of(map), 1, 1, Direction.EAST);
        boolean teleported = false;
        for (int t = 0; t < 400 && !teleported; t++) {
            p.tick();
            teleported = g.x() >= 5;
        }
        assertTrue(teleported, "walking at the party, the mummy steps in and is sent back");
        assertTrue(p.move(Party.Move.FORWARD));
        assertTrue(p.move(Party.Move.FORWARD));
        assertEquals(3, p.x(), "the party walks over it");
    }

    @Test
    void creaturesPressPartyOrCreaturePlates() {
        DungeonMap map = DungeonMap.fromAscii(0, "#########", "#...D...#", "#########");
        map.addSensor(sensor(6, 1, FloorSensor.TYPE_PARTY_OR_CREATURE, FloorSensor.Effect.SET, 4, 1));
        Group g = mummy(7, 1, 200);
        g.face(Direction.WEST);
        map.addGroup(g);
        Party p = party(List.of(map), 1, 1, Direction.EAST);
        boolean opened = false;
        for (int t = 0; t < 600 && !opened; t++) {
            p.tick();
            map.tickDoors();
            opened = map.doorState(4, 1) < DungeonMap.DOOR_CLOSED;
        }
        assertTrue(opened, "the mummy wandered onto the plate and opened the door");
    }

    @Test
    void aGeneratorMakesCreaturesThenRests() {
        DungeonMap map = DungeonMap.fromAscii(0, "#########", "#.......#", "#########");
        map.addSensor(sensor(2, 1, FloorSensor.TYPE_PARTY, FloorSensor.Effect.SET, 6, 1));
        // A generator of 2 mummies, health x2, resting 50 ticks.
        map.addSensor(new FloorSensor(6, 1, FloorSensor.TYPE_GENERATOR, FloorSensor.Effect.SET, false, false, false,
                0, 0, -1, CreatureType.MUMMY.ordinal(), 2, (50 << 8) | (2 << 4)));
        Party p = party(List.of(map), 1, 1, Direction.EAST);
        assertTrue(p.move(Party.Move.FORWARD));
        Group g = map.groupAt(6, 1);
        assertNotNull(g, "two mummies appear");
        assertEquals(2, g.count());
        assertTrue(g.health(0) >= 66 && g.health(0) <= 66 + 8);
        assertFalse(g.centred());
        assertTrue(p.dungeon().creatures().pendingEvents() > 0, "and start wandering");
        FloorSensor generator = map.sensors().get(1);
        assertFalse(generator.enabled(), "resting");
        for (int t = 0; t < 51; t++) {
            p.tick();
        }
        assertTrue(generator.enabled(), "and back after 50 ticks");
    }
}
