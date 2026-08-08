package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.CashClashPlugin;

import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.game.RejoinData;
import me.psikuvit.cashClash.manager.game.RejoinManager;
import me.psikuvit.cashClash.manager.lobby.LayoutManager;
import me.psikuvit.cashClash.manager.lobby.LobbyManager;
import me.psikuvit.cashClash.manager.player.PlayerDataManager;
import me.psikuvit.cashClash.manager.player.ScoreboardManager;
import me.psikuvit.cashClash.manager.player.TabListManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player connection events
 */
public class PlayerConnectionListener implements Listener {

    private final CashClashPlugin plugin;

    public PlayerConnectionListener(CashClashPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Load player data first
        plugin.getPlayerDataManager().getOrLoadData(player.getUniqueId());
        plugin.getPlayerDataManager().markJoined(player.getUniqueId(), System.currentTimeMillis());
        Messages.debug(player, "SYSTEM", "Player joined and data loaded");

        // Check for pending rejoin
        if (plugin.getRejoinManager().hasPendingRejoin(player.getUniqueId())) {
            RejoinData rejoinData = plugin.getRejoinManager().getRejoinData(player.getUniqueId());
            int timeRemaining = rejoinData.getSecondsRemaining(plugin.getConfigManager().getRejoinTimeoutSeconds());

            Messages.send(player, "lobby-messages.rejoin-available");
            Messages.send(player, "lobby-messages.rejoin-time-remaining",
                    "time_remaining", String.valueOf(timeRemaining));

            // Process the rejoin
            boolean rejoined = plugin.getRejoinManager().processRejoin(player);
            if (rejoined) {
                Messages.debug(player, "REJOIN", "Successfully rejoined game");
                return; // Don't set up lobby state if they rejoined a game
            } else {
                Messages.send(player, "lobby-messages.rejoin-failed");
            }
        }

        // Standard join - set up lobby state
        setupLobbyState(player);
    }

    /**
     * Set up the player for the lobby state.
     */
    private void setupLobbyState(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
        CashClashPlayer.resetToDefaultHealth(player);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);

        CashClashPlayer.clearAllEffects(player);

        // Teleport to configured server lobby spawn if present
        var lobbyLoc = plugin.getArenaManager().getServerLobbySpawn();
        if (lobbyLoc != null) {
            player.teleport(lobbyLoc);
            Messages.debug(player, "SYSTEM", "Teleported to lobby spawn");
        }

        // Give lobby items
        plugin.getLobbyManager().giveLobbyItems(player);

        // Set lobby scoreboard
        plugin.getScoreboardManager().setScoreboard(player);

        // Set lobby tab appearance
        plugin.getTabListManager().setPlayerToLobby(player);

        Messages.send(player, "lobby-messages.welcome-title");
        Messages.send(player, "lobby-messages.welcome-arenas");
        Messages.send(player, "lobby-messages.welcome-help");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Accumulate playtime and persist player data
        plugin.getPlayerDataManager().markLeft(player.getUniqueId(), System.currentTimeMillis());

        // Clean up layout editing state
        plugin.getLayoutManager().handleDisconnect(player.getUniqueId());

        // Cleanup mythic state
        plugin.getMythicItemManager().cleanup(player);

        // Remove lobby scoreboard
        plugin.getScoreboardManager().setScoreboard(player);

        // Reset tab list
        plugin.getTabListManager().resetPlayer(player);

        // Check if player is in a game session
        var session = plugin.getGameManager().getPlayerSession(player);
        if (session != null) {
            Messages.debug(player, "GAME", "Player quit while in session " + session.getSessionId());

            // Try to save rejoin data
            boolean rejoinSaved = plugin.getRejoinManager().saveRejoinData(player, session);

            if (rejoinSaved) {
                // Mark player as disconnected but don't remove them yet
                session.markPlayerDisconnected(player);
                // Don't remove from GameManager - let RejoinManager handle timeout
                Messages.debug(player, "REJOIN", "Rejoin data saved for player");
            } else {
                // Rejoin not enabled or game ending - remove immediately
                session.removePlayer(player);
                plugin.getGameManager().removePlayerFromSession(player);
                Messages.debug(player, "GAME", "Player removed from session (rejoin not applicable)");
            }
        }
    }
}
