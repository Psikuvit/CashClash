package me.psikuvit.cashClash.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerData {

    private UUID uuid;
    private int wins;
    private int losses;
    private int deaths;
    private int kills;
    private long totalCoinsInvested;
    private long totalCoinsEarned;
    // Kit name -> (slot -> item identifier) for custom layouts
    private Map<String, Map<Integer, String>> kitLayouts;

    // No-arg constructor for Gson
    public PlayerData() {}

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.wins = 0;
        this.losses = 0;
        this.deaths = 0;
        this.kills = 0;
        this.totalCoinsInvested = 0L;
        this.totalCoinsEarned = 0L;
        this.kitLayouts = new HashMap<>();
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

    public int getLosses() {
        return losses;
    }

    public void setLosses(int losses) {
        this.losses = losses;
    }

    public void incLosses() {
        this.losses++;
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

    public long getTotalCoinsEarned() {
        return totalCoinsEarned;
    }

    public void setTotalCoinsEarned(long totalCoinsEarned) {
        this.totalCoinsEarned = totalCoinsEarned;
    }

    public void addEarnedCoins(long amount) {
        this.totalCoinsEarned += amount;
    }

    public Map<String, Map<Integer, String>> getKitLayouts() {
        if (kitLayouts == null) kitLayouts = new HashMap<>();
        return kitLayouts;
    }

    public void setKitLayouts(Map<String, Map<Integer, String>> kitLayouts) {
        this.kitLayouts = kitLayouts;
    }

    public Map<Integer, String> getKitLayout(String kitName) {
        return kitLayouts.get(kitName);
    }

    public void setKitLayout(String kitName, Map<Integer, String> slotItemMap) {
        kitLayouts.put(kitName, slotItemMap);
    }

    public boolean hasKitLayout(String kitName) {
        return kitLayouts != null && kitLayouts.containsKey(kitName);
    }
}
