package me.psikuvit.cashClash.gamemode;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Centralized Sudden Death Manager for all gamemodes.
 * Handles:
 * - Entering and tracking sudden death state
 * - Final Stand timer management
 * - Extra heart bonuses and tracking
 *
 * This system is reusable for any gamemode that needs sudden death mechanics.
 */
public class SuddenDeathManager {

    private static final long HEART_DURATION_MS = 45 * 1000;
    private static final long FINAL_STAND_DURATION_MS = 5 * 60 * 1000; // 5 minutes

    private final GameSession session;
    private final Gamemode gamemode;
    private final Map<UUID, Long> extraHeartExpiry; // Player UUID -> expiry time in ms
    private boolean inSuddenDeath;
    private boolean finalStandActive;
    private BukkitTask finalStandTask;
    private final BukkitTask heartExpiryTask;
    private long finalStandStartMs; // Start time of the current 5-minute timer

    public SuddenDeathManager(GameSession session, Gamemode gamemode) {
        this.session = session;
        this.gamemode = gamemode;
        this.inSuddenDeath = false;
        this.finalStandActive = false;
        this.finalStandTask = null;
        this.finalStandStartMs = 0;
        this.extraHeartExpiry = new HashMap<>();
        this.heartExpiryTask = SchedulerUtils.runTaskTimer(this::removeExpiredHearts, 20L, 20L);
    }

    /**
     * Enter sudden death mode
     */
    public void enterSuddenDeath() {
        enterSuddenDeath(true);
    }

    public void enterSuddenDeath(boolean startFinalStandTimer) {
        if (inSuddenDeath) {
            Messages.debug("[SuddenDeathManager] Already in sudden death mode");
            return;
        }

        inSuddenDeath = true;
        finalStandActive = false;
        Messages.debug("[SuddenDeathManager] Entering sudden death mode");

        // Start final stand timer
        if (startFinalStandTimer) {
            startFinalStandTimer();
        }
    }

    /**
     * Start the final stand timer (5 minutes)
     * After 5 minutes, final stand is activated
     */
    private void startFinalStandTimer() {
        if (finalStandTask != null && !finalStandTask.isCancelled()) {
            return;
        }
        finalStandStartMs = System.currentTimeMillis();
        long ticksDelay = (FINAL_STAND_DURATION_MS / 50); // Convert milliseconds to ticks
        finalStandTask = SchedulerUtils.runTaskLater(this::activateFinalStand, ticksDelay);
    }

    public void startFinalStandTimerIfNeeded() {
        if (inSuddenDeath && !finalStandActive) {
            startFinalStandTimer();
        }
    }

    /**
     * Activate final stand mode and notify the gamemode
     * The gamemode is responsible for:
     * - Implementing custom final-stand mechanics (elimination, border, etc.)
     * - Deciding if a winner has been declared
     * - Calling resetSuddenDeathCycle() if the match should continue tied
     */
    private void activateFinalStand() {
        finalStandActive = true;
        Messages.debug("[SuddenDeathManager] Final stand activated after 5 minutes");

        // Notify gamemode to handle final stand activation with custom game-specific logic
        if (gamemode != null) {
            gamemode.onFinalStandActivated();
        }
    }

    /**
     * Reset the sudden death cycle and restart the 5-minute timer
     * Called by the gamemode if a cycle ends in a tie and play should continue
     */
    public void resetSuddenDeathCycle() {
        if (!inSuddenDeath) {
            Messages.debug("[SuddenDeathManager] Cannot reset cycle - not in sudden death");
            return;
        }

        finalStandActive = false;
        Messages.debug("[SuddenDeathManager] Resetting sudden death cycle - restarting 5-minute timer");
        startFinalStandTimer();
    }

    /**
     * Check if player has an active extra heart and remove it if expired
     *
     * @return true if player has an active extra heart
     */
    public boolean updateAndCheckExtraHeart(UUID playerUuid) {
        if (!extraHeartExpiry.containsKey(playerUuid)) {
            return false;
        }

        long expiryTime = extraHeartExpiry.get(playerUuid);
        if (System.currentTimeMillis() >= expiryTime) {
            removeExtraHeart(playerUuid);
            return false;
        }

        return true;
    }

