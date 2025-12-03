package me.psikuvit.cashClash.player;

/**
 * Represents an investment (Wallet, Purse, or Ender Bag)
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

    public long calculateReturn() {
        if (deaths == 0) {
            return type.getBonusReturn();
        } else if (deaths == 1) {
            return invested; // Break even
        } else if (deaths == 2) {
            return invested; // Still break even
        } else {
            return -type.getNegativeReturn(); // Loss
        }
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
}

