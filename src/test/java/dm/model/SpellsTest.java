package dm.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sprint 19: DM's spell table, entering symbols, casting, and the light and shield spells. */
class SpellsTest {

    // Symbol columns, by row.
    private static final int LO = 0;
    private static final int UM = 1;
    private static final int ON = 2;
    private static final int MON = 5;
    private static final int YA = 0;
    private static final int OH = 2;
    private static final int FUL = 3;
    private static final int DES = 4;
    private static final int EW = 1;
    private static final int IR = 3;
    private static final int BRO = 4;
    private static final int SAR = 5;
    private static final int NETA = 3;

    private DungeonMap map;
    private Party party;
    private Champion elija;

    @BeforeEach
    void setUp() {
        map = DungeonMap.fromAscii(1, "#######", "#.....#", "#######");
        map.setDifficulty(1); // not always lit
        party = new Party(map, 1, 1, Direction.EAST);
        party.setRandom(new Random(5));
        elija = Champion.parse(ChampionTest.ELIJA, 0);
        party.recruit(new ChampionMirror(0, 0, Direction.SOUTH, elija));
        party.takeMessages();
        elija.setMana(500);
    }

    private static String symbols(int... columns) {
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < columns.length; row++) {
            sb.append((char) (Spells.FIRST_SYMBOL + row * Spells.PER_ROW + columns[row]));
        }
        return sb.toString();
    }

    private void enter(int... columns) {
        for (int column : columns) {
            assertTrue(party.addSymbol(column));
        }
    }

    /** Makes Elija a master of every magic skill. */
    private void master() {
        for (int skill : new int[] {Champion.PRIEST, Champion.WIZARD}) {
            elija.addExperience(skill, 500L << 12);
        }
    }

    private List<String> messages() {
        return party.takeMessages().stream().map(Party.Message::text).toList();
    }

    private void tick(int ticks) {
        for (int i = 0; i < ticks; i++) {
            party.tick();
        }
    }

    @Test
    void theSpellTableFindsSpellsWhateverThePower() {
        for (int power = LO; power <= MON; power++) {
            Spells.Spell fireball = Spells.find(symbols(power, FUL, IR));
            assertEquals(Spells.KIND_PROJECTILE, fireball.kind());
            assertEquals(Explosion.FIREBALL, fireball.type());
        }
        assertEquals(Spells.KIND_OTHER, Spells.find(symbols(LO, FUL)).kind(), "the magic torch");
        assertNull(Spells.find(symbols(LO)), "a power alone");
        assertNull(Spells.find(symbols(LO, YA, EW)), "no such spell");
        assertEquals(25, Spells.TABLE.size());
    }

    @Test
    void theSpellsOfSprint20AreInTheTable() {
        assertEquals(Spells.KIND_POTION, Spells.find(symbols(LO, 1)).kind(), "VI, a health potion");
        assertEquals(Spells.OTHER_INVISIBILITY, Spells.find(symbols(LO, OH, EW, SAR)).type());
        assertEquals(Spells.OTHER_FOOTPRINTS, Spells.find(symbols(LO, YA, BRO, 1)).type());
        assertEquals(Spells.OTHER_THIEVES_EYE, Spells.find(symbols(LO, OH, EW, 4)).type());
        assertEquals(Spells.OTHER_ZOKATHRA, Spells.find(symbols(LO, 5, 2, 4)).type());
    }

    @Test
    void symbolsCostManaThePowerMultiplies() {
        assertEquals(1, Spells.manaCost(0, LO, Spells.FIRST_SYMBOL));
        assertEquals(6, Spells.manaCost(0, MON, Spells.FIRST_SYMBOL));
        char mon = (char) (Spells.FIRST_SYMBOL + MON);
        assertEquals((5 * 28) >> 3, Spells.manaCost(1, FUL, mon), "FUL after MON");
        enter(ON, FUL);
        assertEquals(500 - 3 - ((5 * 16) >> 3), elija.mana());
        assertEquals(symbols(ON, FUL), elija.symbols());
        assertEquals(2, elija.symbolStep());
    }

    @Test
    void aSymbolNeedsTheManaForIt() {
        elija.setMana(2);
        assertFalse(party.addSymbol(MON));
        assertEquals("", elija.symbols());
        assertTrue(party.addSymbol(UM));
        assertEquals(0, elija.mana());
    }

    @Test
    void backspaceTakesTheLastSymbolBackButNotItsMana() {
        enter(LO, FUL, IR);
        int mana = elija.mana();
        assertTrue(party.deleteSymbol());
        assertEquals(symbols(LO, FUL), elija.symbols());
        assertEquals(2, elija.symbolStep());
        assertEquals(mana, elija.mana());
        party.deleteSymbol();
        party.deleteSymbol();
        assertFalse(party.deleteSymbol(), "nothing left");
    }

    @Test
    void afterFourSymbolsTheNextStartsANewSpell() {
        enter(LO, FUL, BRO, NETA);
        assertEquals(0, elija.symbolStep());
        enter(UM);
        assertEquals(symbols(UM), elija.symbols());
    }

    @Test
    void aMeaninglessSpellIsMumbled() {
        master();
        enter(LO, YA, EW);
        int mana = elija.mana();
        assertFalse(party.cast());
        assertEquals(List.of("ELIJA MUMBLES A MEANINGLESS SPELL."), messages());
        assertEquals("", elija.symbols());
        assertEquals(mana, elija.mana(), "the mana stays spent");
    }

    @Test
    void anUnskilledCasterNeedsMorePractice() {
        enter(MON, FUL, IR); // needs wizard level 3 + 6
        assertFalse(party.cast());
        assertEquals(List.of("ELIJA NEEDS MORE PRACTICE WITH THIS WIZARD SPELL."), messages());
        assertTrue(map.projectiles().isEmpty());
        assertFalse(elija.actionDisabled());
    }

    @Test
    void aPotionSpellFillsAnEmptyFlaskInHand() {
        master();
        enter(UM, 1); // VI
        assertFalse(party.cast());
        assertEquals(List.of("ELIJA NEEDS AN EMPTY FLASK IN HAND FOR POTION."), messages());
        elija.place(Slot.READY_HAND, ItemCatalog.item(Item.Category.POTION, ItemCatalog.EMPTY_FLASK));
        enter(UM, 1);
        assertTrue(party.cast());
        Item potion = elija.items().get(Slot.READY_HAND);
        assertEquals(14, potion.type());
        assertTrue(potion.charges() >= 80 && potion.charges() < 96, "UM is power 2: 80 + random(16)");
    }

    @Test
    void aYaPotionShieldsItsDrinkerForAWhile() {
        party.setHeld(ItemCatalog.item(Item.Category.POTION, 12, 100));
        assertTrue(party.feed(elija));
        assertEquals(18, elija.shieldDefense(), "(100/25 + 8) * 1.5");
        tick(18 * 18 - 1);
        assertEquals(18, elija.shieldDefense());
        tick(1);
        assertEquals(0, elija.shieldDefense());
    }

    @Test
    void typeElevenIsTheStaminaPotion() {
        elija.decrementStamina(elija.rawMaxStamina() / 2);
        int before = elija.stamina();
        Upkeep.consume(elija, ItemCatalog.item(Item.Category.POTION, 11, 100));
        assertTrue(elija.stamina() > before);
    }

    @Test
    void invisibilityLastsItsSpellPowerInTicks() {
        master();
        enter(MON, OH, EW, SAR); // power 6: (6 + 1) * 4 = 28 ticks
        assertTrue(party.cast());
        assertTrue(party.invisible());
        tick(27);
        assertTrue(party.invisible());
        tick(1);
        assertFalse(party.invisible());
    }

    @Test
    void thievesEyeLastsHalfItsPowerSquared() {
        master();
        enter(LO, OH, EW, 4); // power 1: 8 >> 1 = 4, 16 ticks
        assertTrue(party.cast());
        assertTrue(party.thievesEye());
        tick(16);
        assertFalse(party.thievesEye());
    }

    @Test
    void zoKathRaPutsAZokathraInAnEmptyHand() {
        master();
        enter(LO, 5, 2, 4);
        assertTrue(party.cast());
        assertEquals("ZOKATHRA SPELL", elija.items().get(Slot.READY_HAND).name());
    }

    @Test
    void footprintsShowWhereThePartyWalksWhileTheSpellRuns() {
        master();
        party.step(Party.Move.FORWARD); // (2,1), before the spell
        enter(LO, YA, BRO, 1);
        assertTrue(party.cast());
        party.step(Party.Move.FORWARD); // (3,1)
        party.step(Party.Move.FORWARD); // (4,1)
        assertFalse(party.footprintsAt(map, 2, 1), "walked before the spell");
        assertTrue(party.footprintsAt(map, 3, 1));
        assertTrue(party.footprintsAt(map, 4, 1));
    }

    @Test
    void godModeCastsEverySpellForFree() {
        party.setGodMode(true);
        enter(MON, FUL, IR);
        assertEquals(500, elija.mana(), "no mana spent");
        assertTrue(party.cast());
        assertEquals(1, map.projectiles().size());
        assertTrue(map.projectiles().get(0).isSpell());
        elija.setMana(0);
        enter(MON, FUL, IR);
        assertTrue(party.cast(), "even with no mana at all");
    }

    @Test
    void aCastEarnsExperienceAndTakesTime() {
        master();
        long before = elija.experience(Champion.WIZARD);
        enter(LO, FUL, IR);
        assertTrue(party.cast());
        assertTrue(elija.experience(Champion.WIZARD) > before);
        assertTrue(elija.actionDisabled(), "a fireball's 42 ticks");
        assertEquals(party.time() + 42, elija.enabledAt());
    }

    @Test
    void aFireballLeavesFromTheCastersSideTheWayThePartyFaces() {
        master();
        elija.face(Direction.NORTH);
        enter(LO, FUL, IR);
        party.cast();
        Projectile p = map.projectiles().get(0);
        assertEquals(Direction.EAST, p.direction());
        assertEquals(Direction.EAST, elija.facing(), "the caster turns the party's way");
        assertEquals(Direction.EAST.ordinal(), p.cell(), "the front left cell is north-east");
        assertEquals(Explosion.FIREBALL, p.spell());
    }

    @Test
    void theMagicTorchLightsUpThenFades() {
        master();
        int dark = party.paletteIndex();
        enter(MON, FUL); // spell power 28: light power 8 for 2000 + 25 × 128 ticks
        assertTrue(party.cast());
        assertEquals(Light.POWER_TO_AMOUNT[8], party.magicalLight());
        assertTrue(party.paletteIndex() < dark);
        tick(2000 + 25 * 128 - 1);
        assertEquals(Light.POWER_TO_AMOUNT[8], party.magicalLight(), "still burning");
        tick(1);
        assertEquals(Light.POWER_TO_AMOUNT[7], party.magicalLight(), "a step weaker");
        tick(8 * 4);
        assertEquals(0, party.magicalLight(), "gone, a step every 4 ticks");
    }

    @Test
    void darknessTakesLightAwayForAWhile() {
        master();
        enter(MON, DES, IR, SAR); // light power 7 taken away, coming back after 98 ticks
        assertTrue(party.cast());
        assertEquals(-Light.POWER_TO_AMOUNT[7], party.magicalLight());
        tick(98 + 7 * 4);
        assertEquals(0, party.magicalLight());
    }

    @Test
    void thePartyShieldAddsToEveryBodyPartsDefense() {
        master();
        enter(UM, YA, IR); // spell power 12 for 144 ticks
        assertTrue(party.cast());
        assertEquals(12, party.shieldDefense());
        enter(UM, YA, IR);
        party.cast();
        assertEquals(24, party.shieldDefense(), "shields add up");
        tick(200);
        assertEquals(0, party.shieldDefense());
    }

    @Test
    void aStrongShieldOnlyGrowsByAQuarter() {
        master();
        for (int i = 0; i < 2; i++) {
            enter(MON, YA, IR);
            party.cast();
        }
        assertEquals(56, party.shieldDefense());
        enter(MON, YA, IR);
        party.cast();
        assertEquals(56 + 7, party.shieldDefense());
    }

    @Test
    void theFireShieldTakesFireAway() {
        master();
        enter(MON, FUL, BRO, NETA); // (28 × 28 + 100) >> 5
        assertTrue(party.cast());
        assertEquals((28 * 28 + 100) >> 5, party.fireShieldDefense());
        CreatureAI creatures = party.dungeon().creatures();
        assertEquals(0, creatures.hurtForTest(party, 0, 20, Champion.WOUND_TORSO, Flight.ATTACK_FIRE),
                "a small fire attack is stopped");
        tick(28 * 28 + 100);
        assertEquals(0, party.fireShieldDefense());
    }

    @Test
    void aDeadCasterPassesTheSpellAreaOn() {
        Champion second = Champion.parse(ChampionTest.ELIJA.replace("ELIJA", "HALK"), 1);
        party.recruit(new ChampionMirror(0, 0, Direction.SOUTH, second));
        assertEquals(0, party.magicCaster());
        enter(LO, FUL);
        elija.takeDamage(elija.health());
        party.bury();
        assertEquals(1, party.magicCaster());
        assertEquals("", elija.symbols(), "a dead champion's symbols are forgotten");
        assertFalse(party.setMagicCaster(0));
    }
}
