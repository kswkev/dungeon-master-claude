package dm.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sprint 22: the Firestaff's FUSE on Lord Chaos (DM's F225), and fluxcages around him (F224). */
class FuseTest {

    private DungeonMap map;
    private Party party;
    private Group chaos;

    /** A corridor (1,1)-(3,1) between walls: the party on (1,1) faces Lord Chaos on (2,1). */
    @BeforeEach
    void setUp() {
        map = DungeonMap.fromAscii(0, "#####", "#...#", "#####");
        party = new Party(map, 1, 1, Direction.EAST);
        party.setRandom(new Random(11));
        Champion elija = Champion.parse(ChampionTest.ELIJA, 0);
        party.recruit(new ChampionMirror(0, 0, Direction.SOUTH, elija));
        elija.replace(Slot.ACTION_HAND, ItemCatalog.item(Item.Category.WEAPON, 45)); // the complete Firestaff
        chaos = new Group(CreatureType.LORD_CHAOS, 2, 1, Group.CENTRED, new int[] {1000, 0, 0, 0}, 1,
                Direction.WEST, List.of());
        map.addGroup(chaos);
    }

    private void fuse() {
        party.act(0, Actions.FUSE);
    }

    @Test
    void caughtBetweenFluxcagesAndWallsHeIsFused() {
        Flight.fluxcage(party, map, 1, 1);
        Flight.fluxcage(party, map, 3, 1);
        fuse();
        assertNotNull(party.endgame());
        assertEquals(2, party.endgame().x());
        assertSame(chaos, map.groupAt(2, 1));
    }

    @Test
    void withAnOpenSideHeEscapes() {
        Flight.fluxcage(party, map, 1, 1);
        fuse();
        assertNull(party.endgame());
        assertSame(chaos, map.groupAt(3, 1), "he steps out the open side");
        assertNull(map.groupAt(2, 1));
    }

    /** The endgame's messages printed so far (white text; clears are left out). */
    private final List<String> printed = new java.util.ArrayList<>();

    /** F446's script, played as the screen plays it: a game tick whenever a step asks for one. */
    private void play(java.util.function.Predicate<Endgame> until) {
        Endgame end = party.endgame();
        for (int i = 0; i < 2000 && !until.test(end); i++) {
            if (end.next()) {
                party.tick();
            }
            for (Party.Message m : party.takeMessages()) {
                if (m.member() == Party.MESSAGE_WHITE) {
                    printed.add(m.text());
                }
            }
        }
    }

    @Test
    void theFuseSequenceTurnsHimIntoTheGreyLordAndWins() {
        map.addGroup(new Group(CreatureType.MUMMY, 3, 1, Group.CENTRED, new int[] {50, 0, 0, 0}, 1,
                Direction.WEST, List.of()));
        map.setEndgameTexts(List.of("BSECOND", "AFIRST\nLINE", "DNEVER"));
        Flight.fluxcage(party, map, 1, 1);
        Flight.fluxcage(party, map, 3, 1);
        fuse();
        Endgame end = party.endgame();
        assertNotNull(end);
        play(e -> chaos.type() == CreatureType.LORD_ORDER);
        assertTrue(chaos.health(0) > 0, "he survives the fireballs");
        assertEquals(200, party.magicalLight());
        assertEquals(100, party.shieldDefense());
        play(Endgame::fluxcagesHidden);
        assertEquals(CreatureType.GREY_LORD, chaos.type());
        assertTrue(map.explosionsAt(2, 1).stream().noneMatch(e -> e.type() == Explosion.FLUXCAGE));
        play(e -> map.groupAt(3, 1) == null);
        assertSame(chaos, map.groupAt(2, 1), "only the Grey Lord is left");
        assertFalse(end.won());
        play(Endgame::won);
        assertTrue(end.won());
        assertEquals(List.of("FIRST\nLINE", "SECOND"), printed);
    }

    @Test
    void theClosingTextsComeInLetterOrder() {
        assertEquals(List.of("FIRST\nLINE", "SECOND"),
                Endgame.closingTexts(List.of("BSECOND", "AFIRST\nLINE", "DNEVER")));
        assertEquals(List.of(), Endgame.closingTexts(List.of("BNO A")));
    }

    @Test
    void fuseElsewhereIsJustABlast() {
        map.removeGroup(chaos);
        fuse();
        assertNull(party.endgame());
        assertTrue(map.explosionsAt(2, 1).stream().anyMatch(e -> e.type() == Explosion.HARM_NON_MATERIAL
                && e.attack() == 255));
    }
}
