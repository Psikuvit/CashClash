package me.psikuvit.cashClash.listener.lobby;

import me.psikuvit.cashClash.manager.game.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

/**
 * Prevents players from doing certain actions while in the lobby (not in a game).
 * Uses LOW priority so game-specific listeners can override if needed.
 */
public class LobbyProtectionListener implements Listener {

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        // Skip if player is in a game session (let game listeners handle it)
        if (GameManager.getInstance().getPlayerSession(player) != null) return;

        // Cancel for non-admins in lobby
        if (!player.hasPermission("cashclash.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        // Skip if player is in a game session (let game listeners handle it)
        if (GameManager.getInstance().getPlayerSession(player) != null) return;

        // Cancel for non-admins in lobby
        if (!player.hasPermission("cashclash.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;

        if (!(event.getDamager() instanceof Player damager)) {
            return;
        }

        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        var damagerSession = GameManager.getInstance().getPlayerSession(damager);
        var victimSession = GameManager.getInstance().getPlayerSession(victim);

        // Allow if both players are in the same game session
        if (damagerSession != null && damagerSession.equals(victimSession)) return;

        // Cancel PvP outside of games
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.isCancelled()) return;

        if (!(event.getEntity() instanceof Player player)) return;

        // Skip if player is in a game session (let game listeners handle it)
        if (GameManager.getInstance().getPlayerSession(player) != null) return;

        // Cancel hunger in lobby
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        if (GameManager.getInstance().getPlayerSession(player) != null) return;

        event.setCancelled(true);
    }
}

