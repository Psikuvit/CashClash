package me.psikuvit.cashClash.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerData {
    private UUID uuid;
    private int wins;
    private int deaths;
    private int kills;
    private long totalCoinsInvested;
    private Map<String, Integer> ownedEnchants = new HashMap<>();

    // No-arg constructor for Gson
    public PlayerData() {}

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.wins = 0;
        this.deaths = 0;
        this.kills = 0;
        this.totalCoinsInvested = 0L;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public int getWins() {
        return wins;
    }

    public void setWins(int wins) {
        this.wins = wins;
    }

    public void incWins() {
        this.wins++;
    }

    public int getDeaths() {
        return deaths;
    }

    public void setDeaths(int deaths) {
        this.deaths = deaths;
    }

    public void incDeaths() {
        this.deaths++;
    }

    public int getKills() {
        return kills;
    }

    public void setKills(int kills) {
        this.kills = kills;
    }

    public void incKills() {
        this.kills++;
    }

    public long getTotalCoinsInvested() {
        return totalCoinsInvested;
    }

    public void setTotalCoinsInvested(long totalCoinsInvested) {
        this.totalCoinsInvested = totalCoinsInvested;
    }

    public void addInvestedCoins(long amount) {
        this.totalCoinsInvested += amount;
    }

    public Map<String, Integer> getOwnedEnchants() {
        return ownedEnchants;
    }

    public void setOwnedEnchants(Map<String, Integer> ownedEnchants) {
        this.ownedEnchants = ownedEnchants;
    }

    public void addOwnedEnchant(String key, int level) {
        this.ownedEnchants.put(key, Math.max(this.ownedEnchants.getOrDefault(key, 0), level));
    }
}
