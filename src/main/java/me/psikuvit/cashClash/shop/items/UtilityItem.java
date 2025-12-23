package me.psikuvit.cashClash.shop.items;

import me.psikuvit.cashClash.config.ShopConfig;
import me.psikuvit.cashClash.shop.ShopCategory;
import org.bukkit.Material;

/**
 * Utility items available in the shop.
 */
public enum UtilityItem implements Purchasable {
    LAVA_BUCKET(Material.LAVA_BUCKET, "lava-bucket", 1),
    WATER_BUCKET(Material.WATER_BUCKET, "water-bucket", 1),
    COBWEB(Material.COBWEB, "cobweb", 16),
    CROSSBOW(Material.CROSSBOW, "crossbow", 1),
    BOW(Material.BOW, "bow", 1),
    FISHING_ROD(Material.FISHING_ROD, "fishing-rod", 1),
    WIND_CHARGE(Material.WIND_CHARGE, "wind-charge", 4),
    LEAVES(Material.OAK_LEAVES, "leaves", 16),
    SOUL_SAND(Material.SOUL_SAND, "soul-sand", 16),
    ARROWS(Material.ARROW, "arrow", 5);

    private final Material material;
    private final String configKey;
    private final int initialAmount;

    UtilityItem(Material material, String configKey, int initialAmount) {
        this.material = material;
        this.configKey = configKey;
        this.initialAmount = initialAmount;
    }

    @Override
    public Material getMaterial() {
        return material;
    }

    @Override
    public ShopCategory getCategory() {
        return ShopCategory.UTILITY;
    }

    @Override
    public long getPrice() {
        return ShopConfig.getInstance().getUtilityPrice(configKey);
    }

    @Override
    public int getInitialAmount() {
        return initialAmount;
    }

    @Override
    public String getDisplayName() {
        return name().substring(0, 1).toUpperCase() + name().substring(1).toLowerCase().replace("_", " ");
    }

    @Override
    public String getDescription() {
        return "";
    }
}

