package me.psikuvit.cashClash.items;

/**
 * Custom armor sets with special abilities
 */
public enum CustomArmor {
    // Investor's Set (progressive pricing)
    INVESTORS_BOOTS(4250),
    INVESTORS_HELMET(4500),
    INVESTORS_LEGGINGS(4750),
    INVESTORS_CHESTPLATE(5000),

    // Individual pieces
    TAX_EVASION_PANTS(5000),
    GILLIE_SUIT_HAT(7500),
    LIGHTFOOT_SHOES(15000),
    GUARDIANS_VEST(20000),

    // Flamebringer's Set
    FLAMEBRINGER_BOOTS(15000),
    FLAMEBRINGER_LEGGINGS(20000),

    // Deathmauler's Outfit
    DEATHMAULER_OUTFIT(50000),

    // Dragon Set
    DRAGON_SET(75000);

    private final long price;

    CustomArmor(long price) {
        this.price = price;
    }

    public long getPrice() {
        return price;
    }

    public boolean isSet() {
        return this == DEATHMAULER_OUTFIT || this == DRAGON_SET;
    }

    public boolean isInvestorsSet() {
        return name().startsWith("INVESTORS_");
    }

    public boolean isFlamebringerSet() {
        return name().startsWith("FLAMEBRINGER_");
    }
}

