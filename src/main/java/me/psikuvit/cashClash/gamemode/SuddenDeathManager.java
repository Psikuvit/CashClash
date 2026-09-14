package me.psikuvit.cashClash.gamemode;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    private final long initialCycleDurationMs;

    private final GameSession session;
    private final Gamemode gamemode;
    private final long repeatCycleDurationMs;
    private boolean cycleActive;
    private final Set<UUID> extraHeartHolders; // players currently holding a sudden-death extra heart
    private boolean inSuddenDeath;
    private int cycleNumber;
    private long cycleDurationMs;
    private long cycleEndsAtMs;
    private BukkitTask cycleTask;
    public SuddenDeathManager(GameSession session, Gamemode gamemode) {
        this(session, gamemode,
                CashClashPlugin.getInstance().getConfigManager().getSuddenDeathInitialCycleSeconds() * 1000L,
                CashClashPlugin.getInstance().getConfigManager().getSuddenDeathRepeatCycleSeconds() * 1000L);
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
        this.extraHeartHolders = new HashSet<>();
        this.cycleTask = null;
    }

    public void enterSuddenDeath() {
        if (inSuddenDeath) {
            Messages.debug("[SuddenDeathManager] Already in sudden death mode");
            return;
        }

        inSuddenDeath = true;
        Messages.debug("[SuddenDeathManager] Entering sudden death mode (buy phase) - cycle timer starts at combat phase");
    }

    /**
     * Starts the sudden-death cycle countdown. Split out from {@link #enterSuddenDeath()} so the
     * buy phase can announce sudden death and swap in its economy without the 3-minute clock
     * already burning away while players are still shopping - the actual countdown only begins
     * once the caller's combat phase starts.
     */
    public void startCycleTimer() {
        if (!inSuddenDeath || cycleActive) {
            return;
        }

        cycleNumber = 1;
        startCycle(initialCycleDurationMs);
        Messages.debug("[SuddenDeathManager] Sudden death cycle timer started");
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
     * Grant a permanent sudden-death extra heart - lasts for the rest of the match (cleared only
     * on round reset / game end), not a timed bonus.
     */
    public void applyExtraHeart(Player player) {
        UUID uuid = player.getUniqueId();
        extraHeartHolders.add(uuid);

        Messages.debug("[SuddenDeathManager] Granted permanent extra heart to: " + player.getName());

        var ccp = session.getCashClashPlayer(uuid);
        if (ccp != null) {
            ccp.addHealthModifier(2.0);
            Messages.debug("[SuddenDeathManager] Added +2 health to " + player.getName() + " via health modifier system");
        }
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
        extraHeartHolders.remove(playerUuid);
    }

    /**
     * Reapply extra heart effect when player spawns (if still in sudden death and has one)
     */
    public void onPlayerSpawn(Player player) {
        UUID playerUuid = player.getUniqueId();

        if (inSuddenDeath && extraHeartHolders.contains(playerUuid)) {
            // Reapply only current health state; do not stack another modifier on each respawn
            var ccp = session.getCashClashPlayer(playerUuid);
            if (ccp != null) {
                ccp.applyHealth();
            }
            Messages.debug("[SuddenDeathManager] Reapplied extra heart to respawned player: " + player.getName());
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
        List<UUID> playersWithHearts = new ArrayList<>(extraHeartHolders);
        for (UUID uuid : playersWithHearts) {
            removeExtraHeart(uuid);
        }
        extraHeartHolders.clear();

        Messages.debug("[SuddenDeathManager] Reset for new round");
    }

    /**
     * Cleanup when game ends
     */
    public void cleanup() {
        cancelTask(cycleTask);
        cycleActive = false;

        // Remove extra heart effects from all players - create a list to avoid ConcurrentModificationException
        List<UUID> playersWithHearts = new ArrayList<>(extraHeartHolders);
        for (UUID uuid : playersWithHearts) {
            removeExtraHeart(uuid);
        }
        extraHeartHolders.clear();

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

    /**
     * Check if player has a (permanent) extra heart
     */
    public boolean hasExtraHeart(UUID playerUuid) {
        return extraHeartHolders.contains(playerUuid);
    }

    public void restartCycle() {
        cycleNumber++;
        startCycle(repeatCycleDurationMs);

        broadcastCycleRestartMessage();

        if (gamemode != null) {
            gamemode.onSuddenDeathCycleRestart();
        }

        Messages.debug("[SuddenDeathManager] Sudden death tied - restarting cycle " + cycleNumber + " for " + repeatCycleDurationMs + "ms");
    }

    private void broadcastCycleRestartMessage() {
        if (gamemode == null) {
            return;
        }
        Messages.broadcast(session.getPlayers(), gamemode.getSuddenDeathTiedRestartMessageKey());
        SoundUtils.playTo(session.getPlayers(), Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
    }

}


