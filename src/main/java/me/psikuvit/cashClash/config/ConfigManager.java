package me.psikuvit.cashClash.config;

import me.psikuvit.cashClash.CashClashPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Configuration manager for Cash Clash.
 * Provides access to all configurable values from config.yml.
 */
public class ConfigManager {

    private static ConfigManager instance;
    private FileConfiguration config;

    private ConfigManager() {
        this.config = CashClashPlugin.getInstance().getConfig();
    }

    public static ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    // ==================== GAME SETTINGS ====================

    public int getMinPlayers() {
        return config.getInt("game.min-players", 8);
    }

    public int getMaxPlayers() {
        return config.getInt("game.max-players", 8);
    }

    public int getTotalRounds() {
        return config.getInt("game.total-rounds", 5);
    }

    public int getFirstRound() {
        return config.getInt("game.first-round", 1);
    }

    public int getCombatPhaseDuration() {
        return config.getInt("game.combat-phase-duration", 360);
    }

    public int getShoppingPhaseDuration() {
        return config.getInt("game.shopping-phase-duration", 90);
    }

    public int getFirstRoundShoppingDuration() {
        return config.getInt("game.first-round-shopping-duration", 120);
    }

    public int getRespawnDelay() {
        return config.getInt("game.respawn-delay", 5);
    }

    public int getRespawnProtection() {
        return config.getInt("game.respawn-protection", 15);
    }

    public int getForfeitCombatGrace() {
        return config.getInt("game.forfeit-combat-grace", 5);
    }

    // ==================== ROUND SETTINGS ====================

    public int getEarlyRoundLives() {
        return config.getInt("rounds.early-round-lives", 3);
    }

    public int getLateRoundLives() {
        return config.getInt("rounds.late-round-lives", 1);
    }

    public long getForfeitBonus() {
        return config.getLong("rounds.forfeit-bonus", 10000);
    }

    // ==================== ARMOR RESTRICTIONS ====================

    public int getDiamondUnlockRound() {
        return config.getInt("armor.diamond-unlock-round", 4);
    }

    public int getMaxDiamondPiecesEarly() {
        return config.getInt("armor.max-diamond-pieces-early", 2);
    }

    // ==================== ECONOMY SETTINGS ====================

    public long getRound1Start() {
        return config.getLong("economy.round-1-start", 10000);
    }

    public long getRound2Bonus() {
        return config.getLong("economy.round-2-bonus", 30000);
    }

    public long getRound3Bonus() {
        return config.getLong("economy.round-3-bonus", 50000);
    }

    public long getRound4Bonus() {
        return config.getLong("economy.round-4-bonus", 100000);
    }

    public long getRound5Minimum() {
        return config.getLong("economy.round-5-minimum", 20000);
    }

    public long getRound5Bonus() {
        return config.getLong("economy.round-5-bonus", 10000);
    }

    public long getRound1KillReward() {
        return config.getLong("economy.round-1-kill-reward", 3000);
    }

    public double getRound1TransferFee() {
        return config.getDouble("economy.round-1-transfer-fee", 0.50);
    }

    public double getRound23TransferFee() {
        return config.getDouble("economy.round-2-3-transfer-fee", 0.10);
    }

    public double getRound45TransferFee() {
        return config.getDouble("economy.round-4-5-transfer-fee", 0.05);
    }

    public double getLateRoundStealPercentage() {
        return config.getDouble("economy.late-round-steal-percentage", 0.25);
    }

    // ==================== CASH QUAKE EVENTS ====================

    public int getMinGuaranteedEvents() {
        return config.getInt("cash-quake.min-guaranteed-events", 2);
    }

    public int getMaxEventsPerGame() {
        return config.getInt("cash-quake.max-events-per-game", 10);
    }

    public int getMaxEventsPerRound() {
        return config.getInt("cash-quake.max-events-per-round", 2);
    }

    public long getEventCheckIntervalTicks() {
        return config.getLong("cash-quake.event-check-interval-ticks", 600L);
    }

    public double getEventBaseChance() {
        return config.getDouble("cash-quake.event-base-chance", 0.30);
    }

    // Lottery
    public long getLotteryEntryCost() {
        return config.getLong("cash-quake.lottery.entry-cost", 5000);
    }

    public long getLotteryPrize() {
        return config.getLong("cash-quake.lottery.prize", 10000);
    }

    public int getLotteryDurationSeconds() {
        return config.getInt("cash-quake.lottery.duration-seconds", 30);
    }

    // Weight of Wealth
    public long getWeightOfWealthTax() {
        return config.getLong("cash-quake.weight-of-wealth.tax-cost", 5000);
    }

