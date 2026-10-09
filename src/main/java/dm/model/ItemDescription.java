package dm.model;

import java.util.ArrayList;
import java.util.List;

/**
 * What DM's eye tells about an item held over it (ReDMCSB INVNTORY.C F342
 * with F336's attribute list and F335's line wrapping): the name, printed
 * beside the icon, and the lines below it. A scroll or a chest shows its own
 * panel instead, so it gets no description.
 *
 * @param name  the name; a potion's starts with its power symbol for a priest above level 1
 * @param lines the lines under the icon, already wrapped at {@link #LINE_LENGTH}
 */
public record ItemDescription(String name, List<String> lines) {

    /** F335 splits a line longer than 18 characters at the last space before the 18th. */
    static final int LINE_LENGTH = 18;

    private static final String[] DIRECTIONS = {"NORTH", "EAST", "SOUTH", "WEST"};
    private static final String[] WATERSKIN = {"(EMPTY)", "(ALMOST EMPTY)", "(ALMOST FULL)", "(FULL)"};
    private static final String[] ATTRIBUTES = {"CONSUMABLE", "POISONED", "BROKEN", "CURSED"};
    private static final int CONSUMABLE = 1;
    private static final int COMPASS = ItemCatalog.COMPASS;
    private static final int WATERSKIN_TYPE = ItemCatalog.WATERSKIN;

    /** Whether DM describes {@code item} (scrolls and chests open their own panels instead). */
    public static boolean describes(Item item) {
        return item != null && item.category() != Item.Category.SCROLL && item.category() != Item.Category.CONTAINER;
    }

    /** F342 for {@code item}, looked at on {@code viewer}'s sheet while the party faces {@code party.facing()}. */
    public static ItemDescription of(Item item, Champion viewer, Party party) {
        List<String> lines = new ArrayList<>();
        int potential = 0;
        int actual = 0;
        switch (item.category()) {
            case WEAPON -> {
                potential = Item.CURSED | Item.POISONED | Item.BROKEN;
                actual = item.flags();
                if (Light.isTorch(item) && item.charges() == 0) {
                    add(lines, "(BURNT OUT)");
                }
            }
            case ARMOUR -> {
                potential = Item.CURSED | Item.BROKEN;
                actual = item.flags();
            }
            case POTION -> {
                potential = CONSUMABLE;
                actual = ItemCatalog.isConsumable(item) ? CONSUMABLE : 0;
            }
            case JUNK -> {
                if (item.type() == WATERSKIN_TYPE) {
                    add(lines, WATERSKIN[Math.min(3, item.charges())]);
                } else if (item.type() == COMPASS) {
                    add(lines, "PARTY FACING " + DIRECTIONS[party.facing().ordinal()]);
                } else {
                    potential = CONSUMABLE;
                    actual = ItemCatalog.isConsumable(item) ? CONSUMABLE : 0;
                }
            }
            default -> { }
        }
        String attributes = attributes(potential, actual);
        if (!attributes.isEmpty()) {
            add(lines, attributes);
        }
        if (item.category() == Item.Category.ARMOUR) {
            // Not in DM: F143's defense, and what is left of it against sharp attacks.
            add(lines, "DEFENSE " + ItemCatalog.armourDefense(item, false) + ".");
            add(lines, "SHARP DEFENSE " + ItemCatalog.armourDefense(item, true) + ".");
        }
        if (item.category() == Item.Category.WEAPON) {
            addWeapon(lines, item, viewer);
        }
        addNourishment(lines, item);
        addEffects(lines, ItemEffects.describe(item));
        int weight = item.weight();
        add(lines, "WEIGHS " + weight / 10 + "." + weight % 10 + " KG.");
        return new ItemDescription(name(item, viewer, party), List.copyOf(lines));
    }

    /** The most rows the panel takes (DM's 6, and 7 a little closer together). */
    static final int MAX_ROWS = 7;

    /** Not in DM: what food gives the food bar, and water the water bar (a waterskin per draught). */
    private static void addNourishment(List<String> lines, Item item) {
        int food = ItemCatalog.foodValue(item);
        if (food > 0) {
            add(lines, "FOOD VALUE " + food);
        }
        if (item.category() == Item.Category.JUNK && item.type() == WATERSKIN_TYPE) {
            add(lines, "WATER VALUE " + Upkeep.WATERSKIN_DRAUGHT);
        } else if (item.category() == Item.Category.POTION && item.type() == ItemCatalog.WATER_FLASK) {
            add(lines, "WATER VALUE " + Upkeep.WATER_FLASK);
        }
    }

