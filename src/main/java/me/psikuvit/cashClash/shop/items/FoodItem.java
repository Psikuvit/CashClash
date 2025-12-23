package me.psikuvit.cashClash.shop.items;

import me.psikuvit.cashClash.config.ShopConfig;
import me.psikuvit.cashClash.shop.ShopCategory;
import org.bukkit.Material;

/**
 * Food items available in the shop.
 */
public enum FoodItem implements Purchasable {
    BREAD(Material.BREAD, "bread", 4),
    COOKED_MUTTON(Material.COOKED_MUTTON, "cooked-mutton", 4),
    STEAK(Material.COOKED_BEEF, "steak", 4),
    PORKCHOP(Material.COOKED_PORKCHOP, "porkchop", 4),
    GOLDEN_CARROT(Material.GOLDEN_CARROT, "golden-carrot", 4),
    GOLDEN_APPLE(Material.GOLDEN_APPLE, "golden-apple", 1),
    ENCHANTED_GOLDEN_APPLE(Material.ENCHANTED_GOLDEN_APPLE, "enchanted-golden-apple", 1),

    SPEED_CARROT(Material.CARROT, "speed-carrot", 1),
    GOLDEN_CHICKEN(Material.COOKED_CHICKEN, "golden-chicken", 1),
    COOKIE_OF_LIFE(Material.COOKIE, "cookie-of-life", 1),
    SUNSCREEN(Material.HONEY_BOTTLE, "sunscreen", 1),
    CAN_OF_SPINACH(Material.SPIDER_EYE, "can-of-spinach", 1);

    private final Material material;
    private final String configKey;
    private final int initialAmount;

    FoodItem(Material material, String configKey, int initialAmount) {
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
        return ShopCategory.FOOD;
    }

    @Override
    public long getPrice() {
        return ShopConfig.getInstance().getFoodPrice(configKey);
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
            case SPEED_CARROT -> "<gray>Grants 20s of Speed I when consumed.</gray>";
            case GOLDEN_CHICKEN, CAN_OF_SPINACH -> "<gray>Grants 15s of Strength I when consumed.</gray>";
            case COOKIE_OF_LIFE -> "<gray>Grants 14s of Regeneration I when consumed.</gray>";
            case SUNSCREEN -> "<gray>Grants 30s of Fire Resistance when consumed.</gray>";
            default -> "";
        };
    }
}

