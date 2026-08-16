package me.psikuvit.cashClash.shop.items;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.shop.ShopCategory;
import org.bukkit.Material;

/**
 * Utility items available in the shop.
 */
public enum UtilityItem implements Purchasable {
    LAVA_BUCKET(Material.LAVA_BUCKET, "lava-bucket", 1, "Quick lava"),
    WATER_BUCKET(Material.WATER_BUCKET, "water-bucket", 1, "Quick water"),
    COBWEB(Material.COBWEB, "cobweb", 1, "Short cobwebs"),
    SPECTRAL_ARROW(Material.SPECTRAL_ARROW, "spectral-arrow", 5, "Timed spectral arrows"),
    LEAVES(Material.OAK_LEAVES, "leaves", 16, "Short leaves"),
    TOTEM(Material.TOTEM_OF_UNDYING, "totem", 1, "Mini totem"),
    ARROWS(Material.ARROW, "arrow", 5, "Timed arrow"),
    // Sold from the Weapons tab (WeaponsCategoryGui), not Utility - category must match that so
    // the shop's "undo last purchase" (category-gated per GUI) can find and refund them there.
    CROSSBOW(Material.CROSSBOW, "crossbow", 1, "Crossbow", ShopCategory.WEAPONS),
    BOW(Material.BOW, "bow", 1, "Bow", ShopCategory.WEAPONS),
    FISHING_ROD(Material.FISHING_ROD, "fishing-rod", 1, "Fishing rod"),
    WIND_CHARGE(Material.WIND_CHARGE, "wind-charge", 4, "Wind charge"),
    SOUL_SAND(Material.SOUL_SAND, "soul-sand", 16, "Soul sand");

    private final Material material;
    private final String configKey;
    private final int initialAmount;
    private final String displayName;
    private final ShopCategory category;

    UtilityItem(Material material, String configKey, int initialAmount, String displayName) {
        this(material, configKey, initialAmount, displayName, ShopCategory.UTILITY);
    }

    UtilityItem(Material material, String configKey, int initialAmount, String displayName, ShopCategory category) {
        this.material = material;
        this.configKey = configKey;
        this.initialAmount = initialAmount;
        this.displayName = displayName;
        this.category = category;
    }

    @Override
    public Material getMaterial() {
        return material;
    }

    @Override
    public ShopCategory getCategory() {
        return category;
    }

    @Override
    public long getPrice() {
        return CashClashPlugin.getInstance().getShopConfig().getUtilityPrice(configKey);
    }

    @Override
    public String getConfigKey() {
        return configKey;
    }

    @Override
    public int getInitialAmount() {
        return initialAmount;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }
}

