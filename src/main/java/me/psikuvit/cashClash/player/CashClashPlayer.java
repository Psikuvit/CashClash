package me.psikuvit.cashClash.player;

import me.psikuvit.cashClash.kit.Kit;
import me.psikuvit.cashClash.shop.EnchantEntry;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wrapper for player data within a Cash Clash game
 */
public class CashClashPlayer {

    private final UUID uuid;
    private final Player player;

    // Economy
    private long coins;
    private long investedCoins;
    private Investment currentInvestment;
    private int deathsThisRound;

    // Combat stats
    private Kit currentKit;
    private int killStreak;
    private int totalKills;
    private boolean hasFirstBlood;
    private final Map<BonusType, Integer> bonusesEarned;

    // Round tracking
    private int lives;
    private long lastDamageTime;
    private double lowestHealthThisLife;
    private boolean reachedOneHeart;
    private long oneHeartTime;
    private UUID firstDeathRound;

    // Special items
    private int revivalStarsUsed;
    private final List<PurchaseRecord> purchaseHistory;
    private long respawnProtectionUntil;

    private final Map<EnchantEntry, Integer> ownedEnchants;

    public CashClashPlayer(Player player) {
        this.uuid = player.getUniqueId();
        this.player = player;
        this.coins = 0;
        this.investedCoins = 0;
        this.lives = 3;
        this.bonusesEarned = new HashMap<>();
        this.purchaseHistory = new ArrayList<>();
        this.lowestHealthThisLife = 20.0;
        this.respawnProtectionUntil = 0L;
        this.ownedEnchants = new HashMap<>();
    }

    public void initializeRound1() {
        this.coins = 10000;
        this.lives = 3;
        this.deathsThisRound = 0;
    }

    public void initializeRound(int roundNumber) {
        switch (roundNumber) {
            case 2 -> this.coins += 30000;
            case 3 -> this.coins += 50000;
            case 4 -> this.coins += 100000;
            case 5 -> {
                if (this.coins < 20000) {
                    this.coins += 10000;
                }
            }
        }

        // Reset round-specific stats
        this.lives = roundNumber <= 3 ? 3 : 1;
        this.deathsThisRound = 0;
        this.killStreak = 0;
        this.hasFirstBlood = false;
        this.lowestHealthThisLife = 20.0;
        this.reachedOneHeart = false;
    }

    public boolean canAfford(long amount) {
        return coins >= amount;
    }

    public void deductCoins(long amount) {
        coins -= amount;
    }

    public void addCoins(long amount) {
        coins += amount;
    }

    public void handleDeath() {
        lives--;
        deathsThisRound++;
        killStreak = 0;
        lowestHealthThisLife = 20.0;
        reachedOneHeart = false;

        // Handle investment penalties
        if (currentInvestment != null) {
            currentInvestment.recordDeath();
        }
    }

    public void handleKill() {
        totalKills++;
        killStreak++;
    }

    public void resetLife() {
        lowestHealthThisLife = 20.0;
        reachedOneHeart = false;
    }

    public void updateLowestHealth(double health) {
        if (health < lowestHealthThisLife) {
            lowestHealthThisLife = health;
        }

        // Check for close call bonus
        if (health <= 2.0 && !reachedOneHeart) {
            reachedOneHeart = true;
            oneHeartTime = System.currentTimeMillis();
        }
    }

    public boolean checkCloseCallBonus() {
        if (reachedOneHeart && lowestHealthThisLife > 2.0) {
            long timeSinceOneHeart = System.currentTimeMillis() - oneHeartTime;
            // 10 seconds
            return timeSinceOneHeart >= 10000;
        }
        return false;
    }

    public void addOwnedEnchant(EnchantEntry entry, int level) {
        ownedEnchants.put(entry, Math.max(ownedEnchants.getOrDefault(entry, 0), level));
    }

    public int getOwnedEnchantLevel(EnchantEntry entry) {
        return ownedEnchants.getOrDefault(entry, 0);
    }

    public Map<EnchantEntry, Integer> getOwnedEnchants() { return Map.copyOf(ownedEnchants); }

    public void addPurchase(PurchaseRecord record) {
        purchaseHistory.add(record);
    }

    public PurchaseRecord popLastPurchase() {
        if (purchaseHistory.isEmpty()) return null;
        return purchaseHistory.removeLast();
    }

    public List<PurchaseRecord> getPurchaseHistory() { return List.copyOf(purchaseHistory); }

    public void earnBonus(BonusType type) {
        bonusesEarned.put(type, bonusesEarned.getOrDefault(type, 0) + 1);
        addCoins(type.getReward());
    }

    public void setRespawnProtection(long millisFromNow) {
        this.respawnProtectionUntil = System.currentTimeMillis() + millisFromNow;
    }

    public boolean isRespawnProtected() {
        return System.currentTimeMillis() < this.respawnProtectionUntil;
    }

    public long getRespawnProtectionUntil() {
        return respawnProtectionUntil;
    }

    // Getters and setters
    public UUID getUuid() {
        return uuid;
    }

    public Player getPlayer() {
        return player;
    }

    public long getCoins() {
        return coins;
    }

    public void setCoins(long coins) {
        this.coins = coins;
    }

    public long getInvestedCoins() {
        return investedCoins;
    }

    public void setInvestedCoins(long invested) {
        this.investedCoins = invested;
    }

    public Investment getCurrentInvestment() {
        return currentInvestment;
    }

    public void setCurrentInvestment(Investment investment) {
        this.currentInvestment = investment;
    }

    public int getDeathsThisRound() {
        return deathsThisRound;
    }

    public Kit getCurrentKit() {
        return currentKit;
    }

    public void setCurrentKit(Kit kit) {
        this.currentKit = kit;
    }

    public int getKillStreak() {
        return killStreak;
    }

    public int getTotalKills() {
        return totalKills;
    }

    public boolean hasFirstBlood() {
        return hasFirstBlood;
    }

    public void setFirstBlood(boolean firstBlood) {
        this.hasFirstBlood = firstBlood;
    }

    public int getLives() {
        return lives;
    }

    public void setLives(int lives) {
        this.lives = lives;
    }

    public long getLastDamageTime() {
        return lastDamageTime;
    }

    public void setLastDamageTime(long time) {
        this.lastDamageTime = time;
    }

    public int getRevivalStarsUsed() {
        return revivalStarsUsed;
    }

    public void incrementRevivalStarsUsed() {
        this.revivalStarsUsed++;
    }

    public UUID getFirstDeathRound() {
        return firstDeathRound;
    }

    public void setFirstDeathRound(UUID round) {
        this.firstDeathRound = round;
    }
}
