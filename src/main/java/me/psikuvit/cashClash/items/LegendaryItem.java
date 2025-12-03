package me.psikuvit.cashClash.items;

/**
 * Legendary weapons and items (one per team per game)
 */
public enum LegendaryItem {
    COIN_CLEAVER(75000),
    CARLS_BATTLEAXE(100000),
    WIND_BOW(100000),
    BOBBY_THE_DOG(100000),
    ELECTRIC_EEL_SWORD(125000),
    GOBLIN_SPEAR(125000),
    SANDSTORMER(125000),
    WARDEN_GLOVES(150000),
    BLAZEBITE_CROSSBOWS(150000);

    private final long price;

    LegendaryItem(long price) {
        this.price = price;
    }

    public long getPrice() {
        return price;
    }

    public String getDisplayName() {
        String[] words = name().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            result.append(word.charAt(0)).append(word.substring(1).toLowerCase()).append(" ");
        }
        return result.toString().trim();
    }
}

