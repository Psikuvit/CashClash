package me.psikuvit.cashClash.config;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Configuration manager for Cash Clash.
 * Provides access to all configurable values from config.yml.
 */
public class ConfigManager {

    private FileConfiguration config;
    private final ConfigValidator validator = new ConfigValidator();

    public ConfigManager() {
        loadConfig();
    }

    private void loadConfig() {
        CashClashPlugin plugin = CashClashPlugin.getInstance();

        plugin.saveDefaultConfig();
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        this.config = YamlConfiguration.loadConfiguration(configFile);

        plugin.getLogger().info("Validating config.yml...");

        boolean hasIssues = !validator.validateMainConfig(config, true);

        plugin.getLogger().info("Validation complete. Added count: " + validator.getAddedCount());

        if (hasIssues) {
            plugin.getLogger().warning("Main configuration has validation issues - check warnings above");
        }
        // Save if any fields were added
        if (validator.getAddedCount() > 0) {
            plugin.getLogger().info("Attempting to save config with " + validator.getAddedCount() + " new values...");
            try {
                config.save(configFile);
                plugin.getLogger().info("Successfully saved config.yml with new default values");
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save config.yml: " + e.getMessage());
            }
        } else {
            plugin.getLogger().info("No new values to add, config is up to date");
        }
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

    public int getBuffSelectionDuration() {
        return config.getInt("game.buff-selection-duration", 15);
    }

    public int getSuddenDeathPhaseDuration() {
        return config.getInt("game.sudden-death-phase-duration", 180);
    }

    public int getPhaseCountdownWarningSeconds() {
        return config.getInt("game.phase-countdown-warning-seconds", 3);
    }

    public int getFinalStandDurationSeconds() {
        return config.getInt("game.final-stand-duration-seconds", 180);
    }

    public int getSuddenDeathInitialCycleSeconds() {
        return config.getInt("game.sudden-death-initial-cycle-seconds", 180);
    }

    public int getSuddenDeathRepeatCycleSeconds() {
        return config.getInt("game.sudden-death-repeat-cycle-seconds", 180);
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

    public double getDeathSpectatorRiseHeight() {
        return config.getDouble("game.death-spectator-rise-height", 4.0);
    }

    public int getDeathSpectatorRiseTicks() {
        return config.getInt("game.death-spectator-rise-ticks", 16);
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

    public long getLossStreak1Bonus() {
        return config.getLong("rounds.loss-streak-1-bonus", 5000);
    }

    public long getLossStreak2Bonus() {
        return config.getLong("rounds.loss-streak-2-bonus", 7500);
    }

    public long getLossStreak3Bonus() {
        return config.getLong("rounds.loss-streak-3-bonus", 10000);
    }

    public long getKillTeamSplitBonus() {
        return config.getLong("rounds.kill-team-split-bonus", 7500);
    }

    public long getPlayerBonusAmount() {
        return config.getLong("rounds.player-bonus-amount", 2000);
    }

    public int getKillstreakInterval() {
        return config.getInt("rounds.killstreak-interval", 4);
    }

    // ==================== ARMOR RESTRICTIONS ====================

    public int getDiamondUnlockRound() {
        return config.getInt("armor.diamond-unlock-round", 4);
    }

    public int getMaxDiamondPiecesEarly() {
        return config.getInt("armor.max-diamond-pieces-early", 2);
    }

    // ==================== ECONOMY SETTINGS ====================

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

    public long getKillPoolPerKill() {
        return config.getLong("economy.kill-pool-per-kill", 1000);
    }

    public long getMinRoundPool() {
        return config.getLong("economy.min-round-pool", 15000);
    }

    public long getMaxRoundPool() {
        return config.getLong("economy.max-round-pool", 100000);
    }

    public double getGuiTransferFee() {
        return config.getDouble("economy.gui-transfer-fee", 0.10);
    }

    // ==================== COMBAT ====================

    public double getStrengthNerfMultiplier() {
        return config.getDouble("combat.strength-nerf-multiplier", 0.5);
    }

    public double getPowerNerfMultiplier() {
        return config.getDouble("combat.power-nerf-multiplier", 0.2);
    }

    public int getMaxPowerLevelRegularBow() {
        return config.getInt("combat.max-power-level-regular-bow", 2);
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

    // ==================== GAMEMODE SETTINGS ====================

    // Capture the Flag
    public int getCTFCapturesToWin() {
        return config.getInt("gamemodes.capture-the-flag.normal-captures-to-win", 2);
    }

    public long getCTFCaptureBonusCoins() {
        return config.getLong("gamemodes.capture-the-flag.capture-bonus-coins", 15000);
    }

    public double getCTFFinalStandCarrierHealthPenalty() {
        return config.getDouble("gamemodes.capture-the-flag.final-stand-carrier-health-penalty", 4.0);
    }

    public long getCTFCaptureBonusTimerMs() {
        return config.getLong("gamemodes.capture-the-flag.capture-bonus-timer-ms", 45000);
    }

    public long getCTFHeartBonusDurationMs() {
        return config.getLong("gamemodes.capture-the-flag.heart-bonus-duration-ms", 45000);
    }

    public double getCTFPickupCircleRadius() {
        return config.getDouble("gamemodes.capture-the-flag.pickup-circle-radius", 1.5);
    }

    public double getCTFScoreZoneRadius() {
        return config.getDouble("gamemodes.capture-the-flag.score-zone-radius", 1.5);
    }

    public double getCTFMaxHeightDifference() {
        return config.getDouble("gamemodes.capture-the-flag.max-height-difference", 5.0);
    }

    public int getCTFCarrierGlowIntervalTicks() {
        return config.getInt("gamemodes.capture-the-flag.carrier-glow-interval-ticks", 100);
    }

    public int getCTFCarrierGlowDurationTicks() {
        return config.getInt("gamemodes.capture-the-flag.carrier-glow-duration-ticks", 10);
    }

    public int getCTFCarrierGlowAmplifier() {
        return config.getInt("gamemodes.capture-the-flag.carrier-glow-amplifier", 0);
    }

    public long getCTFPlateActivationTimeMs() {
        return config.getLong("gamemodes.capture-the-flag.plate-activation-time-ms", 3000);
    }

    // Kill Confirm
    public int getKCScoreToWin() {
        return config.getInt("gamemodes.kill-confirm.score-to-win", 16);
    }

    public int getKCTripleKillStreak() {
        return config.getInt("gamemodes.kill-confirm.triple-kill-streak", 3);
    }

    public long getKCZoneActivationDelayMs() {
        return config.getLong("gamemodes.kill-confirm.zone-activation-delay-ms", 1000);
    }

    public long getKCZoneLifespanMs() {
        return config.getLong("gamemodes.kill-confirm.zone-lifespan-ms", 9000);
    }

    public long getKCBonusZoneLifespanMs() {
        return config.getLong("gamemodes.kill-confirm.bonus-zone-lifespan-ms", 13000);
    }

    public long getKCCaptureDurationMs() {
        return config.getLong("gamemodes.kill-confirm.capture-duration-ms", 4000);
    }

    public long getKCFinalStandCaptureDurationMs() {
        return config.getLong("gamemodes.kill-confirm.final-stand-capture-duration-ms", 2000);
    }

    public long getKCMoneyBonus() {
        return config.getLong("gamemodes.kill-confirm.money-bonus", 15000);
    }

    public long getKCHeartBonusDurationMs() {
        return config.getLong("gamemodes.kill-confirm.heart-bonus-duration-ms", 45000);
    }

    public double getKCBeamHeight() {
        return config.getDouble("gamemodes.kill-confirm.beam-height", 6.0);
    }

    public double getKCBeamYOffset() {
        return config.getDouble("gamemodes.kill-confirm.beam-y-offset", 0.2);
    }

    public int getKCBeamDurationTicks() {
        return config.getInt("gamemodes.kill-confirm.beam-duration-ticks", 10);
    }

    public double getKCZoneHalfWidth() {
        return config.getDouble("gamemodes.kill-confirm.zone-half-width", 1.5);
    }

    public double getKCZoneVerticalTolerance() {
        return config.getDouble("gamemodes.kill-confirm.zone-vertical-tolerance", 2.0);
    }

    public int getKCZoneSafeSpawnSearchRadius() {
        return config.getInt("gamemodes.kill-confirm.zone-safe-spawn-search-radius", 8);
    }

    public int getKCZoneGroundSearchMaxDepth() {
        return config.getInt("gamemodes.kill-confirm.zone-ground-search-max-depth", 64);
    }

    // Protect the President
    public int getPTPSelectionTimeSeconds() {
        return config.getInt("gamemodes.protect-the-president.selection-time-seconds", 15);
    }

    public int getPTPKillBonusThreshold() {
        return config.getInt("gamemodes.protect-the-president.kill-bonus-threshold", 2);
    }

    public long getPTPKillBonusAmount() {
        return config.getLong("gamemodes.protect-the-president.kill-bonus-amount", 15000);
    }

    public long getPTPHeartDurationMs() {
        return config.getLong("gamemodes.protect-the-president.heart-duration-ms", 45000);
    }

    public int getPTPCapturesToWin() {
        return config.getInt("gamemodes.protect-the-president.captures-to-win", 2);
    }

    public int getPTPTankResistanceAmplifier() {
        return config.getInt("gamemodes.protect-the-president.tank-resistance-amplifier", 3);
    }

    public int getPTPTankSlownessAmplifier() {
        return config.getInt("gamemodes.protect-the-president.tank-slowness-amplifier", 3);
    }

    public double getPTPHpHealAmount() {
        return config.getDouble("gamemodes.protect-the-president.hp-heal-amount", 4.0);
    }

    // ==================== SEQUENCE TITLE DURATIONS ====================

    public long getDefaultTitleFadeInMs() {
        return config.getLong("sequences.title-times.default-fade-in-ms", 250);
    }

    public long getDefaultTitleStayMs() {
        return config.getLong("sequences.title-times.default-stay-ms", 5000);
    }

    public long getDefaultTitleFadeOutMs() {
        return config.getLong("sequences.title-times.default-fade-out-ms", 250);
    }

    public long getSuddenDeathTitleFadeInMs() {
        return config.getLong("sequences.title-times.sudden-death-fade-in-ms", 500);
    }

    public long getSuddenDeathTitleStayMs() {
        return config.getLong("sequences.title-times.sudden-death-stay-ms", 5000);
    }

    public long getSuddenDeathTitleFadeOutMs() {
        return config.getLong("sequences.title-times.sudden-death-fade-out-ms", 500);
    }

    public long getVictoryTitleFadeInMs() {
        return config.getLong("sequences.title-times.victory-fade-in-ms", 500);
    }

    public long getVictoryTitleStayMs() {
        return config.getLong("sequences.title-times.victory-stay-ms", 9000);
    }

    public long getVictoryTitleFadeOutMs() {
        return config.getLong("sequences.title-times.victory-fade-out-ms", 500);
    }

    public int getRevealBlindnessTicks() {
        return config.getInt("sequences.reveal-blindness-ticks", 300);
    }

    public double getRoundStartRevealHoldSeconds() {
        return config.getDouble("sequences.hold-seconds.round-start-reveal", 4);
    }

    public double getPresidentRevealHoldSeconds() {
        return config.getDouble("sequences.hold-seconds.president-reveal", 3.5);
    }

    public double getShieldRevealDeterminingHoldSeconds() {
        return config.getDouble("sequences.hold-seconds.shield-reveal-determining", 2);
    }

    public double getShieldRevealResultHoldSeconds() {
        return config.getDouble("sequences.hold-seconds.shield-reveal-result", 3);
    }

    public double getRoundEndResultHoldSeconds() {
        return config.getDouble("sequences.hold-seconds.round-end-result", 5);
    }

    public double getSuddenDeathPreAnnouncementHoldSeconds() {
        return config.getDouble("sequences.hold-seconds.sudden-death-pre-announcement", 1);
    }

    public double getVictoryResultHoldSeconds() {
        return config.getDouble("sequences.hold-seconds.victory-result", 10);
    }

    // ==================== MESSAGES ====================

    public String getPrefix() {
        return Messages.commandPrefix();
    }

    // ==================== SCOREBOARD ====================

    public String getLobbyScoreboardTitle() {
        return config.getString("scoreboard.lobby.title", "<gold><bold>Cash Clash</bold></gold>");
    }

    public List<String> getLobbyScoreboardLines() {
        return config.getStringList("scoreboard.lobby.lines");
    }

    public String getGameScoreboardTitle() {
        return config.getString("scoreboard.game.title", "<gold><bold>Round {round} - {phase}</bold></gold>");
    }

    public List<String> getGameScoreboardLines() {
        return config.getStringList("scoreboard.game.lines");
    }

    public String getCTFScoreboardTitle() {
        return config.getString("scoreboard.capture-the-flag.title", "<gold><bold>Capture the Flag</bold></gold>");
    }

    public List<String> getCTFScoreboardLines() {
        return config.getStringList("scoreboard.capture-the-flag.lines");
    }

    public String getPTPScoreboardTitle() {
        return config.getString("scoreboard.protect-the-president.title", "<gold><bold>Protect the President</bold></gold>");
    }

    public List<String> getPTPScoreboardLines() {
        return config.getStringList("scoreboard.protect-the-president.lines");
    }

    public String getCTFSuddenDeathScoreboardTitle() {
        return config.getString("scoreboard.capture-the-flag-sudden-death.title", "<gold><bold>⚡ SUDDEN DEATH ⚡</bold></gold>");
    }

    public List<String> getCTFSuddenDeathScoreboardLines() {
        return config.getStringList("scoreboard.capture-the-flag-sudden-death.lines");
    }

    public String getPTPSuddenDeathScoreboardTitle() {
        return config.getString("scoreboard.protect-the-president-sudden-death.title", "<gold><bold>⚡ SUDDEN DEATH ⚡</bold></gold>");
    }

    public List<String> getPTPSuddenDeathScoreboardLines() {
        return config.getStringList("scoreboard.protect-the-president-sudden-death.lines");
    }

    public String getKCScoreboardTitle() {
        return config.getString("scoreboard.kill-confirm.title", "<gold><bold>Kill Confirm</bold></gold>");
    }

    public List<String> getKCScoreboardLines() {
        return config.getStringList("scoreboard.kill-confirm.lines");
    }

    public String getKCSuddenDeathScoreboardTitle() {
        return config.getString("scoreboard.kill-confirm-sudden-death.title", "<gold><bold>⚡ SUDDEN DEATH ⚡</bold></gold>");
    }

    public List<String> getKCSuddenDeathScoreboardLines() {
        return config.getStringList("scoreboard.kill-confirm-sudden-death.lines");
    }

    // ==================== NPC SETTINGS ====================

    /**
     * Get the display name for the arena NPC.
     */
    public String getArenaNPCDisplayName() {
        return config.getString("npc.arena.display-name", "<gold><bold>Arena Selector</bold></gold>");
    }

    /**
     * Get the skin texture value for the arena NPC.
     * This is the base64 encoded texture value from Minecraft skin data.
     */
    public String getArenaNPCSkinURL() {
        return config.getString("npc.arena.skin-url", "");
    }

    // ==================== LEADERBOARD SETTINGS ====================

    /**
     * How many entries are shown per leaderboard board.
     */
    public int getLeaderboardSize() {
        return config.getInt("leaderboard.size", 10);
    }

    /**
     * How often (minutes) the leaderboards are recomputed by the async worker.
     * 0 disables automatic refresh (boards are only computed on startup).
     */
    public int getLeaderboardRefreshMinutes() {
        return config.getInt("leaderboard.refresh-minutes", 5);
    }

    // ==================== AFK SETTINGS ====================

    /**
     * How long (minutes) a player can stay idle in the lobby before being kicked.
     * 0 disables the AFK kick.
     */
    public int getAfkLobbyKickMinutes() {
        return config.getInt("afk.lobby-kick-minutes", 5);
    }

    /**
     * Seconds before the AFK timeout at which the player is warned. 0 disables the warning.
     */
    public int getAfkWarningSeconds() {
        return config.getInt("afk.warning-seconds", 15);
    }

    // ==================== REJOIN SETTINGS ====================

    /**
     * Check if the rejoin system is enabled.
     */
    public boolean isRejoinEnabled() {
        return config.getBoolean("rejoin.enabled", true);
    }

    /**
     * Get the timeout in seconds for rejoining after disconnect.
     */
    public int getRejoinTimeoutSeconds() {
        return config.getInt("rejoin.timeout-seconds", 120);
    }

    /**
     * Check if inventory should be restored on rejoin.
     */
    public boolean isRejoinRestoreInventory() {
        return config.getBoolean("rejoin.restore-inventory", true);
    }

    /**
     * Check if balance should be restored on rejoin.
     */
    public boolean isRejoinRestoreBalance() {
        return config.getBoolean("rejoin.restore-balance", true);
    }

    // ==================== SEQUENCE SETTINGS ====================

    /**
     * Check if scripted title/freeze sequences (round start, president reveal, shield
     * reveal, round end, sudden death, victory) are enabled.
     */
    public boolean isSequencesEnabled() {
        return config.getBoolean("sequences.enabled", true);
    }
}
