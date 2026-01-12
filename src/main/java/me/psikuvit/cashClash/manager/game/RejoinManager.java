package me.psikuvit.cashClash.manager.game;

import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.GameState;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.player.PurchaseRecord;
import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

/**
 * Manages player rejoin data for disconnected players.
 * Allows players to rejoin their game session if they reconnect within the timeout period.
 */
public class RejoinManager {

    private static RejoinManager instance;

    private final Map<UUID, RejoinData> pendingRejoins;
    private BukkitTask cleanupTask;

    private RejoinManager() {
        this.pendingRejoins = new HashMap<>();
        startCleanupTask();
    }

    public static RejoinManager getInstance() {
        if (instance == null) {
            instance = new RejoinManager();
        }
        return instance;
    }

    /**
     * Start the periodic cleanup task that removes expired rejoin data.
     */
    private void startCleanupTask() {
        // Run every 5 seconds (100 ticks)
        cleanupTask = SchedulerUtils.runTaskTimer(this::cleanupExpiredData, 100L, 100L);
    }

    /**
     * Clean up expired rejoin data and notify sessions.
     */
    private void cleanupExpiredData() {
        if (!ConfigManager.getInstance().isRejoinEnabled()) {
            pendingRejoins.clear();
            return;
        }

        int timeout = ConfigManager.getInstance().getRejoinTimeoutSeconds();

        for (Map.Entry<UUID, RejoinData> entry : pendingRejoins.entrySet()) {
            RejoinData data = entry.getValue();

            if (data.isExpired(timeout)) {
                // Notify the session that the player didn't rejoin in time
                GameSession session = GameManager.getInstance().getActiveSessions().stream()
                        .filter(s -> s.getSessionId().equals(data.sessionId()))
                        .findFirst()
                        .orElse(null);

                if (session != null) {
                    session.handleRejoinTimeout(data.playerUuid());
                    Messages.broadcast(session.getPlayers(),
                            "<red>" + getPlayerName(data.playerUuid()) + " did not rejoin in time and has been removed.</red>");
                }

                Messages.debug("REJOIN", "Removed expired rejoin data for " + data.playerUuid());
                pendingRejoins.remove(entry.getKey());
            }
        }
    }

    /**
     * Save rejoin data for a disconnected player.
     *
     * @param player  The player who disconnected
     * @param session The game session they were in
     * @return true if data was saved successfully
     */
    public boolean saveRejoinData(Player player, GameSession session) {
        if (!ConfigManager.getInstance().isRejoinEnabled()) {
            return false;
        }

        // Don't save rejoin data for games that are ending or waiting
        GameState state = session.getState();
        if (state == GameState.ENDING || state == GameState.WAITING) {
            return false;
        }

        CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) {
            return false;
        }

        // Determine team number
        Team playerTeam = session.getPlayerTeam(player);
        int teamNumber = playerTeam == session.getTeam1() ? 1 : 2;

        // Clone inventory contents
        ItemStack[] inventoryContents = cloneItemArray(player.getInventory().getContents());
        ItemStack[] armorContents = cloneItemArray(player.getInventory().getArmorContents());
        ItemStack offhandItem = player.getInventory().getItemInOffHand().clone();

        // Clone purchase history
        Queue<PurchaseRecord> purchaseHistory = new ArrayDeque<>(ccp.getPurchaseHistory());

        // Copy owned enchants
        Map<EnchantEntry, Integer> ownedEnchants = new HashMap<>(ccp.getOwnedEnchants());

        RejoinData data = new RejoinData(
                player.getUniqueId(),
                session.getSessionId(),
                teamNumber,
                ccp.getCurrentKit(),
                ccp.getCoins(),
                ccp.getLives(),
                ccp.getKillStreak(),
                ccp.getTotalKills(),
                inventoryContents,
                armorContents,
                offhandItem,
                purchaseHistory,
                ownedEnchants,
                System.currentTimeMillis()
        );

        pendingRejoins.put(player.getUniqueId(), data);
        Messages.debug("REJOIN", "Saved rejoin data for " + player.getName() + " in session " + session.getSessionId());

        // Notify team
        int timeout = ConfigManager.getInstance().getRejoinTimeoutSeconds();
        Messages.broadcast(session.getPlayers(),
                "<yellow>" + player.getName() + " disconnected! They have <gold>" + timeout + "s</gold> to rejoin.</yellow>");

