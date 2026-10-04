package dm.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sprint 18: the game menu's options: difficulty, god mode and deep sleep. */
class DifficultyTest {

    private static Party party(Difficulty difficulty, long seed) {
        DungeonMap map = DungeonMap.fromAscii(0, "######", "#....#", "######");
        Party p = new Party(List.of(map), 0, 1, 1, Direction.EAST);
        p.setRandom(new Random(seed));
        p.setDifficulty(difficulty);
        p.recruit(new ChampionMirror(0, 0, Direction.SOUTH, Champion.parse(ChampionTest.ELIJA, 0)));
        p.takeMessages();
        return p;
    }

    @Test
    void scalingKeepsSmallNumbersRightOnAverage() {
        Random random = new Random(1);
        int total = 0;
        for (int i = 0; i < 10000; i++) {
            total += Difficulty.scale(3, 80, random);
        }
        assertEquals(24000, total, 300, "80% of 3 is 2.4");
        assertEquals(120, Difficulty.scale(100, 120, random));
        assertEquals(0, Difficulty.scale(0, 120, random));
    }

    @Test
    void normalIsDmUnchangedAndDrawsNoRandomNumbers() {
        Random random = new Random(1);
        int next = new Random(1).nextInt();
        assertEquals(7, Difficulty.scale(7, 100, random));
        assertEquals(next, random.nextInt(), "the dice are left as they were");
    }

    @Test
    void easyAndHardAreMirrorImages() {
        Random random = new Random(1);
        assertEquals(80, Difficulty.EASY.creatureDamage(100, random));
        assertEquals(120, Difficulty.EASY.partyDamage(100, random));
        assertEquals(120, Difficulty.EASY.experience(100, random));
        assertEquals(80, Difficulty.EASY.hunger(100, random));
        assertEquals(120, Difficulty.HARD.creatureDamage(100, random));
        assertEquals(80, Difficulty.HARD.partyDamage(100, random));
        assertEquals(80, Difficulty.HARD.experience(100, random));
        assertEquals(120, Difficulty.HARD.hunger(100, random));
    }

    @Test
    void experienceIsScaled() {
        for (Difficulty d : Difficulty.values()) {
            Party p = party(d, 5);
            Champion c = p.members().get(0);
            long before = c.experience(Champion.PRIEST);
            p.addSkillExperience(0, Champion.PRIEST, 100);
            int expected = d == Difficulty.EASY ? 120 : d == Difficulty.HARD ? 80 : 100;
            assertEquals(before + expected, c.experience(Champion.PRIEST), d.name());
        }
    }

    /** Food a champion has used up after {@code periods} of DM's time effects. */
    private static int foodUsed(Difficulty difficulty, int periods) {
        Party p = party(difficulty, 2);
        Champion c = p.members().get(0);
        c.setFood(Champion.MAX_FOOD);
        for (int t = 0; t < Upkeep.PERIOD * periods; t++) {
            p.tick();
        }
        return Champion.MAX_FOOD - c.food();
    }

    @Test
    void foodRunsDownSlowerOnEasyAndFasterOnHard() {
        int normal = foodUsed(Difficulty.NORMAL, 100);
        assertEquals(normal * 0.8, foodUsed(Difficulty.EASY, 100), normal * 0.08);
        assertEquals(normal * 1.2, foodUsed(Difficulty.HARD, 100), normal * 0.08);
    }

    /** All the damage a mummy next to the party does over a few hundred ticks, over a few seeds. */
    private static int mummyDamage(Difficulty difficulty) {
        int total = 0;
        for (long seed = 1; seed <= 8; seed++) {
            DungeonMap map = DungeonMap.fromAscii(0, "######", "#....#", "######");
            map.addGroup(new Group(CreatureType.MUMMY, 2, 1, Group.CENTRED, new int[] {500, 0, 0, 0}, 1,
                    Direction.WEST, List.of()));
            Party p = new Party(List.of(map), 0, 1, 1, Direction.EAST);
            p.setRandom(new Random(seed));
            p.setDifficulty(difficulty);
            Champion c = Champion.parse(ChampionTest.ELIJA, 0);
            c.raiseMaxHealth(5000); // nobody dies, so every blow counts
            c.addHealth(5000);
            p.recruit(new ChampionMirror(0, 0, Direction.SOUTH, c));
            for (int t = 0; t < 300; t++) {
                Party.Tick tick = p.tick();
                if (tick.damage() != null) {
                    total += tick.damage()[0];
                }
            }
        }
        return total;
    }

    @Test
    void creaturesHitHarderTheHarderTheGame() {
        int easy = mummyDamage(Difficulty.EASY);
        int hard = mummyDamage(Difficulty.HARD);
        assertTrue(easy > 0);
        assertTrue(hard > easy * 1.2, easy + " on easy, " + hard + " on hard");
    }

