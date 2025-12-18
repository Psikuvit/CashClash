package me.psikuvit.cashClash.shop.items;

import me.psikuvit.cashClash.shop.ShopCategory;
import org.bukkit.Material;

/**
 * Standard armor items available in the shop.
 */
public enum ArmorItem implements Purchasable {
    IRON_BOOTS(Material.IRON_BOOTS, 2250),
    IRON_HELMET(Material.IRON_HELMET, 2500),
    IRON_LEGGINGS(Material.IRON_LEGGINGS, 2750),
    IRON_CHESTPLATE(Material.IRON_CHESTPLATE, 3000),

    DIAMOND_BOOTS(Material.DIAMOND_BOOTS, 2250),
    DIAMOND_HELMET(Material.DIAMOND_HELMET, 2500),
    DIAMOND_LEGGINGS(Material.DIAMOND_LEGGINGS, 2750),
    DIAMOND_CHESTPLATE(Material.DIAMOND_CHESTPLATE, 3000),

    NETHERITE_BOOTS(Material.NETHERITE_BOOTS, 15000),
    NETHERITE_HELMET(Material.NETHERITE_HELMET, 17500),
    NETHERITE_LEGGINGS(Material.NETHERITE_LEGGINGS, 20000),
    NETHERITE_CHESTPLATE(Material.NETHERITE_CHESTPLATE, 25000);

    private final Material material;
    private final long price;

    ArmorItem(Material material, long price) {
        this.material = material;
        this.price = price;
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

