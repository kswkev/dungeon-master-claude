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

    /**
     * DM's slot rules: hands and backpack take anything, body slots only what
     * is worn there, pouches small things, the first quiver slot any weapon
     * and the other three only missiles.
     */
    public boolean fits(Slot slot) {
        if (slot == Slot.READY_HAND || slot == Slot.ACTION_HAND || slot.isBackpack()) {
            return true;
        }
        if (slot.isPouch()) {
            return ItemCatalog.fitsPouch(this);
        }
        if (slot == Slot.QUIVER_1) {
            return category == Category.WEAPON;
        }
        if (slot.isQuiver()) {
            return isMissile();
        }
        return wornOn == slot;
    }
}
