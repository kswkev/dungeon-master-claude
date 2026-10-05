package dm.model;

import java.io.Serializable;
import java.util.Random;

/**
 * Casting spells, ported from ReDMCSB's MENUS.C (F0399 adding a symbol, F0400
 * removing one, F0408/F0412 casting, F0410 the failure messages, F0403 the
 * shields) and TIMELINE.C (the light and shield events, C70 and C74-C78),
 * cross-checked against ScummVM's {@code MenuMan} and {@code Timeline}. The
 * party calls it and keeps the state: the caster, the magical light and the
 * shields, and the events that undo them.
 */
final class Magic {

    private Magic() {
    }

    /** What {@link Party#cast()} did. */
    enum Result {
        /** The spell worked. */
        CAST,
        /** It failed: no spell, not enough skill, no flask for a potion. */
        FAILED
    }

    /**
     * A party spell running out (DM's events 70 light, 72 a champion's shield,
     * 74 party shield, 77 spell shield, 78 fire shield). {@code member} is the
     * champion for event 72 (0 in games saved before Sprint 20).
     */
    record PartySpell(long time, int kind, int amount, int member) implements Serializable {
        PartySpell(long time, int kind, int amount) {
            this(time, kind, amount, 0);
        }
    }

    static final int LIGHT = 70;
    static final int INVISIBILITY = 71;
    static final int CHAMPION_SHIELD = 72;
    static final int THIEVES_EYE = 73;
    static final int FOOTPRINTS = 79;
    static final int PARTY_SHIELD = 74;
    static final int SPELL_SHIELD = 77;
    static final int FIRE_SHIELD = 78;

    /**
     * F0399: the caster enters the symbol in column {@code column} of their
     * current row, if they have the mana for it (in god mode they always do).
     * After the fourth symbol the rows start over, and the next symbol starts
     * a new spell.
     */
    static boolean addSymbol(Party party, int member, int column) {
        Champion c = party.members().get(member);
        if (c.health() == 0 || column < 0 || column >= Spells.PER_ROW) {
            return false;
        }
        int step = c.symbolStep();
        String symbols = c.symbols();
        int cost = Spells.manaCost(step, column, step == 0 ? Spells.FIRST_SYMBOL : symbols.charAt(0));
        if (cost > c.mana() && !party.godMode()) {
            return false;
        }
        int before = c.mana();
        c.setMana(c.mana() - cost);
        int spent = (step == 0 ? 0 : c.symbolMana()) + before - c.mana();
        String entered = (step == 0 ? "" : symbols.substring(0, step))
                + (char) (Spells.FIRST_SYMBOL + step * Spells.PER_ROW + column);
        c.setSymbols(entered, (step + 1) & 3, spent);
        return true;
    }

    /** F0400: the caster takes back their last symbol (the mana stays spent). */
    static boolean deleteSymbol(Party party, int member) {
        Champion c = party.members().get(member);
        String symbols = c.symbols();
        if (symbols.isEmpty()) {
            return false;
        }
        int step = (c.symbolStep() + 3) & 3;
        c.setSymbols(symbols.substring(0, Math.min(step, symbols.length())), step, c.symbolMana());
        return true;
    }

    /**
     * F0408 and F0412: member {@code member} casts the spell their symbols
     * make. With no spell they mumble; short of the skill it needs, each
     * missing level may fail it (wisdom helps), teaching a little. A spell
     * that works earns experience in its skill and keeps the caster from
     * acting for its duration. Either way the symbols are cleared.
     */
    static Result cast(Party party, int member) {
        Champion c = party.members().get(member);
        String symbols = c.symbols();
        if (symbols.isEmpty() || c.health() == 0) {
            return Result.FAILED;
        }
        Spells.Spell spell = Spells.find(symbols);
        int step = c.symbolStep();
        int symbolMana = c.symbolMana();
        c.setSymbols("", 0, 0);
        if (spell == null) {
            party.message(c.name() + " MUMBLES A MEANINGLESS SPELL.", -1);
            return Result.FAILED;
        }
        Random random = party.random();
        int power = symbols.charAt(0) - Spells.FIRST_SYMBOL + 1; // 1 (LO) to 6 (MON)
        int required = spell.baseSkillLevel() + power;
        int experience = random.nextInt(8) + (required << 4) + (((power - 1) * spell.baseSkillLevel()) << 3)
                + required * required;
        int skill = c.skillLevel(spell.skill());
        if (skill < required && !party.godMode()) {
            for (int missing = required - skill; missing > 0; missing--) {
                if (random.nextInt(128) > Math.min(c.stat(Champion.Stat.WISDOM) + 15, 115)) {
                    party.addSkillExperience(member, spell.skill(), experience >> (required - skill));
                    int base = spell.skill() > Champion.WIZARD ? (spell.skill() - 4) / 4 : spell.skill();
                    party.message(c.name() + " NEEDS MORE PRACTICE WITH THIS " + Champion.BASE_SKILLS.get(base)
                            + " SPELL.", -1);
                    return Result.FAILED;
                }
            }
        }
        switch (spell.kind()) {
            case Spells.KIND_POTION -> {
                Slot flask = emptyFlaskInHand(c);
                if (flask == null) {
                    party.message(c.name() + " NEEDS AN EMPTY FLASK IN HAND FOR POTION.", -1);
                    c.setSymbols(symbols, step, symbolMana); // F0408 keeps them, to cast again with a flask
                    return Result.FAILED;
                }
                c.take(flask);
                c.place(flask, ItemCatalog.item(Item.Category.POTION, spell.type(), random.nextInt(16) + power * 40));
            }
            case Spells.KIND_PROJECTILE -> castProjectile(party, member, c, spell, power, skill);
            case Spells.KIND_OTHER -> castOther(party, c, spell, power);
            default -> { }
        }
        party.addSkillExperience(member, spell.skill(), experience);
        Combat.disable(party, c, spell.duration());
        return Result.CAST;
    }

