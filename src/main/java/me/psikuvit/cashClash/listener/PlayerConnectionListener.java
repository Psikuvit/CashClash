package me.psikuvit.cashClash.listener;

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
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;

/**
 * Handles player connection events
 */
public class PlayerConnectionListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Load player data first
        PlayerDataManager.getInstance().getOrLoadData(player.getUniqueId());
        Messages.debug(player, "SYSTEM", "Player joined and data loaded");

        // Check for pending rejoin
        if (RejoinManager.getInstance().hasPendingRejoin(player.getUniqueId())) {
            RejoinData rejoinData = RejoinManager.getInstance().getRejoinData(player.getUniqueId());
            int timeRemaining = rejoinData.getSecondsRemaining(ConfigManager.getInstance().getRejoinTimeoutSeconds());

            Messages.send(player, "<yellow>You have a game in progress! Rejoining...</yellow>");
            Messages.send(player, "<gray>Time remaining to rejoin: <gold>" + timeRemaining + "s</gold></gray>");

            // Process the rejoin
            boolean rejoined = RejoinManager.getInstance().processRejoin(player);
            if (rejoined) {
                Messages.debug(player, "REJOIN", "Successfully rejoined game");
                return; // Don't set up lobby state if they rejoined a game
            } else {
                Messages.send(player, "<red>Failed to rejoin game. Starting fresh.</red>");
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
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);

        player.getActivePotionEffects().stream()
                .map(PotionEffect::getType)
                .forEach(player::removePotionEffect);

        // Teleport to configured server lobby spawn if present
        var lobbyLoc = ArenaManager.getInstance().getServerLobbySpawn();
        if (lobbyLoc != null) {
            player.teleport(lobbyLoc);
            Messages.debug(player, "SYSTEM", "Teleported to lobby spawn");
        }

        // Give lobby items
        LobbyManager.getInstance().giveLobbyItems(player);

        // Set lobby scoreboard
        ScoreboardManager.getInstance().setLobbyScoreboard(player);

        // Set lobby tab appearance
        TabListManager.getInstance().setPlayerToLobby(player);

        Messages.send(player, "<gold><bold>=== Welcome to Cash Clash ===</bold></gold>");
        Messages.send(player, "<yellow>Type <green>/cc arenas</green> <yellow>to browse and join games!</yellow>");
        Messages.send(player, "<gray>Use <yellow>/cc help <gray>for more commands.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Clean up layout editing state
        LayoutManager.getInstance().handleDisconnect(player.getUniqueId());

        // Remove lobby scoreboard
        ScoreboardManager.getInstance().removeLobbyScoreboard(player);

        // Reset tab list
        TabListManager.getInstance().resetPlayer(player);

        // Check if player is in a game session
        var session = GameManager.getInstance().getPlayerSession(player);
        if (session != null) {
            Messages.debug(player, "GAME", "Player quit while in session " + session.getSessionId());

            // Try to save rejoin data
            boolean rejoinSaved = RejoinManager.getInstance().saveRejoinData(player, session);

            if (rejoinSaved) {
                // Mark player as disconnected but don't remove them yet
                session.markPlayerDisconnected(player);
                // Don't remove from GameManager - let RejoinManager handle timeout
                Messages.debug(player, "REJOIN", "Rejoin data saved for player");
            } else {
                // Rejoin not enabled or game ending - remove immediately
                session.removePlayer(player);
                GameManager.getInstance().removePlayerFromSession(player);
                Messages.debug(player, "GAME", "Player removed from session (rejoin not applicable)");
            }
        }
    }
}
