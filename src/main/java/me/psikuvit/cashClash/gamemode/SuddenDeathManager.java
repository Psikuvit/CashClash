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
 * This system is reusable for any gamemode that needs sudden death mechanics.
 */
public class SuddenDeathManager {

    // Initial sudden-death period before repeating cycles begin (3 minutes)
    private static final long DEFAULT_INITIAL_CYCLE_MS = 3 * 60 * 1000L;
    private static final long DEFAULT_REPEAT_CYCLE_MS = 3 * 60 * 1000L;
    private final long initialCycleDurationMs;

    private final GameSession session;
    private final Gamemode gamemode;
    private final long repeatCycleDurationMs;
    private boolean cycleActive;
    private final Map<UUID, Long> extraHeartExpiry; // Player UUID -> expiry time in ms
    private boolean inSuddenDeath;
    private int cycleNumber;
    private long cycleDurationMs;
    private long cycleEndsAtMs;
    private BukkitTask cycleTask;
    private final BukkitTask heartExpiryTask;
    public SuddenDeathManager(GameSession session, Gamemode gamemode) {
        this(session, gamemode, DEFAULT_INITIAL_CYCLE_MS, DEFAULT_REPEAT_CYCLE_MS);
    }


    public SuddenDeathManager(GameSession session, Gamemode gamemode, long initialCycleDurationMs, long repeatCycleDurationMs) {
        this.session = session;
        this.gamemode = gamemode;
        this.initialCycleDurationMs = initialCycleDurationMs;
        this.repeatCycleDurationMs = repeatCycleDurationMs;
        this.inSuddenDeath = false;
        this.cycleActive = false;
        this.cycleNumber = 0;
        this.cycleDurationMs = 0L;
        this.cycleEndsAtMs = 0L;
        this.extraHeartExpiry = new HashMap<>();
        this.heartExpiryTask = SchedulerUtils.runTaskTimer(this::removeExpiredHearts, 20L, 20L);
        this.cycleTask = null;
    }

    public void enterSuddenDeath() {
        if (inSuddenDeath) {
            Messages.debug("[SuddenDeathManager] Already in sudden death mode");
            return;
        }

        inSuddenDeath = true;
        cycleNumber = 1;
        startCycle(initialCycleDurationMs);
        Messages.debug("[SuddenDeathManager] Entering sudden death mode");
        // Schedule periodic tick to advance sudden-death cycles automatically every second
        if (cycleTask == null) {
            cycleTask = SchedulerUtils.runTaskTimer(() -> {
                try {
                    CycleTickResult res = tickSuddenDeathCycle();
                    if (res == CycleTickResult.RESOLVED || res == CycleTickResult.INACTIVE) {
                        // Sudden death resolved or became inactive -> stop ticking
                        inSuddenDeath = (res != CycleTickResult.RESOLVED) && inSuddenDeath;
                        cancelTask(cycleTask);
                        cycleTask = null;
                    }
                } catch (Exception e) {
                    Messages.debug("[SuddenDeathManager] Exception during cycle tick: " + e.getMessage());
                }
            }, 20L, 20L);
        }
    }

    /**
     * Advance the current sudden-death cycle by one second.
     *
     * @return the result of the tick, or INACTIVE if sudden death is not active.
     */
    public CycleTickResult tickSuddenDeathCycle() {
        if (!inSuddenDeath || !cycleActive) {
            return CycleTickResult.INACTIVE;
        }

        long remaining = getSuddenDeathCycleRemainingMs();
        if (remaining > 0) {
            return CycleTickResult.RUNNING;
        }

        if (gamemode != null) {
            gamemode.onSuddenDeathCycleEnded();
        }

        if (gamemode != null && gamemode.getWinningTeam() > 0) {
            cycleActive = false;
            Messages.debug("[SuddenDeathManager] Sudden death cycle resolved by Team " + gamemode.getWinningTeam());
            return CycleTickResult.RESOLVED;
        }

        restartCycle();
        return CycleTickResult.RESTARTED;
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

        var ccp = session.getCashClashPlayer(uuid);
        if (ccp != null) {
            ccp.addHealthModifier(2.0);
            Messages.debug("[SuddenDeathManager] Added +2 health to " + player.getName() + " via health modifier system");
        }
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

    public boolean isSuddenDeathCycleActive() {
        return cycleActive;
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

    public int getSuddenDeathCycleNumber() {
        return cycleNumber;
    }

    public int getSuddenDeathCycleRemainingSeconds() {
        long remainingMs = getSuddenDeathCycleRemainingMs();
        return remainingMs < 0 ? -1 : (int) (remainingMs / 1000);
    }

    public long getSuddenDeathCycleRemainingMs() {
        if (!cycleActive || cycleEndsAtMs <= 0) {
            return -1;
        }

        return Math.max(cycleEndsAtMs - System.currentTimeMillis(), 0);
    }

    /**
     * Reset sudden death state for new round
     */
    public void resetForNewRound() {
        inSuddenDeath = false;
        cycleActive = false;
        cycleNumber = 0;
        cycleDurationMs = 0L;
        cycleEndsAtMs = 0L;
        // Cancel any running cycle task
        cancelTask(cycleTask);
        cycleTask = null;

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
        cancelTask(heartExpiryTask);
        cancelTask(cycleTask);
        cycleActive = false;

        // Remove extra heart effects from all players - create a list to avoid ConcurrentModificationException
        List<UUID> playersWithHearts = new ArrayList<>(extraHeartExpiry.keySet());
        for (UUID uuid : playersWithHearts) {
            removeExtraHeart(uuid);
        }
        extraHeartExpiry.clear();

        Messages.debug("[SuddenDeathManager] Cleaned up");
    }

    private void startCycle(long durationMs) {
        cycleActive = true;
        cycleDurationMs = durationMs;
        cycleEndsAtMs = System.currentTimeMillis() + durationMs;
        Messages.debug("[SuddenDeathManager] Starting sudden death cycle " + cycleNumber + " for " + durationMs + "ms");
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

    private void restartCycle() {
        cycleNumber++;
        startCycle(repeatCycleDurationMs);

        if (gamemode != null) {
            gamemode.onSuddenDeathCycleRestart();
        }

        broadcastCycleRestartMessage();
        Messages.debug("[SuddenDeathManager] Sudden death tied - restarting cycle " + cycleNumber + " for " + repeatCycleDurationMs + "ms");
    }

    private void broadcastCycleRestartMessage() {
        if (gamemode == null) {
            return;
        }

        switch (gamemode.getType()) {
            case CAPTURE_THE_FLAG -> Messages.broadcast(session.getPlayers(), "gamemode-ctf.sudden-death-tied-restart");
            case PROTECT_THE_PRESIDENT -> Messages.broadcast(session.getPlayers(), "gamemode-ptp.sudden-death-tied-restart");
        }
    }

    public enum CycleTickResult {
        RUNNING,
        RESTARTED,
        RESOLVED,
        INACTIVE
    }

}