    /** F0411 (DM 1.1 on): the hand holding an empty flask, the action hand first, or null. */
    private static Slot emptyFlaskInHand(Champion c) {
        for (Slot slot : new Slot[] {Slot.ACTION_HAND, Slot.READY_HAND}) {
            Item item = c.items().get(slot);
            if (item != null && item.category() == Item.Category.POTION && item.type() == ItemCatalog.EMPTY_FLASK) {
                return slot;
            }
        }
        return null;
    }

    /**
     * F0412's projectile spells, through F0327: the caster turns the party's
     * way and the spell leaves from their side of the party's front, with
     * energy from its power and their skill (doubled for open door) and a
     * step energy that is smaller for casters with more mana.
     */
    private static void castProjectile(Party party, int member, Champion c, Spells.Spell spell, int power,
                                       int skill) {
        c.face(party.facing());
        if (spell.type() == Explosion.OPEN_DOOR) {
            skill <<= 1;
        }
        projectileSpell(party, c, spell.type(), Math.max(21, Math.min((power + 2) * (4 + (skill << 1)), 255)), 0);
    }

    /**
     * DM's F0327: {@code c} pays {@code mana} (false, and nothing happens,
     * if they can't) and the spell leaves from their side of the party's
     * front, with a step energy that is smaller for casters with more mana.
     */
    static boolean projectileSpell(Party party, Champion c, int spell, int kineticEnergy, int mana) {
        if (c.mana() < mana && !party.godMode()) {
            return false;
        }
        c.setMana(c.mana() - mana);
        int stepEnergy = 10 - Math.min(8, c.maxMana() >> 3);
        if (kineticEnergy < (stepEnergy << 2)) {
            kineticEnergy += 3;
            stepEnergy--;
        }
        int dir = c.facing().ordinal();
        int cell = ((((party.cellOf(c) - dir + 1) & 2) >> 1) + dir) & 3;
        Flight.launchSpell(party, spell, party.map(), party.x(), party.y(), cell, c.facing(), kineticEnergy, 90,
                stepEnergy);
        return true;
    }

    /**
     * DM's F0403 with mana, for the spell and fire shield actions: 4 mana;
     * with less the shield lasts half as long, takes what mana is left and
     * counts as failed; with none, nothing.
     */
    static boolean shieldWithMana(Party party, Champion c, boolean spellShield, int ticks) {
        if (c.mana() == 0) {
            return false;
        }
        boolean full = c.mana() >= 4;
        if (full) {
            c.setMana(c.mana() - 4);
        } else {
            ticks >>= 1;
            c.setMana(0);
        }
        shield(party, spellShield, ticks);
        return full;
    }

