package dm.model;

/**
 * An object a champion can carry.
 *
 * @param category    what kind of object it is (decides which slots fit it)
 * @param type        the type number within its category, as stored in DUNGEON.DAT
 * @param name        display name
 * @param nameVariant which of several icons sharing {@code name} this is (0 = first), e.g. ROBE body vs legs
 * @param wornOn      the body slot it is worn in, or null if it isn't wearable
 */
public record Item(Category category, int type, String name, int nameVariant, Slot wornOn) {

    public enum Category { WEAPON, ARMOUR, SCROLL, POTION, CONTAINER, JUNK }

    /** Thrown or fired weapons that DM packs into the quiver. */
    public boolean isMissile() {
        return category == Category.WEAPON && ItemCatalog.isMissileWeapon(type);
    }

    public boolean isShield() {
        return category == Category.ARMOUR && ItemCatalog.isShield(type);
    }
}
