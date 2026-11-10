package me.psikuvit.cashClash.game.round;

public class PlayerRoundStats {

    private int kills;
    private double damageDealt;
    private int deaths;
    private long poolShare;
    private long poolTransfers;

    public PlayerRoundStats() {
        this.kills = 0;
        this.damageDealt = 0.0;
    }

    public int getKills() {
        return kills;
    }

    public void incrementKills() {
        kills++;
    }

    public double getDamageDealt() {
        return damageDealt;
    }

    public void addDamage(double damage) {
        damageDealt += damage;
    }

    public int getDeaths() {
        return deaths;
    }

    public void incrementDeaths() {
        deaths++;
    }

    public long getPoolShare() {
        return poolShare;
    }

    public void setPoolShare(long amount) {
        poolShare = amount;
    }

    public long getPoolTransfers() {
        return poolTransfers;
    }

    public void addPoolTransfer(long amount) {
        poolTransfers += amount;
    }
}