    /**
     * F0412's other spells: light, magic torch, darkness, party shield, fire
     * shield, thieves' eye, invisibility, magic footprints and ZO KATH RA.
     * Invisibility lasts only its spell power in ticks: in DM its tick count
     * shares a register with the power and skips the squaring the others get.
     */
    private static void castOther(Party party, Champion c, Spells.Spell spell, int power) {
        int spellPower = (power + 1) << 2;
        switch (spell.type()) {
            case Spells.OTHER_LIGHT -> {
                int light = (spellPower >> 1) - 1;
                party.addMagicalLight(Light.POWER_TO_AMOUNT[light]);
                party.addPartySpell(new PartySpell(party.time() + 10000 + ((spellPower - 8) << 9), LIGHT, -light));
            }
            case Spells.OTHER_MAGIC_TORCH -> {
                int light = (spellPower >> 2) + 1;
                party.addMagicalLight(Light.POWER_TO_AMOUNT[light]);
                party.addPartySpell(new PartySpell(party.time() + 2000 + ((spellPower - 3) << 7), LIGHT, -light));
            }
            case Spells.OTHER_DARKNESS -> {
                int light = spellPower >> 2;
                party.addMagicalLight(-Light.POWER_TO_AMOUNT[light]);
                party.addPartySpell(new PartySpell(party.time() + 98, LIGHT, light));
            }
            case Spells.OTHER_PARTY_SHIELD -> {
                int defense = party.shieldDefense() > 50 ? spellPower >> 2 : spellPower;
                party.addShield(PARTY_SHIELD, defense);
                party.addPartySpell(new PartySpell(party.time() + spellPower * spellPower, PARTY_SHIELD, defense));
            }
            case Spells.OTHER_FIRESHIELD -> shield(party, false, spellPower * spellPower + 100);
            case Spells.OTHER_THIEVES_EYE -> {
                int half = spellPower >> 1;
                countedSpell(party, THIEVES_EYE, half * half);
            }
            case Spells.OTHER_INVISIBILITY -> countedSpell(party, INVISIBILITY, spellPower);
            case Spells.OTHER_FOOTPRINTS -> {
                party.startFootprints(power);
                party.addPartySpell(new PartySpell(party.time() + spellPower * spellPower, FOOTPRINTS, 1));
            }
            case Spells.OTHER_ZOKATHRA -> {
                Item zokathra = ItemCatalog.item(Item.Category.JUNK, ItemCatalog.ZOKATHRA);
                if (c.items().get(Slot.READY_HAND) == null) {
                    c.place(Slot.READY_HAND, zokathra);
                } else if (c.items().get(Slot.ACTION_HAND) == null) {
                    c.place(Slot.ACTION_HAND, zokathra);
                } else {
                    party.map().dropItem(party.x(), party.y(), party.cellOf(c), zokathra);
                }
            }
            default -> { }
        }
    }

    /** DM's event counts: one more spell of {@code kind} running, for {@code ticks}. */
    static void countedSpell(Party party, int kind, int ticks) {
        party.addSpellCount(kind, 1);
        party.addPartySpell(new PartySpell(party.time() + ticks, kind, 1));
    }

    /**
     * F0403 without the mana (which only the item actions spend): a spell or
     * fire shield of {@code ticks} >> 5, a quarter of that if the party's
     * shield is already over 50, for {@code ticks}.
     */
    static void shield(Party party, boolean spellShield, int ticks) {
        int defense = ticks >> 5;
        int current = spellShield ? party.spellShieldDefense() : party.fireShieldDefense();
        if (current > 50) {
            defense >>= 2;
        }
        int kind = spellShield ? SPELL_SHIELD : FIRE_SHIELD;
        party.addShield(kind, defense);
        party.addPartySpell(new PartySpell(party.time() + ticks, kind, defense));
    }

    /**
     * DM's events 70 and 74-78: a shield runs out; light fades a step at a
     * time (every 4 ticks, from its power down to nothing), and darkness
     * lifts the same way.
     */
    static void expire(Party party, PartySpell e) {
        switch (e.kind()) {
            case LIGHT -> {
                int power = e.amount();
                if (power == 0) {
                    return;
                }
                boolean negative = power < 0;
                power = Math.abs(power);
                int weaker = power - 1;
                int amount = Light.POWER_TO_AMOUNT[power] - Light.POWER_TO_AMOUNT[weaker];
                if (negative) {
                    amount = -amount;
                    weaker = -weaker;
                }
                party.addMagicalLight(amount);
                if (weaker != 0) {
                    party.addPartySpell(new PartySpell(e.time() + 4, LIGHT, weaker));
                }
            }
            case PARTY_SHIELD, SPELL_SHIELD, FIRE_SHIELD -> party.addShield(e.kind(), -e.amount());
            case INVISIBILITY, THIEVES_EYE, FOOTPRINTS -> party.addSpellCount(e.kind(), -1);
            case CHAMPION_SHIELD -> {
                if (e.member() < party.members().size()) {
                    party.members().get(e.member()).addShieldDefense(-e.amount());
                }
            }
            default -> { }
        }
    }
}
