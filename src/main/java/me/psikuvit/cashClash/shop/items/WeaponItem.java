package me.psikuvit.cashClash.shop.items;

import me.psikuvit.cashClash.config.ShopConfig;
import me.psikuvit.cashClash.shop.ShopCategory;
import org.bukkit.Material;

/**
 * Weapon items available in the shop.
 */
public enum WeaponItem implements Purchasable {
    IRON_SWORD(Material.IRON_SWORD, "iron-sword"),
    IRON_AXE(Material.IRON_AXE, "iron-axe"),
    DIAMOND_SWORD(Material.DIAMOND_SWORD, "diamond-sword"),
    DIAMOND_AXE(Material.DIAMOND_AXE, "diamond-axe"),
    NETHERITE_SWORD(Material.NETHERITE_SWORD, "netherite-sword"),
    NETHERITE_AXE(Material.NETHERITE_AXE, "netherite-axe");

    private final Material material;
    private final String configKey;

    WeaponItem(Material material, String configKey) {
        this.material = material;
        this.configKey = configKey;
    }

    @Override
    public Material getMaterial() {
        return material;
    }

    @Override
    public ShopCategory getCategory() {
        return ShopCategory.WEAPONS;
    }

    @Override
    public long getPrice() {
        return ShopConfig.getInstance().getWeaponPrice(configKey);
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

