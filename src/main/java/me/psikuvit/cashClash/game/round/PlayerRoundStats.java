package me.psikuvit.cashClash.game.round;

public class PlayerRoundStats {

    private int kills;
    private double damageDealt;
    private int deaths;

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
}