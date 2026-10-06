package dm.model;

import java.util.ArrayList;
import java.util.List;

/**
 * DM's F299 (ReDMCSB CHAMPION.C): what an item does to its bearer's
 * statistics while it is in a slot, and (not in DM) the lines describing
 * every effect an item has when worn or held, for {@link ItemDescription}.
 *
 * <p>DM adds the bonus to the statistic's current, maximum and minimum
 * values as the item goes in and takes it off as it comes out; here
 * {@link Champion} adds up the bonuses of what it carries whenever a
 * statistic is read, which comes to the same and keeps older saves right.
 */
public final class ItemEffects {

    private ItemEffects() {
    }

    /** A bonus to a statistic, or to maximum mana when {@code stat} is null. */
    public record Bonus(Champion.Stat stat, int amount) {
    }

    // Weapons (DUNGEON.DAT types).
    private static final int STAFF_OF_CLAWS = 4;
    private static final int FIRESTAFF = 7;
    private static final int DELTA = 14;
    private static final int VORPAL_BLADE = 16;
    private static final int INQUISITOR = 17;
    private static final int MACE_OF_ORDER = 21;
    private static final int FIRESTAFF_COMPLETE = 45;
    /** Staff to Sceptre of Lyf (34-42), DM's icons 58-66, and the mana each gives in the action hand. */
    private static final int STAFF = 34;
    private static final int[] STAFF_MANA = {2, 1, 6, 4, 10, 8, 16, 7, 5};
    private static final int SCEPTRE_OF_LYF = 42;
    // Armour.
    private static final int CLOAK_OF_NIGHT = 1;
    private static final int ELVEN_BOOTS = 15;
    private static final int CROWN_OF_NERRA = 24;
    private static final int DEXHELM = 53;
    private static final int FLAMEBAIN = 54;
    private static final int POWERTOWERS = 55;
    private static final int BOOTS_OF_SPEED = 56;
    // Junk.
    private static final int JEWEL_SYMAL = 2;
    private static final int ILLUMULET = 3;
    private static final int GEM_OF_AGES = 37;
    private static final int EKKHARD_CROSS = 38;
    private static final int MOONSTONE = 39;
    private static final int PENDANT_FERAL = 41;
    private static final int RABBITS_FOOT = 46;

    /**
     * F299: the bonus {@code item} gives in {@code slot}, or null. A cursed
     * weapon or armour anywhere but the backpack costs 3 luck instead of
     * anything else it does; a Rabbit's Foot anywhere adds 10 luck (CSB's
     * fix: not in a chest, which here isn't a slot); the rest count only in
     * their place: mana for staffs and some blades in the action hand, the
     * Mace of Order's strength there, and the worn pieces' statistics.
     */
    public static Bonus of(Slot slot, Item item) {
        Item.Category category = item.category();
        int type = item.type();
        boolean weapon = category == Item.Category.WEAPON;
        boolean armour = category == Item.Category.ARMOUR;
        boolean junk = category == Item.Category.JUNK;
        if ((weapon || armour) && !slot.isBackpack() && (item.flags() & Item.CURSED) != 0) {
            return new Bonus(Champion.Stat.LUCK, -3);
        }
        if (junk && type == RABBITS_FOOT) {
            return new Bonus(Champion.Stat.LUCK, 10);
        }
        return switch (slot) {
            case ACTION_HAND -> weapon ? actionHand(type) : null;
            case LEGS -> armour && type == POWERTOWERS ? new Bonus(Champion.Stat.STRENGTH, 10) : null;
            case HEAD -> !armour ? null
                    : type == CROWN_OF_NERRA ? new Bonus(Champion.Stat.WISDOM, 10)
                    : type == DEXHELM ? new Bonus(Champion.Stat.DEXTERITY, 10) : null;
            case TORSO -> !armour ? null
                    : type == FLAMEBAIN ? new Bonus(Champion.Stat.ANTI_FIRE, 12)
                    : type == CLOAK_OF_NIGHT ? new Bonus(Champion.Stat.DEXTERITY, 8) : null;
            case NECK -> junk && type == JEWEL_SYMAL ? new Bonus(Champion.Stat.ANTI_MAGIC, 15)
                    : armour && type == CLOAK_OF_NIGHT ? new Bonus(Champion.Stat.DEXTERITY, 8)
                    : junk && type == MOONSTONE ? new Bonus(null, 3) : null;
            default -> null;
        };
    }

