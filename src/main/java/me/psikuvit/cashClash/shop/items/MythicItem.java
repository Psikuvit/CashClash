package me.psikuvit.cashClash.shop.items;

import me.psikuvit.cashClash.config.ShopConfig;
import me.psikuvit.cashClash.shop.ShopCategory;
import org.bukkit.Material;

/**
 * Mythic (Legendary) items - one per team per game.
 * These are powerful unique weapons with special abilities.
 */
public enum MythicItem implements Purchasable {
    COIN_CLEAVER(Material.DIAMOND_AXE, "coin-cleaver", "Coin Cleaver"),
    CARLS_BATTLEAXE(Material.NETHERITE_AXE, "carls-battleaxe", "Carl's Battleaxe"),
    WIND_BOW(Material.BOW, "wind-bow", "Wind Bow"),
    ELECTRIC_EEL_SWORD(Material.DIAMOND_SWORD, "electric-eel-sword", "Electric Eel Sword"),
    GOBLIN_SPEAR(Material.TRIDENT, "goblin-spear", "Goblin Spear"),
    BLOODWRENCH_CROSSBOW(Material.CROSSBOW, "bloodwrench-crossbow", "BloodWrench Crossbow"),
    WARDEN_GLOVES(Material.NETHERITE_SWORD, "warden-gloves", "Warden Gloves"),
    BLAZEBITE_CROSSBOWS(Material.CROSSBOW, "blazebite-crossbows", "BlazeBite Crossbows");

    private final Material material;
    private final String configKey;
    private final String displayName;

    MythicItem(Material material, String configKey, String displayName) {
        this.material = material;
        this.configKey = configKey;
        this.displayName = displayName;
    }

    @Override
    public Material getMaterial() {
        return material;
    }

    @Override
    public ShopCategory getCategory() {
        return ShopCategory.LEGENDARIES;
    }

    @Override
    public long getPrice() {
        return ShopConfig.getInstance().getMythicItemPrice(configKey);
    }

    @Override
    public String getConfigKey() {
        return configKey;
    }

    @Override
    public int getInitialAmount() {
        return 1;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Check if this mythic is a melee weapon.
     */
    public boolean isMelee() {
        return this == COIN_CLEAVER || this == CARLS_BATTLEAXE ||
               this == ELECTRIC_EEL_SWORD || this == GOBLIN_SPEAR || this == WARDEN_GLOVES;
    }

    /**
     * Check if this mythic is a ranged weapon.
     */
    public boolean isRanged() {
        return this == WIND_BOW || this == BLOODWRENCH_CROSSBOW || this == BLAZEBITE_CROSSBOWS;
    }
}
