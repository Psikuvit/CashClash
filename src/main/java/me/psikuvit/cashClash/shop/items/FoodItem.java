package me.psikuvit.cashClash.shop.items;

import me.psikuvit.cashClash.shop.ShopCategory;
import org.bukkit.Material;

/**
 * Food items available in the shop.
 */
public enum FoodItem implements Purchasable {
    BREAD(Material.BREAD, 10, 4),
    COOKED_MUTTON(Material.COOKED_MUTTON, 50, 4),
    STEAK(Material.COOKED_BEEF, 75, 4),
    PORKCHOP(Material.COOKED_PORKCHOP, 75, 4),
    GOLDEN_CARROT(Material.GOLDEN_CARROT, 100, 4),
    GOLDEN_APPLE(Material.GOLDEN_APPLE, 3000, 1),
    ENCHANTED_GOLDEN_APPLE(Material.ENCHANTED_GOLDEN_APPLE, 30000, 1),

    // Special consumables
    SPEED_CARROT(Material.CARROT, 1200, 1),          // 20s speed
    GOLDEN_CHICKEN(Material.COOKED_CHICKEN, 1400, 1), // 15s strength (nerfed)
    COOKIE_OF_LIFE(Material.COOKIE, 1000, 1),         // 14s regen
    SUNSCREEN(Material.HONEY_BOTTLE, 2500, 1),        // 30s fire res
    CAN_OF_SPINACH(Material.SPIDER_EYE, 4000, 1);     // 15s strength (spinach)

    private final Material material;
    private final long price;
    private final int initialAmount;

    FoodItem(Material material, long price, int initialAmount) {
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
        return ShopCategory.FOOD;
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
        return switch (this) {
            case SPEED_CARROT -> "<gray>Grants 20s of Speed II when consumed.</gray>";
            case GOLDEN_CHICKEN -> "<gray>Grants 15s of Strength I when consumed.</gray>";
            case COOKIE_OF_LIFE -> "<gray>Grants 14s of Regeneration I when consumed.</gray>";
            case SUNSCREEN -> "<gray>Grants 30s of Fire Resistance when consumed.</gray>";
            case CAN_OF_SPINACH -> "<gray>Grants 15s of Strength I when consumed.</gray>";
            default -> "";
        };
    }
}