        return true;
    }

    /**
     * Check if a player has pending rejoin data.
     *
     * @param playerUuid The player's UUID
     * @return true if they have pending rejoin data
     */
    public boolean hasPendingRejoin(UUID playerUuid) {
        if (!ConfigManager.getInstance().isRejoinEnabled()) {
            return false;
        }

        RejoinData data = pendingRejoins.get(playerUuid);
        if (data == null) {
            return false;
        }

        // Check if expired
        int timeout = ConfigManager.getInstance().getRejoinTimeoutSeconds();
        if (data.isExpired(timeout)) {
            pendingRejoins.remove(playerUuid);
            return false;
        }

        // Check if session still exists and is active
        GameSession session = findSessionById(data.sessionId());
        if (session == null || session.getState() == GameState.ENDING || session.getState() == GameState.WAITING) {
            pendingRejoins.remove(playerUuid);
            return false;
        }

        return true;
    }

    /**
     * Get pending rejoin data for a player.
     *
     * @param playerUuid The player's UUID
     * @return The rejoin data, or null if none exists
     */
    public RejoinData getRejoinData(UUID playerUuid) {
        return pendingRejoins.get(playerUuid);
    }

    /**
     * Process a player rejoining their game.
     *
     * @param player The player who is rejoining
     * @return true if rejoin was successful
     */
    public boolean processRejoin(Player player) {
        if (!ConfigManager.getInstance().isRejoinEnabled()) {
            return false;
        }

        RejoinData data = pendingRejoins.get(player.getUniqueId());
        if (data == null) {
            return false;
        }

        int timeout = ConfigManager.getInstance().getRejoinTimeoutSeconds();
        if (data.isExpired(timeout)) {
            pendingRejoins.remove(player.getUniqueId());
            return false;
        }

        // Find the session
        GameSession session = findSessionById(data.sessionId());
        if (session == null) {
            Messages.send(player, "<red>Your game session has ended.</red>");
            pendingRejoins.remove(player.getUniqueId());
            return false;
        }

        // Check if session is still active
        if (session.getState() == GameState.ENDING || session.getState() == GameState.WAITING) {
            Messages.send(player, "<red>Your game has ended.</red>");
            pendingRejoins.remove(player.getUniqueId());
            return false;
        }

        // Restore the player to the session
        boolean restored = session.rejoinPlayer(player, data);
        if (restored) {
            // Add player back to GameManager tracking
            GameManager.getInstance().addPlayerToSession(player, session);

            pendingRejoins.remove(player.getUniqueId());
            Messages.debug("REJOIN", "Successfully restored " + player.getName() + " to session " + session.getSessionId());

            // Notify team
            Messages.broadcast(session.getPlayers(),
                    "<green>" + player.getName() + " has reconnected!</green>");
        }

        return restored;
    }

    /**
     * Remove rejoin data for a player (e.g., when they manually leave).
     *
     * @param playerUuid The player's UUID
     */
    public void removeRejoinData(UUID playerUuid) {
        pendingRejoins.remove(playerUuid);
    }

    /**
     * Clear all pending rejoins for a specific session (e.g., when session ends).
     *
     * @param sessionId The session ID
     */
    public void clearSessionRejoins(UUID sessionId) {
        pendingRejoins.entrySet().removeIf(entry -> entry.getValue().sessionId().equals(sessionId));
    }

    /**
     * Shutdown the rejoin manager.
     */
    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        pendingRejoins.clear();
    }

    // ==================== HELPER METHODS ====================

    private GameSession findSessionById(UUID sessionId) {
        return GameManager.getInstance().getActiveSessions().stream()
                .filter(s -> s.getSessionId().equals(sessionId))
                .findFirst()
                .orElse(null);
    }

    private ItemStack[] cloneItemArray(ItemStack[] items) {
        if (items == null) return new ItemStack[0];
        ItemStack[] cloned = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) {
                cloned[i] = items[i].clone();
            }
        }
        return cloned;
    }

    private String getPlayerName(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            return player.getName();
        }
        // Try to get offline player name
        return Bukkit.getOfflinePlayer(uuid).getName();
    }
}

