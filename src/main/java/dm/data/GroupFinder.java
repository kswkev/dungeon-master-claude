package dm.data;

import dm.model.CreatureType;
import dm.model.Direction;
import dm.model.Group;
import dm.model.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * Decodes the creature groups (thing type 4) on each square; see
 * {@link Group} for the record layout. A group's possessions are a thing
 * list of their own, linked from its second word.
 */
final class GroupFinder {

    private GroupFinder() {
    }

    static List<Group> find(List<List<Thing>>[] squareThings, Thing.Store store) {
        List<Group> groups = new ArrayList<>();
        for (int x = 0; x < squareThings.length; x++) {
            for (int y = 0; y < squareThings[x].size(); y++) {
                for (Thing t : squareThings[x].get(y)) {
                    if (t.type() == Thing.GROUP) {
                        Group g = decode(x, y, t.words(), store == null ? List.of() : store.chain(t.words()[1]));
                        if (g != null) {
                            groups.add(g);
                        }
                    }
                }
            }
        }
        return groups;
    }

    /** The group a record describes, or null for an unknown creature type. */
    static Group decode(int x, int y, int[] words, List<Thing> possessionThings) {
        CreatureType type = CreatureType.of(words[2] & 0xFF);
        if (type == null) {
            return null;
        }
        int cells = (words[2] >>> 8) & 0xFF;
        int attributes = words[7];
        int count = ((attributes >>> 5) & 3) + 1; // bits 0-3 are the behaviour
        Direction facing = Direction.fromIndex((attributes >>> 8) & 3);
        int[] health = {words[3], words[4], words[5], words[6]};
        List<Item> possessions = new ArrayList<>();
        for (Thing p : possessionThings) {
            Item item = ChampionFinder.toItem(p);
            if (item != null) {
                possessions.add(item);
            }
        }
        return new Group(type, x, y, cells, health, count, facing, attributes & 15, possessions);
    }
}
