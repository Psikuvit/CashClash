package me.psikuvit.cashClash.shop.items;

import me.psikuvit.cashClash.shop.ShopCategory;
import org.bukkit.Material;

/**
 * Weapon items available in the shop.
 */
public enum WeaponItem implements Purchasable {
    IRON_SWORD(Material.IRON_SWORD, 1000),
    IRON_AXE(Material.IRON_AXE, 2000),
    DIAMOND_SWORD(Material.DIAMOND_SWORD, 3000),
    DIAMOND_AXE(Material.DIAMOND_AXE, 4000),
    NETHERITE_SWORD(Material.NETHERITE_SWORD, 10000),
    NETHERITE_AXE(Material.NETHERITE_AXE, 12000);

    private final Material material;
    private final long price;

    WeaponItem(Material material, long price) {
        this.material = material;
        this.price = price;
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
        return price;
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

