package me.psikuvit.cashClash.game.round;

public class PlayerRoundStats {

    private int kills;
    private double damageDealt;
    private int deaths;
    private long lastDamageTime;

    public PlayerRoundStats() {
        this.kills = 0;
        this.damageDealt = 0.0;
        this.lastDamageTime = 0L;
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

    public long getLastDamageTime() {
        return lastDamageTime;
    }

    public void setLastDamageTime(long time) {
        this.lastDamageTime = time;
    }
}