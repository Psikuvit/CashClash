package me.psikuvit.cashClash.config;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.List;

/**
 * Configuration manager for Cash Clash.
 * Provides access to all configurable values from config.yml.
 */
public class ConfigManager {

    private static ConfigManager instance;
    private FileConfiguration config;
    private final ConfigValidator validator = new ConfigValidator();

    private ConfigManager() {
        loadConfig();
    }

    private void loadConfig() {
        CashClashPlugin plugin = CashClashPlugin.getInstance();
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();

        // Validate and auto-add missing fields
        if (!validator.validateMainConfig(config, true)) {
            Messages.debug("CONFIG", "Main configuration has errors - check warnings above");
        }

        // Save if any fields were added
        if (validator.getAddedCount() > 0) {
            try {
                File configFile = new File(plugin.getDataFolder(), "config.yml");
                config.save(configFile);
                Messages.debug("CONFIG", "Saved config.yml with " + validator.getAddedCount() + " new default values");
            } catch (Exception e) {
                Messages.debug("CONFIG", "Failed to save config.yml: " + e.getMessage());
            }
        }
    }

    public static ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    public void reload() {
        CashClashPlugin.getInstance().reloadConfig();
        loadConfig();
        validator.logConfigDiff("config.yml", 0);
    }

    // ==================== GAME SETTINGS ====================

    /**
     * Check if debug mode is enabled globally.
     */
    public boolean isDebugEnabled() {
        return config.getBoolean("debug", false);
    }

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
}
