package me.psikuvit.cashClash.items;

/**
 * Types of custom items in Cash Clash
 */
public enum CustomItemType {
    // Custom Crafts
    GRENADE(1500, 0),
    BOUNCE_PAD(3000, 8),
    MEDIC_POUCH(3500, 0),
    TABLET_OF_HACKING(2500, 0),
    BAG_OF_POTATOES(3500, 3),
    STEAM_CLOUD_GRENADE(3000, 0),
    BOOMBOX(2500, 0),
    INVIS_CLOAK(1000, 0),
    CASH_BLASTER(15000, 0),
    REVIVAL_STAR(5000, 2),

    // Special Food
    SPEED_CARROT(200, 0),
    SPINACH_CAN(300, 0),
    SUPER_SPINACH_CAN(15000, 0),
    FLOWER_OF_LIFE(400, 0),
    SUN_SCREEN(3000, 0);

    private final long price;
    private final int maxPurchase;

    CustomItemType(long price, int maxPurchase) {
        this.price = price;
        this.maxPurchase = maxPurchase;
    }

    public long getPrice() {
        return price;
    }

    public int getMaxPurchase() {
        return maxPurchase;
    }

    public boolean hasLimit() {
        return maxPurchase > 0;
    }
}

