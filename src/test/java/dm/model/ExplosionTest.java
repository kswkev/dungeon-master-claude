package dm.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sprint 19: spells in flight, and the explosions they make (DM's PROJEXPL.C). */
class ExplosionTest {

    private DungeonMap map;
    private Party party;
    private Champion elija;

    /** A corridor from (1,1) to (5,1), the party on (1,1) facing east. */
    @BeforeEach
    void setUp() {
        corridor("#######", "#.....#", "#######");
    }

    private void corridor(String... rows) {
        map = DungeonMap.fromAscii(0, rows);
        party = new Party(map, 1, 1, Direction.EAST);
        party.setRandom(new Random(7));
        elija = Champion.parse(ChampionTest.ELIJA, 0);
        party.recruit(new ChampionMirror(0, 0, Direction.SOUTH, elija));
        party.takeMessages();
    }

    private Group creature(CreatureType type, int x, int y, int health) {
        Group g = new Group(type, x, y, Group.CENTRED, new int[] {health, 0, 0, 0}, 1, Direction.WEST, List.of());
        map.addGroup(g);
        return g;
    }

    /** A spell leaves the party's front left cell, heading east. */
    private void launch(int spell, int kineticEnergy) {
        Flight.launchSpell(party, spell, map, party.x(), party.y(), Direction.EAST.ordinal(), Direction.EAST,
                kineticEnergy, 90, 2);
    }

    private boolean sawExplosion(int type, int x, int y, int ticks) {
        boolean seen = false;
        for (int i = 0; i < ticks; i++) {
            party.tick();
            seen |= map.explosionsAt(x, y).stream().anyMatch(e -> e.type() == type);
        }
        return seen;
    }

    @Test
    void aThrownFulBombBurstsAgainstTheWall() {
        Flight.launch(party, ItemCatalog.item(Item.Category.POTION, 19, 120), map, 1, 1, Direction.EAST.ordinal(),
                Direction.EAST, 200, 90, 2);
        assertTrue(sawExplosion(Explosion.FIREBALL, 5, 1, 20));
        for (int x = 1; x <= 5; x++) {
            for (int cell = 0; cell < 4; cell++) {
                assertTrue(map.itemsAt(x, 1, cell).isEmpty(), "the bomb is gone");
            }
        }
    }

    @Test
    void aVenPotionThatRunsOutOfEnergyJustLands() {
        Flight.launch(party, ItemCatalog.item(Item.Category.POTION, 3, 120), map, 1, 1, Direction.EAST.ordinal(),
                Direction.EAST, 4, 90, 2);
        for (int i = 0; i < 20; i++) {
            party.tick();
        }
        assertTrue(map.projectiles().isEmpty());
        boolean landed = false;
        for (int x = 1; x <= 5; x++) {
            for (int cell = 0; cell < 4; cell++) {
                landed |= !map.itemsAt(x, 1, cell).isEmpty();
            }
        }
        assertTrue(landed);
    }

    @Test
    void walkingIntoAProjectileOnTheSquareAheadHurts() {
        // A dagger hanging in the back left cell of the square ahead, which Elija (front left) walks into.
        int cell = party.cellOf(elija);
        Flight.launch(party, ItemCatalog.item(Item.Category.WEAPON, 8), map, 2, 1, 0, Direction.SOUTH, 100, 90, 2);
        Projectile p = map.projectiles().get(0);
        p.ignoreImpacts = false;
        p.nextMove = Long.MAX_VALUE;
        assertEquals(1, cell, "Elija stands front left: north-east, facing east");
        int health = elija.health();
        party.step(Party.Move.FORWARD);
        assertTrue(map.projectiles().isEmpty(), "it hit");
        party.tick();
        assertTrue(elija.health() < health);
    }

    /** A dagger hanging still in the back-left cell (north-west, cell 0) of (3,1). */
    private Projectile hangingDagger() {
        Flight.launch(party, ItemCatalog.item(Item.Category.WEAPON, 8), map, 3, 1, 0, Direction.SOUTH, 100, 90, 2);
        Projectile p = map.projectiles().get(0);
        p.ignoreImpacts = false;
        p.nextMove = Long.MAX_VALUE;
        return p;
    }

    /** Sprint 22: F266 for groups. A creature walking off a square meets the projectiles in its cells. */
    @Test
    void aCreatureWalkingOffItsSquareMeetsAProjectile() {
        Group mummy = creature(CreatureType.MUMMY, 3, 1, 500);
        hangingDagger();
        assertEquals(false, Flight.groupMoves(party, map, mummy, 3, 1, 2, 1));
        assertTrue(map.projectiles().isEmpty(), "it hit");
        assertTrue(mummy.health(0) < 500);
    }

    @Test
    void aCreatureKilledByAProjectileItWalksIntoDoesNotMove() {
        Group mummy = creature(CreatureType.MUMMY, 3, 1, 1);
        hangingDagger();
        assertTrue(Flight.groupMoves(party, map, mummy, 3, 1, 2, 1));
        assertNull(map.groupAt(3, 1));
    }

    @Test
    void projectilesPassThroughGhostsWalkingIntoThem() {
        Group ghost = creature(CreatureType.GHOST, 3, 1, 500);
        hangingDagger();
        assertEquals(false, Flight.groupMoves(party, map, ghost, 3, 1, 2, 1));
        assertEquals(1, map.projectiles().size(), "still hanging there");
        assertEquals(500, ghost.health(0));
    }

    @Test
    void aFireballMetByAWalkingCreatureBursts() {
        Group mummy = creature(CreatureType.MUMMY, 3, 1, 500);
        Flight.launchSpell(party, Explosion.FIREBALL, map, 3, 1, 0, Direction.SOUTH, 100, 90, 2);
        Projectile p = map.projectiles().get(0);
        p.ignoreImpacts = false;
        p.nextMove = Long.MAX_VALUE;
        Flight.groupMoves(party, map, mummy, 3, 1, 2, 1);
        assertTrue(map.projectiles().isEmpty());
        assertTrue(map.explosionsAt(3, 1).stream().anyMatch(e -> e.type() == Explosion.FIREBALL));
    }

    @Test
    void aFireballIntoTheWallAheadBurnsTheParty() {
        corridor("###", "#.#", "###");
        int health = elija.health();
        launch(Explosion.FIREBALL, 120);
        party.tick();
        assertTrue(map.projectiles().isEmpty());
        List<Explosion> here = map.explosionsAt(1, 1);
        assertEquals(1, here.size(), "it bursts on the party's own square");
        assertEquals(Direction.EAST.ordinal(), here.get(0).cell());
        assertTrue(elija.health() < health, "and burns everyone there");
        party.tick();
        assertTrue(map.explosionsAt(1, 1).isEmpty(), "for a single tick");
    }

    @Test
    void godModeIsFireproof() {
        corridor("###", "#.#", "###");
        party.setGodMode(true);
        int health = elija.health();
        launch(Explosion.FIREBALL, 255);
        party.tick();
        assertEquals(health, elija.health());
    }

    @Test
    void aFireballBurnsTheGroupItHits() {
        creature(CreatureType.MUMMY, 4, 1, 30);
        launch(Explosion.FIREBALL, 200);
        assertTrue(sawExplosion(Explosion.FIREBALL, 4, 1, 6));
        party.tick();
        assertNull(map.groupAt(4, 1), "hit, then burnt by the blast");
        assertTrue(map.explosionsAt(4, 1).stream().allMatch(e -> e.type() == Explosion.SMOKE),
                "only its smoke is left");
    }

    @Test
    void aFireballFeedsABlackFlame() {
        Group g = creature(CreatureType.BLACK_FLAME, 3, 1, 50);
        launch(Explosion.FIREBALL, 200);
        for (int i = 0; i < 6; i++) {
            party.tick();
        }
        assertTrue(g.health(0) > 50);
        assertTrue(map.explosionsAt(3, 1).isEmpty(), "no blast either");
    }

    @Test
    void aFireballFliesThroughAGhostButHarmNonMaterialHurtsIt() {
        Group ghost = creature(CreatureType.GHOST, 3, 1, 100);
        launch(Explosion.FIREBALL, 200);
        assertTrue(sawExplosion(Explosion.FIREBALL, 5, 1, 12), "it bursts on the far wall");
        assertEquals(100, ghost.health(0));
        launch(Explosion.HARM_NON_MATERIAL, 200);
        assertTrue(sawExplosion(Explosion.HARM_NON_MATERIAL, 3, 1, 6));
        assertTrue(map.groupAt(3, 1) == null || ghost.health(0) < 100);
    }

    @Test
    void harmNonMaterialLeavesSolidCreaturesAlone() {
        Group mummy = creature(CreatureType.MUMMY, 3, 1, 100);
        launch(Explosion.HARM_NON_MATERIAL, 200);
        assertTrue(sawExplosion(Explosion.HARM_NON_MATERIAL, 3, 1, 6));
        assertEquals(100, mummy.health(0));
    }

    @Test
    void aFireballBreaksADoorMagicCanBreak() {
        corridor("#######", "#..D..#", "#######");
        map.setDoorStyle(3, 1, 1 | DungeonMap.DOOR_MAGIC_DESTRUCTIBLE); // wood
        launch(Explosion.FIREBALL, 255);
        assertTrue(sawExplosion(Explosion.FIREBALL, 3, 1, 6), "it bursts on the door");
        assertEquals(DungeonMap.DOOR_BROKEN, map.doorState(3, 1));
    }

    @Test
    void aDoorMagicCantBreakHolds() {
        corridor("#######", "#..D..#", "#######");
        map.setDoorStyle(3, 1, 1 | DungeonMap.DOOR_MELEE_DESTRUCTIBLE);
        launch(Explosion.FIREBALL, 255);
        assertTrue(sawExplosion(Explosion.FIREBALL, 3, 1, 6));
        assertEquals(DungeonMap.DOOR_CLOSED, map.doorState(3, 1));
    }

    @Test
    void openDoorWorksADoorWithAButton() {
        corridor("#######", "#..D..#", "#######");
        Decorations decorations = new Decorations(7, 3);
        decorations.setDoor(3, 1, 0, true);
        map.setDecorations(decorations);
        launch(Explosion.OPEN_DOOR, 200);
        for (int i = 0; i < 4; i++) {
            party.tick();
        }
        for (int i = 0; i < 6; i++) {
            map.tickDoors();
        }
        assertEquals(DungeonMap.DOOR_OPEN, map.doorState(3, 1));
    }

    @Test
    void openDoorCantOpenADoorWithoutAButton() {
        corridor("#######", "#..D..#", "#######");
        map.setDecorations(new Decorations(7, 3));
        launch(Explosion.OPEN_DOOR, 200);
        for (int i = 0; i < 4; i++) {
            party.tick();
        }
        for (int i = 0; i < 6; i++) {
            map.tickDoors();
        }
        assertEquals(DungeonMap.DOOR_CLOSED, map.doorState(3, 1));
    }

    @Test
    void aPoisonCloudLingersThinningAndHurting() {
        Flight.explode(party, map, Explosion.POISON_CLOUD, 30, 1, 1, Group.CENTRED);
        int health = elija.health();
        int ticks = 0;
        while (!map.explosionsAt(1, 1).isEmpty() && ticks < 50) {
            party.tick();
            ticks++;
        }
        assertEquals(10, ticks, "30, 27 ... down to 3: ten ticks of poison");
        assertTrue(elija.health() < health);
    }

    @Test
    void aPoisonCloudCastOnACreatureCoversItsSquare() {
        assertEquals(15, CreatureType.MUMMY.poisonResistance(), "the undead are immune");
        Group scorpion = creature(CreatureType.GIANT_SCORPION, 4, 1, 500);
        assertTrue(CreatureType.GIANT_SCORPION.poisonResistance() < 15);
        launch(Explosion.POISON_CLOUD, 200);
        boolean centred = false;
        for (int i = 0; i < 10; i++) {
            party.tick();
            centred |= map.explosionsAt(4, 1).stream().anyMatch(Explosion::centred);
        }
        assertTrue(centred);
        assertTrue(scorpion.health(0) < 500 || map.groupAt(4, 1) != scorpion, "it chokes, or moves away");
    }

    @Test
    void aSpellWithoutEnergyFizzles() {
        Flight.launchSpell(party, Explosion.FIREBALL, map, 1, 1, Direction.EAST.ordinal(), Direction.EAST, 12, 90, 9);
        for (int i = 0; i < 10; i++) {
            party.tick();
            for (int x = 0; x < 7; x++) {
                assertTrue(map.explosionsAt(x, 1).isEmpty(), "no burst when it runs out");
            }
        }
        assertTrue(map.projectiles().isEmpty());
    }

    @Test
    void aPoisonBoltPoisonsWithoutBursting() {
        Group mummy = creature(CreatureType.MUMMY, 3, 1, 500);
        launch(Explosion.POISON_BOLT, 200);
        for (int i = 0; i < 6; i++) {
            party.tick();
            assertTrue(map.explosionsAt(3, 1).isEmpty());
        }
        assertTrue(map.projectiles().isEmpty());
        assertTrue(mummy.health(0) < 500, "poison in its veins");
    }

    @Test
    void creaturesImmuneToFireShrugOffTheBlast() {
        assertEquals(15, CreatureType.BLACK_FLAME.fireResistance());
        assertTrue(CreatureType.RED_DRAGON.fireResistance() > CreatureType.MUMMY.fireResistance());
    }

    @Test
    void theSpellShieldTakesMagicAway() {
        Magic.shield(party, true, 640); // defense 20 for 640 ticks
        assertEquals(20, party.spellShieldDefense());
        assertEquals(0, party.dungeon().creatures().hurtForTest(party, 0, 15, Champion.WOUND_HEAD,
                Flight.ATTACK_MAGIC));
        for (int i = 0; i < 640; i++) {
            party.tick();
        }
        assertEquals(0, party.spellShieldDefense());
    }
}
