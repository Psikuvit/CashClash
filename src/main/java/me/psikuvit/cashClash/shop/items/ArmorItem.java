package me.psikuvit.cashClash.shop.items;

import me.psikuvit.cashClash.config.ShopConfig;
import me.psikuvit.cashClash.shop.ShopCategory;
import org.bukkit.Material;

/**
 * Standard armor items available in the shop.
 */
public enum ArmorItem implements Purchasable {
    IRON_BOOTS(Material.IRON_BOOTS, "iron-boots"),
    IRON_HELMET(Material.IRON_HELMET, "iron-helmet"),
    IRON_LEGGINGS(Material.IRON_LEGGINGS, "iron-leggings"),
    IRON_CHESTPLATE(Material.IRON_CHESTPLATE, "iron-chestplate"),

    DIAMOND_BOOTS(Material.DIAMOND_BOOTS, "diamond-boots"),
    DIAMOND_HELMET(Material.DIAMOND_HELMET, "diamond-helmet"),
    DIAMOND_LEGGINGS(Material.DIAMOND_LEGGINGS, "diamond-leggings"),
    DIAMOND_CHESTPLATE(Material.DIAMOND_CHESTPLATE, "diamond-chestplate");

    private final Material material;
    private final String configKey;

    ArmorItem(Material material, String configKey) {
        this.material = material;
        this.configKey = configKey;
    }

    @Override
    public Material getMaterial() {
        return material;
    }

    @Override
    public ShopCategory getCategory() {
        return ShopCategory.ARMOR;
    }

    @Override
    public long getPrice() {
        return ShopConfig.getInstance().getArmorPrice(configKey);
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
        return name().substring(0, 1).toUpperCase() + name().substring(1).toLowerCase().replace("_", " ");
    }

    @Override
    public String getDescription() {
        return "";
    }
}

