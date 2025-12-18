package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.arena.ArenaManager;
import me.psikuvit.cashClash.manager.PlayerDataManager;
import me.psikuvit.cashClash.manager.ScoreboardManager;
import me.psikuvit.cashClash.manager.TabListManager;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player connection events
 */
public class PlayerConnectionListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);

        player.getActivePotionEffects().forEach(effect ->
            player.removePotionEffect(effect.getType())
        );

        PlayerDataManager.getInstance().getOrLoadData(player.getUniqueId());

        // Teleport to configured server lobby spawn if present
        var lobbyLoc = ArenaManager.getInstance().getServerLobbySpawn();
        if (lobbyLoc != null) {
            player.teleport(lobbyLoc);
        }

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

        // Remove lobby scoreboard
        ScoreboardManager.getInstance().removeLobbyScoreboard(player);

        // Reset tab list
        TabListManager.getInstance().resetPlayer(player);

        // Remove from game if in one
        var session = GameManager.getInstance().getPlayerSession(player);
        if (session != null) {
            session.removePlayer(player);
            GameManager.getInstance().removePlayerFromSession(player);
        }
    }
}
