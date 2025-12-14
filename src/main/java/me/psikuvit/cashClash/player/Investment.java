package me.psikuvit.cashClash.player;

/**
 * Represents an investment (Wallet, Purse, or Ender Bag).
 * Investment rules:
 * - 1 death: Player gets the bonus return
 * - 2 deaths: Player breaks even (gets back invested amount)
 * - 3+ deaths: Player loses money (negative return)
 *
 * Note: Revival stars have no effect on investment calculations.
 * Note: Cannot be purchased in Round 5.
 */
public class Investment {

    private final InvestmentType type;
    private final long invested;
    private int deaths;

    public Investment(InvestmentType type, long invested) {
        this.type = type;
        this.invested = invested;
        this.deaths = 0;
    }

    public void recordDeath() {
        deaths++;
    }

    /**
     * Calculate the return based on deaths.
     * @return positive value for profit, zero for break even, negative for loss
     */
    public long calculateReturn() {
        if (deaths <= 1) {
            // 0 or 1 death = bonus
            return type.getBonusReturn();
        } else if (deaths == 2) {
            // 2 deaths = break even (return invested amount)
            return invested;
        } else {
            // 3+ deaths = negative (lose money)
            return -type.getNegativeReturn();
        }
    }

    /**
     * Get the net result (return minus invested).
     * @return profit/loss amount
     */
    public long getNetResult() {
        long returnAmount = calculateReturn();
        if (returnAmount < 0) {
            // Negative return means we lose both invested AND the penalty
            return -invested + returnAmount;
        }
        return returnAmount - invested;
    }

    /**
     * Check if this investment is currently profitable.
     */
    public boolean isProfitable() {
        return deaths <= 1;
    }

    /**
     * Check if this investment breaks even.
     */
    public boolean isBreakEven() {
        return deaths == 2;
    }

    /**
     * Check if this investment results in a loss.
     */
    public boolean isLoss() {
        return deaths >= 3;
    }

    public InvestmentType getType() {
        return type;
    }

    public long getInvested() {
        return invested;
    }

    public int getDeaths() {
        return deaths;
    }

    /**
     * Force set deaths (used for forfeit penalty).
     */
    public void setDeathsToMax() {
        this.deaths = 3;
    }
}