    public int getWeightOfWealthDurationSeconds() {
        return config.getInt("cash-quake.weight-of-wealth.duration-seconds", 20);
    }

    // Life Steal
    public int getLifeStealDurationMinutes() {
        return config.getInt("cash-quake.life-steal.duration-minutes", 2);
    }

    public double getLifeStealHealthPerKill() {
        return config.getDouble("cash-quake.life-steal.health-per-kill", 4.0);
    }

    public double getLifeStealMaxHealth() {
        return config.getDouble("cash-quake.life-steal.max-health", 40.0);
    }

    // Check Up
    public int getCheckUpDurationMinutes() {
        return config.getInt("cash-quake.check-up.duration-minutes", 2);
    }

    public int getCheckUpMinHearts() {
        return config.getInt("cash-quake.check-up.min-hearts", 1);
    }

    public int getCheckUpMaxHearts() {
        return config.getInt("cash-quake.check-up.max-hearts", 5);
    }

    // Broken Gear
    public int getBrokenGearDurationSeconds() {
        return config.getInt("cash-quake.broken-gear.duration-seconds", 30);
    }

    // Supply Drop
    public int getSupplyDropMinChests() {
        return config.getInt("cash-quake.supply-drop.min-chests", 3);
    }

    public int getSupplyDropMaxExtraChests() {
        return config.getInt("cash-quake.supply-drop.max-extra-chests", 4);
    }

    public int getSupplyDropMinCoins() {
        return config.getInt("cash-quake.supply-drop.min-coins", 1000);
    }

    public int getSupplyDropMaxExtraCoins() {
        return config.getInt("cash-quake.supply-drop.max-extra-coins", 1001);
    }

    // ==================== CUSTOM ITEMS ====================

    // Invisibility Cloak
    public long getInvisCloakCostPerSecond() {
        return config.getLong("custom-items.invis-cloak.cost-per-second", 100);
    }

    public int getInvisCloakCooldownSeconds() {
        return config.getInt("custom-items.invis-cloak.cooldown-seconds", 15);
    }

    public int getInvisCloakUsesPerRound() {
        return config.getInt("custom-items.invis-cloak.uses-per-round", 5);
    }

    // Grenade
    public int getGrenadeFuseSeconds() {
        return config.getInt("custom-items.grenade.fuse-seconds", 3);
    }

    // Boombox
    public int getBoomboxPulseIntervalSeconds() {
        return config.getInt("custom-items.boombox.pulse-interval-seconds", 3);
    }

    public int getBoomboxDurationSeconds() {
        return config.getInt("custom-items.boombox.duration-seconds", 12);
    }

    public int getBoomboxRadius() {
        return config.getInt("custom-items.boombox.radius", 5);
    }

    // ==================== MYTHIC ITEMS ====================

    // Coin Cleaver
    public int getCoinCleaverGrenadeCooldown() {
        return config.getInt("mythic-items.coin-cleaver.grenade-cooldown", 3);
    }

    public int getCoinCleaverGrenadeCost() {
        return config.getInt("mythic-items.coin-cleaver.grenade-cost", 2000);
    }

    public double getCoinCleaverGrenadeDamage() {
        return config.getDouble("mythic-items.coin-cleaver.grenade-damage", 4.0);
    }

    public int getCoinCleaverGrenadeRadius() {
        return config.getInt("mythic-items.coin-cleaver.grenade-radius", 5);
    }

    // ==================== PLAYER DEFAULTS ====================

    public double getDefaultHealth() {
        return config.getDouble("player.default-health", 20.0);
    }

    public double getMaxHealthCap() {
        return config.getDouble("player.max-health-cap", 40.0);
    }

    // ==================== MESSAGES ====================

    public String getPrefix() {
        return config.getString("messages.prefix", "<gold><bold>[Cash Clash]</bold></gold>");
    }

    // ==================== SCOREBOARD ====================

    public String getLobbyScoreboardTitle() {
        return config.getString("scoreboard.lobby.title", "<gold><bold>Cash Clash</bold></gold>");
    }

    public List<String> getLobbyScoreboardLines() {
        return config.getStringList("scoreboard.lobby.lines");
    }

    public String getGameScoreboardTitle() {
        return config.getString("scoreboard.game.title", "<gold><bold>Cash Clash</bold></gold>");
    }

    public List<String> getGameScoreboardLines() {
        return config.getStringList("scoreboard.game.lines");
    }

    // ==================== UTILITY ====================

    public void reload() {
        CashClashPlugin.getInstance().reloadConfig();
        this.config = CashClashPlugin.getInstance().getConfig();
    }
}
