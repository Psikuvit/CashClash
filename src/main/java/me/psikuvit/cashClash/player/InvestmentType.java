package me.psikuvit.cashClash.player;

/**
 * Types of investments available
 */
public enum InvestmentType {
    WALLET(10000, 30000, 5000),
    PURSE(30000, 60000, 10000),
    ENDER_BAG(50000, 100000, 20000);

    private final long cost;
    private final long bonusReturn;
    private final long negativeReturn;

    InvestmentType(long cost, long bonusReturn, long negativeReturn) {
        this.cost = cost;
        this.bonusReturn = bonusReturn;
        this.negativeReturn = negativeReturn;
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
}

