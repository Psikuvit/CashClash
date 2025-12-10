package me.psikuvit.cashClash.config;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.configuration.file.FileConfiguration;
import net.kyori.adventure.text.Component;

import java.util.List;

/**
 * Configuration manager for Cash Clash
 */
public class ConfigManager {

    private static ConfigManager instance;
    private final FileConfiguration config;

    private ConfigManager() {
        this.config = CashClashPlugin.getInstance().getConfig();
    }

    public static ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    public int getMinPlayers() {
        return config.getInt("game.min-players", 8);
    }

    public int getMaxPlayers() {
        return config.getInt("game.max-players", 8);
    }

    public int getCombatPhaseDuration() {
        return config.getInt("game.combat-phase-duration", 360);
    }

    public int getShoppingPhaseDuration() {
        return config.getInt("game.shopping-phase-duration", 90);
    }

    public boolean isForfeitEnabled() {
        return config.getBoolean("game.forfeit-enabled", true);
    }

    public int getForfeitDelay() {
        return config.getInt("game.forfeit-delay", 10);
    }

    public int getForfeitCombatGrace() {
        return config.getInt("game.forfeit-combat-grace", 5); // seconds
    }

    public int getRespawnDelay() {
        return config.getInt("game.respawn-delay", 10); // seconds until respawn
    }

    public int getRespawnProtection() {
        return config.getInt("game.respawn-protection", 15); // seconds where pearls/bounce pads disabled
    }

    public int getEarlyRoundLives() {
        return config.getInt("rounds.early-round-lives", 3);
    }

    public int getLateRoundLives() {
        return config.getInt("rounds.late-round-lives", 1);
    }

    public long getForfeitBonus() {
        return config.getLong("rounds.forfeit-bonus", 10000);
    }

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

    public int getMinCashQuakeEvents() {
        return config.getInt("cash-quake.min-events", 2);
    }

    public int getMaxCashQuakeEvents() {
        return config.getInt("cash-quake.max-events", 10);
    }

    public int getMaxCashQuakePerRound() {
        return config.getInt("cash-quake.max-per-round", 2);
    }

    public double getSupplyDropChance() {
        return config.getDouble("cash-quake.supply-drop-chance", 0.5);
    }

    /**
     * Return configured prefix (MiniMessage string)
     */
    public String getPrefix() {
        return config.getString("messages.prefix", "<gold><bold>[Cash Clash]</bold></gold>");
    }

    /**
     * Return a configured message by key as raw MiniMessage string
     */
    public String getMessage(String key) {
        return config.getString("messages." + key, "");
    }

    /**
     * Convenience: parse configured message key into a Component (uses Messages.parse)
     */
    public Component getMessageComponent(String key) {
        String raw = getMessage(key);
        return Messages.parse(raw);
    }

    public void reload() {
        CashClashPlugin.getInstance().reloadConfig();
    }


    public String getLobbyScoreboardTitle() {
        return config.getString("scoreboard.lobby.title", "<gold><bold>Cash Clash</bold></gold>");
    }

    public List<String> getLobbyScoreboardLines() {
        return config.getStringList("scoreboard.lobby.lines");
    }

    public boolean isLobbyScoreboardEnabled() {
        return config.getBoolean("scoreboard.lobby.enabled", true);
    }

    public String getGameScoreboardTitle() {
        return config.getString("scoreboard.game.title", "<gold><bold>Cash Clash</bold></gold>");
    }

    public List<String> getGameScoreboardLines() {
        return config.getStringList("scoreboard.game.lines");
    }

    public boolean isGameScoreboardEnabled() {
        return config.getBoolean("scoreboard.game.enabled", true);
    }

}
