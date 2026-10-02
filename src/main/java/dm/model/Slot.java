package dm.model;

/** A champion's inventory slots, laid out as on DM's inventory screen. */
public enum Slot {
    READY_HAND, ACTION_HAND,
    HEAD, NECK, TORSO, LEGS, FEET,
    POUCH_1, POUCH_2,
    QUIVER_1, QUIVER_2, QUIVER_3, QUIVER_4,
    BACKPACK_1, BACKPACK_2, BACKPACK_3, BACKPACK_4, BACKPACK_5, BACKPACK_6,
    BACKPACK_7, BACKPACK_8, BACKPACK_9, BACKPACK_10, BACKPACK_11, BACKPACK_12,
    BACKPACK_13, BACKPACK_14, BACKPACK_15, BACKPACK_16, BACKPACK_17;

    public boolean isBackpack() {
        return ordinal() >= BACKPACK_1.ordinal();
    }

    public boolean isQuiver() {
        return ordinal() >= QUIVER_1.ordinal() && ordinal() <= QUIVER_4.ordinal();
    }

    public boolean isPouch() {
        return this == POUCH_1 || this == POUCH_2;
    }
}
