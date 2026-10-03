package dm.model;

import java.io.Serializable;

/**
 * An object a champion can carry. Items are values: one that changes (a
 * waterskin drunk from, a flask emptied) is replaced by a new one.
 *
 * @param category    what kind of object it is (decides which slots fit it)
 * @param type        the type number within its category, as stored in DUNGEON.DAT
 * @param name        display name
 * @param nameVariant which of several icons sharing {@code name} this is (0 = first), e.g. ROBE body vs legs
 * @param wornOn      the body slot it is worn in, or null if it isn't wearable
 * @param charges     DM's charge count: a weapon's charges (a torch's light power, 0-15),
 *                    a waterskin's draughts (0-3), or a potion's power (0-255)
 */
public record Item(Category category, int type, String name, int nameVariant, Slot wornOn, int charges)
        implements Serializable {

    public enum Category { WEAPON, ARMOUR, SCROLL, POTION, CONTAINER, JUNK }

    public Item(Category category, int type, String name, int nameVariant, Slot wornOn) {
        this(category, type, name, nameVariant, wornOn, 0);
    }

    /** Thrown or fired weapons that DM packs into the quiver. */
    public boolean isMissile() {
        return category == Category.WEAPON && ItemCatalog.isMissileWeapon(type);
    }

    public boolean isShield() {
        return category == Category.ARMOUR && ItemCatalog.isShield(type);
    }

    /** The same item with {@code charges} charges (a waterskin's name changes to WATER when it holds some). */
    public Item withCharges(int charges) {
        return ItemCatalog.item(category, type, charges);
    }

    /** DM's weight in tenths of a kilogram (ReDMCSB F140). */
    public int weight() {
        return ItemCatalog.weight(this);
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
