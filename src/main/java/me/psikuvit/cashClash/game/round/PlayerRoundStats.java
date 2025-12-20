package me.psikuvit.cashClash.game.round;

public class PlayerRoundStats {

    private int kills;
    private double damageDealt;
    private int lives;
    private long lastDamageTime;
    private boolean survivor;

    public PlayerRoundStats(int lives) {
        this.kills = 0;
        this.damageDealt = 0.0;
        this.lives = lives;
        this.lastDamageTime = 0L;
        this.survivor = false;
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

    public int getLives() {
        return lives;
    }

    public void decrementLives() {
        if (lives > 0) {
            lives--;
        }
    }

    public long getLastDamageTime() {
        return lastDamageTime;
    }

    public void setLastDamageTime(long time) {
        this.lastDamageTime = time;
    }

    public boolean isSurvivor() {
        return survivor;
    }

    public void markSurvivor() {
        this.survivor = true;
    }

    public boolean isAlive() {
        return lives > 0;
    }
}