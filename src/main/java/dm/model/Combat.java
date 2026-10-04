package dm.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The champions' actions, ported from ReDMCSB's MENUS.C (F383, F391,
 * F401-F407), GROUP2.C (F231 melee damage) and CHAMPION.C (F326 shooting,
 * F328 throwing, F330 and the enable event F253/F259). The party calls it;
 * the creatures' side (damage, deaths, fear, reactions) is in
 * {@link CreatureAI}, and what flies is moved by {@link Flight}.
 *
 * <p>Actions that are spells or item magic (a staff's fireball and the like)
 * aren't offered yet; they come with the spells.
 */
final class Combat {

    private Combat() {
    }

    /** What the action area shows after an action (DM's G513): damage dealt, or one of these. */
    static final int CANT_REACH = -1;
    static final int NEED_AMMO = -2;

    /** The fright of the actions that scare creatures (F401): {action, amount, experience}. */
    private static final int[][] FRIGHT = {
            {Actions.WAR_CRY, 3, 12}, {Actions.CALM, 7, 35}, {Actions.BRANDISH, 6, 30},
            {Actions.BLOW_HORN, 6, 20}, {Actions.CONFUSE, 12, 45}};

    /**
     * DM's F389 and F383: the actions {@code c}'s action hand offers, in
     * menu order: the set's first action, then the others the champion is
     * skilled enough for (and that have a charge left, if they need one).
     * An empty hand punches, kicks and shouts. Empty when the champion
     * can't act: dead, still recovering, or holding something with no actions.
     */
    static List<Integer> actionsFor(Champion c) {
        List<Integer> list = new ArrayList<>();
        if (c.health() == 0 || c.actionDisabled()) {
            return list;
        }
        Item hand = c.items().get(Slot.ACTION_HAND);
        int set = hand == null ? Actions.EMPTY_HAND_SET : ItemCatalog.actionSet(hand);
        if (set == 0) {
            return list;
        }
        list.add(Actions.setAction(set, 0));
        for (int i = 1; i < 3; i++) {
            int action = Actions.setAction(set, i);
            if (action == Actions.NONE) {
                continue;
            }
            int property = Actions.setProperty(set, i);
            if ((property & 0x80) != 0 && charges(hand) == 0) {
                continue;
            }
            if (c.skillLevel(Actions.skill(action)) >= (property & 0x7F)) {
                list.add(action);
            }
        }
        list.removeIf(Actions::isMagic);
        return list;
    }

    /** DM's F382: the charges of what the action hand holds (1 for an item that has none). */
    private static int charges(Item item) {
        if (item == null) {
            return 1;
        }
        return switch (item.category()) {
            case WEAPON, ARMOUR, JUNK -> item.charges();
            default -> 1;
        };
    }

    /**
     * DM's F391 then F407: member {@code member} performs {@code action}.
     * Its defense counts while the champion recovers; they may then not act
     * for the action's ticks, it costs stamina and earns experience. Returns
     * what the action area shows: the damage a blow did, {@link #CANT_REACH},
     * {@link #NEED_AMMO}, or 0.
     */
    static int act(Party party, int member, int action) {
        Champion c = party.members().get(member);
        if (c.health() == 0) {
            return 0;
        }
        c.addActionDefense(Actions.defense(action));
        int shown = perform(party, member, c, action);
        if (c.actionDisabled()) {
            c.setActionIndex(action);
        } else {
            // DM's BUG0_54: an action that ends up not disabling the champion (a punch at
            // nothing: 1 tick, halved to 0) is never re-enabled, so DM keeps its defense for good.
            c.addActionDefense(-Actions.defense(action));
        }
        return shown;
    }

    private static int perform(Party party, int member, Champion c, int action) {
        Random random = party.random();
        DungeonMap m = party.map();
        int tx = party.x() + c.facing().dx;
        int ty = party.y() + c.facing().dy;
        Group target = m.groupAt(tx, ty);
        int disabledTicks = Actions.disabledTicks(action);
        int skill = Actions.skill(action);
        int stamina = Actions.stamina(action) + random.nextInt(2);
        int experience = Actions.experience(action);
        int shown = 0;
        switch (action) {
            case Actions.BASH, Actions.HACK, Actions.BERZERK, Actions.KICK, Actions.SWING, Actions.CHOP,
                 Actions.DISRUPT, Actions.JAB, Actions.PARRY, Actions.STAB_14, Actions.STAB_9, Actions.STUN,
                 Actions.THRUST, Actions.MELEE, Actions.SLASH, Actions.CLEAVE, Actions.PUNCH -> {
                boolean breaksDoors = action == Actions.BASH || action == Actions.HACK || action == Actions.BERZERK
                        || action == Actions.KICK || action == Actions.SWING || action == Actions.CHOP;
                if (breaksDoors && m.get(tx, ty).type() == SquareType.DOOR
                        && m.doorState(tx, ty) == DungeonMap.DOOR_CLOSED) {
                    sound(party, CreatureAI.SOUND_COMBAT);
                    disabledTicks = 6;
                    m.breakDoor(tx, ty, c.strength(Slot.ACTION_HAND, random), false, 2);
                    sound(party, CreatureAI.SOUND_WOODEN_THUD);
                    break;
                }
                int result = melee(party, member, c, action, target, tx, ty, skill);
                if (result < 0) {
                    experience >>= 1;
                    disabledTicks >>= 1;
                    shown = result == CANT_REACH ? CANT_REACH : 0;
                } else {
                    shown = result;
                }
            }
            case Actions.WAR_CRY, Actions.CALM, Actions.BRANDISH, Actions.BLOW_HORN, Actions.CONFUSE -> {
                if (action == Actions.WAR_CRY) {
                    sound(party, SOUND_WAR_CRY);
                } else if (action == Actions.BLOW_HORN) {
                    sound(party, SOUND_HORN);
                }
                frighten(party, member, c, action, target);
            }
            case Actions.SHOOT -> {
                Item bow = c.items().get(Slot.ACTION_HAND);
                Item ammo = c.items().get(Slot.READY_HAND);
                int bowClass = ItemCatalog.weaponClass(bow);
                int ammoClass = ItemCatalog.weaponClass(ammo);
                int stepEnergy;
                if (bowClass >= ItemCatalog.CLASS_FIRST_BOW && bowClass <= ItemCatalog.CLASS_LAST_BOW
                        && ammoClass == ItemCatalog.CLASS_BOW_AMMUNITION) {
                    stepEnergy = bowClass - ItemCatalog.CLASS_FIRST_BOW;
                } else if (bowClass >= ItemCatalog.CLASS_FIRST_SLING && bowClass <= ItemCatalog.CLASS_LAST_SLING
                        && ammoClass == ItemCatalog.CLASS_SLING_AMMUNITION) {
                    stepEnergy = bowClass - ItemCatalog.CLASS_FIRST_SLING;
                } else {
                    shown = NEED_AMMO;
                    experience = 0;
                    break;
                }
                c.face(party.facing());
                c.take(Slot.READY_HAND);
                sound(party, CreatureAI.SOUND_COMBAT);
                int kineticEnergy = ItemCatalog.weaponKineticEnergy(bow) + ItemCatalog.weaponKineticEnergy(ammo);
                int attack = (ItemCatalog.shootAttack(bow) + c.skillLevel(Champion.SHOOT)) << 1;
                shoot(party, c, ammo, kineticEnergy, attack, stepEnergy);
            }
            case Actions.FLIP -> party.message(random.nextInt(2) != 0 ? "IT COMES UP HEADS." : "IT COMES UP TAILS.", -1);
            case Actions.THROW -> {
                c.face(party.facing());
                int cell = party.cellOf(c);
                boolean rightSide = cell == party.facing().turnRight().ordinal()
                        || cell == party.facing().opposite().ordinal();
                if (throwFrom(party, member, Slot.ACTION_HAND, rightSide ? 1 : 0)) {
                    c.setRefillActionHand(true);
                }
            }
            case Actions.CLIMB_DOWN -> {
                if (!party.climbDown()) {
                    disabledTicks = 0;
                }
            }
            default -> { } // BLOCK and HIT only count for their defense; the magic waits for the spells
        }
        if (disabledTicks != 0) {
            disable(party, c, disabledTicks);
        }
        if (stamina != 0) {
            party.spendStamina(member, stamina);
        }
        if (experience != 0) {
            party.addSkillExperience(member, skill, experience);
        }
        return shown;
    }

    /** DM's war cry and horn sounds. */
    static final int SOUND_WAR_CRY = 28;
    static final int SOUND_HORN = 25;

    private static void sound(Party party, int dmSound) {
        party.dungeon().creatures().soundAt(party, dmSound, party.map(), party.x(), party.y());
    }

    /** {@link #melee}'s result when no blow was struck at all. */
    private static final int NO_BLOW = -100;

    /**
     * DM's F402: a blow at the group ahead of the champion. Returns the
     * damage done (0 for a miss), {@link #CANT_REACH} for a back-row
     * champion with someone in front, or {@link #NO_BLOW} when there was
     * nothing to strike (no creature there, or a disrupt against a solid one).
     */
    private static int melee(Party party, int member, Champion c, int action, Group g, int tx, int ty, int skill) {
        sound(party, CreatureAI.SOUND_COMBAT);
        if (g == null) {
            return NO_BLOW;
        }
        int cell = party.cellOf(c);
        int target = party.dungeon().creatures().meleeTarget(party, party.map(), tx, ty, cell);
        if (target < 0) {
            return NO_BLOW;
        }
        int viewCell = (cell + 4 - c.facing().ordinal()) & 3;
        int delta = viewCell == 2 ? 3 : viewCell == 3 ? 1 : 0; // back right, back left
        if (delta != 0 && party.memberInCell((cell + delta) & 3) >= 0) {
            return CANT_REACH;
        }
        if (action == Actions.DISRUPT && !g.type().nonMaterial()) {
            return NO_BLOW;
        }
        int hitProbability = Actions.hitProbability(action);
        boolean hitsNonMaterial = action == Actions.DISRUPT || isWeapon(c, ItemCatalog.VORPAL_BLADE);
        return meleeDamage(party, member, c, g, target, tx, ty, hitProbability, hitsNonMaterial,
                Actions.damageFactor(action), skill);
    }

    private static boolean isWeapon(Champion c, int type) {
        Item hand = c.items().get(Slot.ACTION_HAND);
        return hand != null && hand.category() == Item.Category.WEAPON && hand.type() == type;
    }

    /**
     * DM's F231: the blow's hit roll (dexterity against the creature's, or
     * luck), its damage (strength through the weapon, the action's factor,
     * less the creature's defense, a critical hit for the skilled), and the
     * creature's wound. The champion learns and tires; a group that survives
     * turns on the party. Returns the damage done.
     */
    static int meleeDamage(Party party, int member, Champion c, Group g, int creature, int x, int y,
                           int hitProbability, boolean hitsNonMaterial, int damageFactor, int skill) {
        Random random = party.random();
        CreatureType info = g.type();
        int difficulty = party.map().difficulty() << 1;
        int damage = 0;
        int outcome = CreatureAI.KILLED_NONE;
        boolean hit = (!info.nonMaterial() || hitsNonMaterial)
                && (c.dexterity(party.load(c), random) > random.nextInt(32) + info.dexterity() + difficulty - 16
                || random.nextInt(4) == 0 || c.isLucky(75 - hitProbability, random));
        if (hit) {
            damage = c.strength(Slot.ACTION_HAND, random);
            int margin = 0;
            boolean weak = damage == 0;
            if (!weak) {
                damage += random.nextInt((damage >> 1) + 1);
                damage = (int) ((long) damage * damageFactor >> 5);
                int defense = random.nextInt(32) + info.defense() + difficulty;
                if (isWeapon(c, ItemCatalog.DIAMOND_EDGE)) {
                    defense -= defense >> 2;
                } else if (isWeapon(c, ItemCatalog.HARDCLEAVE)) {
                    defense -= defense >> 3;
                }
                damage = margin = random.nextInt(32) + damage - defense;
                weak = damage <= 1;
            }
            if (weak) {
                damage = random.nextInt(4);
                if (damage == 0) {
                    hit = false;
                } else {
                    damage++;
                    margin += random.nextInt(16);
                    if (margin > 0 || random.nextInt(2) != 0) {
                        damage += random.nextInt(4);
                        if (random.nextInt(4) == 0) {
                            damage += Math.max(0, margin + random.nextInt(16));
                        }
                    }
                }
            }
            if (hit) {
                damage >>= 1;
                damage += random.nextInt(Math.max(1, damage)) + random.nextInt(4);
                damage += random.nextInt(Math.max(1, damage));
                damage >>= 2;
                damage += random.nextInt(4) + 1;
                if (isWeapon(c, ItemCatalog.VORPAL_BLADE) && !info.nonMaterial()) {
                    damage >>= 1;
                    hit = damage != 0;
                }
            }
            if (hit) {
                if (random.nextInt(64) < c.skillLevel(skill)) {
                    damage += damage + 10;
                }
                outcome = party.dungeon().creatures().hitCreature(party, party.map(), g, creature, damage);
                party.addSkillExperience(member, skill, (damage * info.experience() >> 4) + 3);
                party.spendStamina(member, random.nextInt(4) + 4);
            }
        }
        if (!hit) {
            damage = 0;
            party.spendStamina(member, random.nextInt(2) + 2);
        }
        if (outcome != CreatureAI.KILLED_ALL) {
            party.dungeon().creatures().react(party, party.map(), x, y, CreatureAI.PARTY_ADJACENT);
        }
        return damage;
    }

    /** DM's F401: a war cry, horn or the like may frighten the group ahead; the champion learns influence. */
    private static void frighten(Party party, int member, Champion c, int action, Group g) {
        if (g == null) {
            return;
        }
        int amount = 0;
        int experience = 0;
        for (int[] f : FRIGHT) {
            if (f[0] == action) {
                amount = f[1];
                experience = f[2];
            }
        }
        amount += c.skillLevel(Champion.INFLUENCE);
        if (!party.dungeon().creatures().frighten(party, party.map(), g, amount)) {
            experience >>= 1;
        }
        party.addSkillExperience(member, Champion.INFLUENCE, experience);
    }

    /** DM's F326: the champion shoots {@code item}, from the front cell on their side, the way they face. */
    private static void shoot(Party party, Champion c, Item item, int kineticEnergy, int attack, int stepEnergy) {
        int dir = c.facing().ordinal();
        int cell = (((party.cellOf(c) - dir + 1) & 2) >> 1) + dir;
        Flight.launch(party, item, party.map(), party.x(), party.y(), cell, c.facing(), kineticEnergy, attack,
                stepEnergy);
    }

    /**
     * DM's F328: member {@code member} throws what is in {@code slot} (or,
     * with null, the item on the pointer: F329's leader hand) from
     * {@code side} (0 left, 1 right) of the party's front, the way the
     * party faces. The stronger the champion and the better their throwing,
     * the further and harder it flies. Returns whether anything was thrown.
     */
    static boolean throwFrom(Party party, int member, Slot slot, int side) {
        Champion c = party.members().get(member);
        Random random = party.random();
        Item item;
        int kineticEnergy;
        if (slot == null) {
            item = party.held();
            if (item == null) {
                return false;
            }
            Item hand = c.take(Slot.ACTION_HAND);
            c.replace(Slot.ACTION_HAND, item);
            kineticEnergy = c.strength(Slot.ACTION_HAND, random);
            c.take(Slot.ACTION_HAND);
            if (hand != null) {
                c.replace(Slot.ACTION_HAND, hand);
            }
            party.setHeld(null);
        } else {
            item = c.items().get(slot);
            if (item == null) {
                return false;
            }
            kineticEnergy = c.strength(slot, random);
            c.take(slot);
        }
        sound(party, CreatureAI.SOUND_COMBAT);
        party.spendStamina(member, throwingStaminaCost(item));
        disable(party, c, 4);
        int experience = 8;
        int weaponEnergy = 1;
        if (item.category() == Item.Category.WEAPON) {
            experience += 4;
            if (ItemCatalog.weaponClass(item) <= ItemCatalog.CLASS_POISON_DART) {
                weaponEnergy = ItemCatalog.weaponKineticEnergy(item);
                experience += weaponEnergy >> 2;
            }
        }
        party.addSkillExperience(member, Champion.THROW, experience);
        kineticEnergy += weaponEnergy;
        int level = c.skillLevel(Champion.THROW);
        kineticEnergy += random.nextInt(16) + (kineticEnergy >> 1) + level;
        int attack = Math.max(40, Math.min((level << 3) + random.nextInt(32), 200));
        int stepEnergy = Math.max(5, 11 - level);
        Flight.launch(party, item, party.map(), party.x(), party.y(), party.facing().ordinal() + side,
                party.facing(), kineticEnergy, attack, stepEnergy);
        return true;
    }

    /** DM's F305: half the weight, 1-10, plus half of every 10 beyond. */
    static int throwingStaminaCost(Item item) {
        int weight = item.weight() >> 1;
        int cost = Math.max(1, Math.min(weight, 10));
        while ((weight -= 10) > 0) {
            cost += weight >> 1;
        }
        return cost;
    }

    /**
     * DM's F330: the champion can't act for {@code ticks}. While already
     * recovering, a longer wait adds half of what was left; a shorter one
     * adds half of itself.
     */
    static void disable(Party party, Champion c, int ticks) {
        long now = party.time();
        long until = now + ticks;
        if (c.actionDisabled()) {
            long current = c.enabledAt();
            if (until >= current) {
                until += (current - now) >> 1;
            } else {
                until = current + (ticks >> 1);
            }
        }
        c.setEnabledAt(until);
    }

    /**
     * DM's event 11 (F253, F259): the champion can act again. The action's
     * defense goes; after a shot an empty ready hand takes the next arrow
     * or rock from the quiver, and after a throw an empty action hand takes
     * the next weapon.
     */
    static void enable(Champion c) {
        c.setEnabledAt(-1);
        if (c.actionIndex() != Actions.NONE) {
            c.addActionDefense(-Actions.defense(c.actionIndex()));
        }
        if (c.health() > 0) {
            if (c.actionIndex() == Actions.SHOOT && c.items().get(Slot.READY_HAND) == null) {
                for (Slot from : QUIVER_ORDER) {
                    if (ammunitionFits(c, from)) {
                        c.replace(Slot.READY_HAND, c.take(from));
                        break;
                    }
                }
            }
            if (c.refillActionHand() && c.items().get(Slot.ACTION_HAND) == null) {
                for (Slot from : QUIVER_ORDER) {
                    Item item = c.items().get(from);
                    if (item != null && item.category() == Item.Category.WEAPON) {
                        c.replace(Slot.ACTION_HAND, c.take(from));
                        break;
                    }
                }
            }
        }
        c.setRefillActionHand(false);
        c.setActionIndex(Actions.NONE);
    }

    /** DM's quiver slots in the order F253 and F259 look: 12, then 7, 8, 9. */
    private static final Slot[] QUIVER_ORDER = {Slot.QUIVER_1, Slot.QUIVER_3, Slot.QUIVER_2, Slot.QUIVER_4};

    /** DM's F294: whether {@code from} holds ammunition for the bow or sling in the action hand. */
    private static boolean ammunitionFits(Champion c, Slot from) {
        int weapon = ItemCatalog.weaponClass(c.items().get(Slot.ACTION_HAND));
        int ammo = ItemCatalog.weaponClass(c.items().get(from));
        if (weapon >= ItemCatalog.CLASS_FIRST_BOW && weapon <= ItemCatalog.CLASS_LAST_BOW) {
            return ammo == ItemCatalog.CLASS_BOW_AMMUNITION;
        }
        if (weapon >= ItemCatalog.CLASS_FIRST_SLING && weapon <= ItemCatalog.CLASS_LAST_SLING) {
            return ammo == ItemCatalog.CLASS_SLING_AMMUNITION;
        }
        return false;
    }
}
