package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.config.ConfigManager;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.game.RejoinData;
import me.psikuvit.cashClash.manager.game.RejoinManager;
import me.psikuvit.cashClash.manager.lobby.LayoutManager;
import me.psikuvit.cashClash.manager.lobby.LobbyManager;
import me.psikuvit.cashClash.manager.items.mythic.MythicItemManager;
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

    private final ArenaManager arenaManager;
    private final ConfigManager configManager;
    private final GameManager gameManager;
    private final LayoutManager layoutManager;
    private final LobbyManager lobbyManager;
    private final MythicItemManager mythicItemManager;
    private final PlayerDataManager playerDataManager;
    private final RejoinManager rejoinManager;
    private final ScoreboardManager scoreboardManager;
    private final TabListManager tabListManager;

    public PlayerConnectionListener(ArenaManager arenaManager, ConfigManager configManager, GameManager gameManager,
                                   LayoutManager layoutManager, LobbyManager lobbyManager, MythicItemManager mythicItemManager,
                                   PlayerDataManager playerDataManager, RejoinManager rejoinManager,
                                   ScoreboardManager scoreboardManager, TabListManager tabListManager) {
        this.arenaManager = arenaManager;
        this.configManager = configManager;
        this.gameManager = gameManager;
        this.layoutManager = layoutManager;
        this.lobbyManager = lobbyManager;
        this.mythicItemManager = mythicItemManager;
        this.playerDataManager = playerDataManager;
        this.rejoinManager = rejoinManager;
        this.scoreboardManager = scoreboardManager;
        this.tabListManager = tabListManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Load player data first
        playerDataManager.getOrLoadData(player.getUniqueId());
        playerDataManager.markJoined(player.getUniqueId(), System.currentTimeMillis());
        Messages.debug(player, "SYSTEM", "Player joined and data loaded");

        // Check for pending rejoin
        if (rejoinManager.hasPendingRejoin(player.getUniqueId())) {
            RejoinData rejoinData = rejoinManager.getRejoinData(player.getUniqueId());
            int timeRemaining = rejoinData.getSecondsRemaining(configManager.getRejoinTimeoutSeconds());

            Messages.send(player, "lobby-messages.rejoin-available");
            Messages.send(player, "lobby-messages.rejoin-time-remaining",
                    "time_remaining", String.valueOf(timeRemaining));

            // Process the rejoin
            boolean rejoined = rejoinManager.processRejoin(player);
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
        var lobbyLoc = arenaManager.getServerLobbySpawn();
        if (lobbyLoc != null) {
            player.teleport(lobbyLoc);
            Messages.debug(player, "SYSTEM", "Teleported to lobby spawn");
        }

        // Give lobby items
        lobbyManager.giveLobbyItems(player);

        // Set lobby scoreboard
        scoreboardManager.setScoreboard(player);

        // Set lobby tab appearance
        tabListManager.setPlayerToLobby(player);

        Messages.send(player, "lobby-messages.welcome-title");
        Messages.send(player, "lobby-messages.welcome-arenas");
        Messages.send(player, "lobby-messages.welcome-help");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Accumulate playtime and persist player data
        playerDataManager.markLeft(player.getUniqueId(), System.currentTimeMillis());

        // Clean up layout editing state
        layoutManager.handleDisconnect(player.getUniqueId());

        // Cleanup mythic state
        mythicItemManager.cleanup(player);

        // Remove lobby scoreboard
        scoreboardManager.setScoreboard(player);

        // Reset tab list
        tabListManager.resetPlayer(player);

        // Check if player is in a game session
        var session = gameManager.getPlayerSession(player);
        if (session != null) {
            Messages.debug(player, "GAME", "Player quit while in session " + session.getSessionId());

            // Try to save rejoin data
            boolean rejoinSaved = rejoinManager.saveRejoinData(player, session);

            if (rejoinSaved) {
                // Mark player as disconnected but don't remove them yet
                session.markPlayerDisconnected(player);
                // Don't remove from GameManager - let RejoinManager handle timeout
                Messages.debug(player, "REJOIN", "Rejoin data saved for player");
            } else {
                // Rejoin not enabled or game ending - remove immediately
                session.removePlayer(player);
                gameManager.removePlayerFromSession(player);
                Messages.debug(player, "GAME", "Player removed from session (rejoin not applicable)");
            }
        }
    }
}
