package me.psikuvit.cashClash.util.enums;

import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.items.Purchasable;
import org.bukkit.Material;

/**
 * Types of investments available
 */
public enum InvestmentType implements Purchasable {
    WALLET(10000, 30000, 5000, Material.PAPER),
    PURSE(30000, 60000, 10000, Material.BUNDLE),
    ENDER_BAG(50000, 100000, 20000, Material.POPPED_CHORUS_FRUIT);

    private final long cost;
    private final long bonusReturn;
    private final long negativeReturn;
    private final Material material;

    InvestmentType(long cost, long bonusReturn, long negativeReturn, Material material) {
        this.cost = cost;
        this.bonusReturn = bonusReturn;
        this.negativeReturn = negativeReturn;
        this.material = material;
    }

    public long getCost() {
        return cost;
    }

    public long getBonusReturn() {
        return bonusReturn;
    }

    public long getNegativeReturn() {
        return negativeReturn;
    }

    @Override
    public Material getMaterial() {
        return material;
    }

    @Override
    public ShopCategory getCategory() {
        return ShopCategory.INVESTMENTS;
    }

    @Override
    public long getPrice() {
        return cost;
    }

    @Override
    public int getInitialAmount() {
        return 1;
    }

    @Override
    public String getDisplayName() {
        return name().replace("_", " ");
    }

    @Override
    public String getDescription() {
        return "Bonus: $" + String.format("%,d", bonusReturn) + " | Loss: $" + String.format("%,d", negativeReturn);
    }
}