    /**
     * Apply extra heart with custom duration
     */
    public void applyExtraHeart(Player player, long durationMs) {
        UUID uuid = player.getUniqueId();
        long expiryTime = System.currentTimeMillis() + durationMs;
        extraHeartExpiry.put(uuid, expiryTime);
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, (int) (durationMs / 50), 0, false, false));

        Messages.debug("[SuddenDeathManager] Applied extra heart to: " + player.getName() + " for " + durationMs + "ms");

        // Use centralized health system to add temporary hearts (1 heart = 2 health)
        var ccp = session.getCashClashPlayer(uuid);
        if (ccp != null) {
            ccp.addHealthModifier(2.0);
            Messages.debug("[SuddenDeathManager] Added +2 health to " + player.getName() + " via health modifier system");
        }
    }

    /**
     * Remove extra heart from a player and restore max health
     */
    private void removeExtraHeart(UUID playerUuid) {
        Player p = Bukkit.getPlayer(playerUuid);
        if (p != null && p.isOnline()) {
            // Use centralized health system to remove the temporary heart
            CashClashPlayer ccp = session.getCashClashPlayer(playerUuid);
            if (ccp != null) {
                ccp.removeHealthModifier(2.0);
                Messages.debug("[SuddenDeathManager] Removed +2 health from " + p.getName() + " via health modifier system");
            }
        }
        extraHeartExpiry.remove(playerUuid);
    }

    /**
     * Reapply extra heart effect when player spawns (if still in sudden death and has one)
     */
    public void onPlayerSpawn(Player player) {
        UUID playerUuid = player.getUniqueId();

        if (inSuddenDeath && extraHeartExpiry.containsKey(playerUuid)) {
            long expiryTime = extraHeartExpiry.get(playerUuid);
            long remainingMs = expiryTime - System.currentTimeMillis();

            if (remainingMs > 0) {
                // Reapply only current health state; do not stack another temporary modifier on each respawn
                var ccp = session.getCashClashPlayer(playerUuid);
                if (ccp != null) {
                    ccp.applyHealth();
                }
                Messages.debug("[SuddenDeathManager] Reapplied extra heart to respawned player: " + player.getName());
            } else {
                removeExtraHeart(playerUuid);
            }
        }
    }

    /**
     * Check if currently in sudden death
     */
    public boolean isInSuddenDeath() {
        return inSuddenDeath;
    }

    /**
     * Check if final stand is active
     */
    public boolean isFinalStandActive() {
        return finalStandActive;
    }

    /**
     * Reset sudden death state for new round
     */
    public void resetForNewRound() {
        inSuddenDeath = false;
        finalStandActive = false;
        cancelTask(finalStandTask);
        finalStandTask = null;

        // Clear all extra hearts - create a list to avoid ConcurrentModificationException
        List<UUID> playersWithHearts = new ArrayList<>(extraHeartExpiry.keySet());
        for (UUID uuid : playersWithHearts) {
            removeExtraHeart(uuid);
        }
        extraHeartExpiry.clear();

        Messages.debug("[SuddenDeathManager] Reset for new round");
    }

    /**
     * Cleanup when game ends
     */
    public void cleanup() {
        cancelTask(finalStandTask);
        cancelTask(heartExpiryTask);

        // Remove extra heart effects from all players - create a list to avoid ConcurrentModificationException
        List<UUID> playersWithHearts = new ArrayList<>(extraHeartExpiry.keySet());
        for (UUID uuid : playersWithHearts) {
            removeExtraHeart(uuid);
        }
        extraHeartExpiry.clear();

        Messages.debug("[SuddenDeathManager] Cleaned up");
    }

    /**
     * Cancel a task if it exists
     */
    private void cancelTask(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private void removeExpiredHearts() {
        long now = System.currentTimeMillis();
        List<UUID> expired = extraHeartExpiry.entrySet().stream()
                .filter(entry -> now >= entry.getValue())
                .map(Map.Entry::getKey)
                .toList();

        for (UUID uuid : expired) {
            removeExtraHeart(uuid);
        }
    }

    /**
     * Get remaining time for a player's extra heart in milliseconds
     */
    public long getExtraHeartRemainingMs(UUID playerUuid) {
        if (!extraHeartExpiry.containsKey(playerUuid)) {
            return -1;
        }

        long expiryTime = extraHeartExpiry.get(playerUuid);
        long remaining = expiryTime - System.currentTimeMillis();
        return Math.max(remaining, 0);
    }

    /**
     * Check if player has an extra heart
     */
    public boolean hasExtraHeart(UUID playerUuid) {
        return extraHeartExpiry.containsKey(playerUuid);
    }

    /**
     * Get remaining time for the current sudden death timer in seconds
     * Returns -1 if not in sudden death or timer not active
     */
    public int getSuddenDeathTimerRemainingSeconds() {
        if (!inSuddenDeath || finalStandStartMs <= 0) {
            return -1;
        }

        long elapsedMs = System.currentTimeMillis() - finalStandStartMs;
        long remainingMs = Math.max(0, FINAL_STAND_DURATION_MS - elapsedMs);
        return (int) (remainingMs / 1000);
    }

    /**
     * Get remaining time for the current sudden death timer in milliseconds
     * Returns -1 if not in sudden death or timer not active
     */
    public long getFinalStandRemainingMs() {
        if (!inSuddenDeath || finalStandStartMs <= 0) {
            return -1;
        }

        long elapsedMs = System.currentTimeMillis() - finalStandStartMs;
        return Math.max(0, FINAL_STAND_DURATION_MS - elapsedMs);
    }

}