    /** All the damage a strong champion's sword does to a mummy over 30 swings. */
    private static int swordDamage(Difficulty difficulty) {
        Party p = party(difficulty, 3);
        Champion c = p.members().get(0);
        c.setStat(Champion.Stat.STRENGTH, 120);
        c.setStat(Champion.Stat.DEXTERITY, 120);
        c.replace(Slot.ACTION_HAND, ItemCatalog.item(Item.Category.WEAPON, 10));
        Group mummy = new Group(CreatureType.MUMMY, 2, 1, Group.CENTRED, new int[] {5000, 0, 0, 0}, 1,
                Direction.WEST, List.of());
        p.map().addGroup(mummy);
        p.setGodMode(true); // so the mummy's blows don't end the test early
        for (int i = 0; i < 30; i++) {
            p.act(0, Actions.SWING);
            for (int t = 0; t < 200 && c.actionDisabled(); t++) {
                p.tick();
            }
        }
        return 5000 - mummy.health(0);
    }

    @Test
    void theChampionsHitHarderTheEasierTheGame() {
        int easy = swordDamage(Difficulty.EASY);
        int hard = swordDamage(Difficulty.HARD);
        assertTrue(hard > 0);
        assertTrue(easy > hard * 1.2, easy + " on easy, " + hard + " on hard");
    }

    @Test
    void godModeKeepsEveryVitalUp() {
        Party p = party(Difficulty.NORMAL, 4);
        Champion c = p.members().get(0);
        p.setGodMode(true);
        int health = c.health();
        int stamina = c.rawStamina();
        int food = c.food();
        int water = c.water();
        int mana = c.mana();
        assertEquals(0, c.takeDamage(50));
        assertEquals(0, c.decrementStamina(10000));
        c.setMana(0);
        for (int t = 0; t < Upkeep.PERIOD * 20; t++) {
            p.tick();
            p.step(Party.Move.FORWARD);
            p.step(Party.Move.BACKWARD);
        }
        assertEquals(health, c.health());
        assertTrue(c.rawStamina() >= stamina);
        assertEquals(food, c.food());
        assertEquals(water, c.water());
        assertTrue(c.mana() >= mana);
        // gains still count: eating raises food
        Upkeep.consume(c, ItemCatalog.item(Item.Category.JUNK, ItemCatalog.FIRST_FOOD));
        assertTrue(c.food() > food);
    }

    @Test
    void godModeStopsWounds() {
        Party p = party(Difficulty.NORMAL, 4);
        Champion c = p.members().get(0);
        p.setGodMode(true);
        c.addWounds(Champion.WOUND_HEAD | Champion.WOUND_TORSO);
        assertEquals(0, c.wounds());
        p.dungeon().creatures().hurtForTest(p, 0, 500, 0x3F, 3); // a blow hard enough to wound anywhere
        assertEquals(0, c.wounds());
        p.setGodMode(false);
        c.addWounds(Champion.WOUND_HEAD);
        assertEquals(Champion.WOUND_HEAD, c.wounds());
    }

    @Test
    void deepSleepRestoresHealthStaminaAndManaAtOnce() {
        Party p = party(Difficulty.NORMAL, 4);
        Champion c = p.members().get(0);
        c.takeDamage(20);
        c.decrementStamina(c.rawStamina() / 2);
        c.setMana(0);
        p.setDeepSleep(true);
        assertTrue(p.sleep());
        assertEquals(c.maxHealth(), c.health());
        assertEquals(c.rawMaxStamina(), c.rawStamina());
        assertEquals(c.maxMana(), c.mana());
        assertTrue(p.sleeping(), "the party still sleeps until woken");
    }

    @Test
    void ordinarySleepRestoresNothingAtOnceAndTheDeadStayDead() {
        Party p = party(Difficulty.NORMAL, 4);
        Champion c = p.members().get(0);
        c.takeDamage(20);
        int health = c.health();
        p.sleep();
        assertEquals(health, c.health(), "deep sleep is off");
        p.wakeUp();
        Champion second = Champion.parse(ChampionTest.ELIJA, 1);
        p.recruit(new ChampionMirror(1, 0, Direction.SOUTH, second));
        second.takeDamage(second.health());
        p.setDeepSleep(true);
        p.sleep();
        assertEquals(0, second.health());
        assertEquals(c.maxHealth(), c.health());
    }

    @Test
    void godModeCoversChampionsWhoJoinLaterAndCanBeTurnedOff() {
        Party p = party(Difficulty.NORMAL, 4);
        p.setGodMode(true);
        Champion second = Champion.parse(ChampionTest.ELIJA, 1);
        p.recruit(new ChampionMirror(1, 0, Direction.SOUTH, second));
        assertEquals(0, second.takeDamage(5));
        p.setGodMode(false);
        assertEquals(5, second.takeDamage(5));
        assertEquals(5, p.members().get(0).takeDamage(5));
    }
}