    private static Bonus actionHand(int type) {
        if (type == MACE_OF_ORDER) {
            return new Bonus(Champion.Stat.STRENGTH, 5);
        }
        int mana = type == STAFF_OF_CLAWS ? 4
                : type >= STAFF && type <= SCEPTRE_OF_LYF ? STAFF_MANA[type - STAFF]
                : type == DELTA ? 1 : type == INQUISITOR ? 2 : type == VORPAL_BLADE ? 4 : 0;
        return mana == 0 ? null : new Bonus(null, mana);
    }

    /**
     * Not in DM: what {@code item} does when worn or held, as pairs of lines:
     * where it must be ("WORN ON HEAD:") and what it does (" WISDOM +10").
     * Its F299 bonus first, then the skill bonuses of F303 ({@link
     * Champion#skillLevel}), the Ekkhard Cross's healing ({@link Upkeep}),
     * the Illumulet's light and the boots'. A cursed
     * weapon or armour only costs luck.
     */
    public static List<String[]> describe(Item item) {
        List<String[]> effects = new ArrayList<>();
        Item.Category category = item.category();
        int type = item.type();
        if ((category == Item.Category.WEAPON || category == Item.Category.ARMOUR)
                && (item.flags() & Item.CURSED) != 0) {
            effects.add(new String[] {"WORN OR IN HAND:", " LUCK -3"});
            return effects;
        }
        Slot place = category == Item.Category.WEAPON ? Slot.ACTION_HAND : item.wornOn();
        if (category == Item.Category.JUNK && type == RABBITS_FOOT) {
            effects.add(new String[] {"CARRIED:", " LUCK +10"});
        } else if (place != null) {
            Bonus bonus = of(place, item);
            if (bonus != null) {
                effects.add(new String[] {where(place), " " + statName(bonus.stat()) + " +" + bonus.amount()});
            }
        }
        if (category == Item.Category.WEAPON) {
            if (type == FIRESTAFF || type == FIRESTAFF_COMPLETE) {
                effects.add(new String[] {where(Slot.ACTION_HAND), " ALL SKILLS +" + (type == FIRESTAFF ? 1 : 2)});
            } else if (type == SCEPTRE_OF_LYF) {
                effects.add(new String[] {where(Slot.ACTION_HAND), " HEAL +1"});
            }
        } else if (category == Item.Category.JUNK) {
            String skill = switch (type) {
                case PENDANT_FERAL -> " WIZARD +1";
                case EKKHARD_CROSS -> " DEFEND +1";
                case GEM_OF_AGES -> " HEAL +1";
                case MOONSTONE -> " INFLUENCE +1";
                case ILLUMULET -> " GIVES LIGHT";
                default -> null;
            };
            if (skill != null) {
                effects.add(new String[] {where(Slot.NECK), skill});
            }
            if (type == EKKHARD_CROSS) { // F331 heals its wearer half as fast again
                effects.add(new String[] {where(Slot.NECK), " FASTER HEALING"});
            }
        } else if (category == Item.Category.ARMOUR) {
            if (type == BOOTS_OF_SPEED) {
                effects.add(new String[] {where(Slot.FEET), " FASTER WALKING"});
            } else if (type == ELVEN_BOOTS) {
                effects.add(new String[] {where(Slot.FEET), " MAX LOAD +6%"});
            }
        }
        return effects;
    }

    private static String where(Slot slot) {
        return switch (slot) {
            case ACTION_HAND -> "IN ACTION HAND:";
            case HEAD -> "WORN ON HEAD:";
            case NECK -> "WORN ON NECK:";
            case TORSO -> "WORN ON TORSO:";
            case LEGS -> "WORN ON LEGS:";
            case FEET -> "WORN ON FEET:";
            default -> "CARRIED:";
        };
    }

    private static String statName(Champion.Stat stat) {
        return stat == null ? "MANA" : stat.label();
    }
}