    /**
     * Not in DM: what the item does worn or held ({@link ItemEffects#describe}),
     * each place once ("WORN ON NECK:") with its effects under it. If that
     * would take the panel past {@link #MAX_ROWS} (with the weight still to
     * come), each effect goes on one row with a short place instead
     * ("NECK: MANA +3").
     */
    private static void addEffects(List<String> lines, List<String[]> effects) {
        List<String> full = new ArrayList<>();
        String last = null;
        for (String[] e : effects) {
            if (!e[0].equals(last)) {
                full.add(e[0]);
                last = e[0];
            }
            full.add(e[1]);
        }
        if (lines.size() + full.size() + 1 <= MAX_ROWS) {
            lines.addAll(full);
            return;
        }
        for (String[] e : effects) {
            if (lines.size() + 1 >= MAX_ROWS) {
                break; // only a poisoned, broken and cursed weapon with three actions: "(CURSED)" says enough
            }
            String place = e[0].replace("WORN ON ", "").replace("IN ACTION HAND", "HAND")
                    .replace("WORN OR IN HAND", "WORN");
            add(lines, place + e[1]);
        }
    }

    /**
     * The name F342 prints: a champion's bones carry the champion's name, and
     * a potion other than water starts with its power symbol (DM's font has
     * the symbols right after '_', so power / 40 picks one) when the viewer's
     * priest level is above 1. An empty flask gets one too: DM's BUG0_49.
     */
    static String name(Item item, Champion viewer, Party party) {
        if (item.category() == Item.Category.JUNK && item.type() == Party.BONES
                && item.charges() >= 0 && item.charges() < party.members().size()) {
            return party.members().get(item.charges()).name() + " " + item.name();
        }
        if (item.category() == Item.Category.POTION && item.type() != ItemCatalog.WATER_FLASK
                && viewer.skillLevel(Champion.PRIEST) > 1) {
            return (char) ('_' + item.charges() / 40) + " " + item.name();
        }
        return item.name();
    }

    /** F336: "(A)", "(A AND B)" or "(A, B AND C)" for the bits in both masks, or "" for none. */
    static String attributes(int potential, int actual) {
        int count = Integer.bitCount(potential & actual);
        if (count == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < ATTRIBUTES.length; i++) {
            if ((potential & actual & (1 << i)) != 0) {
                sb.append(ATTRIBUTES[i]);
                if (count-- > 2) {
                    sb.append(", ");
                } else if (count == 1) {
                    sb.append(" AND ");
                }
            }
        }
        return sb.append(')').toString();
    }

    /**
     * Not in DM: a weapon's G238 strength (what F312 adds to its wielder's
     * blows and throws, so only shown when it has a melee or throw action)
     * and its G237 actions, one per row, each marked with the skill level
     * {@code viewer} still needs as "n+" (F383's minimum) and, when it uses a charge,
     * how many the weapon has left.
     */
    private static void addWeapon(List<String> lines, Item item, Champion viewer) {
        int set = ItemCatalog.actionSet(item);
        List<Integer> actions = new ArrayList<>();
        for (int i = 0; i < 3 && set != 0; i++) {
            actions.add(Actions.setAction(set, i));
        }
        int strength = ItemCatalog.weaponStrength(item);
        if (strength > 0 && actions.stream().anyMatch(a -> a == Actions.THROW || Combat.isMelee(a))) {
            add(lines, "DAMAGE RATING " + strength + ".");
        }
        for (int i = 0; i < actions.size(); i++) {
            int action = actions.get(i);
            if (action == Actions.NONE) {
                continue;
            }
            String entry = Actions.name(action);
            if (i > 0) {
                int property = Actions.setProperty(set, i);
                int level = property & 0x7F;
                if (viewer != null && viewer.skillLevel(Actions.skill(action)) < level) {
                    entry += " " + level + "+";
                }
                if ((property & 0x80) != 0) {
                    entry += " (" + Combat.charges(item) + ")"; // the item's charges, which every such action uses
                }
                if (entry.length() > LINE_LENGTH) {
                    entry = entry.replace(" (", "("); // SPELLSHIELD 2+(15) rather than a row of its own
                }
            }
            add(lines, entry);
        }
    }

    /** F335: each line goes on as many rows as it needs, split at a space. */
    private static void add(List<String> lines, String text) {
        String rest = text;
        while (rest.length() > LINE_LENGTH) {
            int split = rest.lastIndexOf(' ', LINE_LENGTH - 1);
            if (split <= 0) {
                break;
            }
            lines.add(rest.substring(0, split));
            rest = rest.substring(split + 1);
        }
        lines.add(rest);
    }
}
