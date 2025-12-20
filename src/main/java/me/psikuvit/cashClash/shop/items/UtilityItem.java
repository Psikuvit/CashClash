package me.psikuvit.cashClash.shop.items;

import me.psikuvit.cashClash.shop.ShopCategory;
import org.bukkit.Material;

/**
 * Utility items available in the shop.
 */
public enum UtilityItem implements Purchasable {
    LAVA_BUCKET(Material.LAVA_BUCKET, 1500, 1),
    WATER_BUCKET(Material.WATER_BUCKET, 500, 1),
    COBWEB(Material.COBWEB, 625, 4),
    CROSSBOW(Material.CROSSBOW, 4500, 1),
    BOW(Material.BOW, 4000, 1),
    FISHING_ROD(Material.FISHING_ROD, 1500, 1),
    ARROWS(Material.ARROW, 50, 5),
    ENDER_PEARL(Material.ENDER_PEARL, 2500, 1),
    WIND_CHARGE(Material.WIND_CHARGE, 600, 4),
    LEAVES(Material.OAK_LEAVES, 10, 16),
    SOUL_SPEED_BLOCK(Material.SOUL_SAND, 80, 16);

    private final Material material;
    private final long price;
    private final int initialAmount;

    UtilityItem(Material material, long price, int initialAmount) {
        this.material = material;
        this.price = price;
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
        return price;
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

