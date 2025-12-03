package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.manager.GameManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

/**
 * Prevents players from doing certain actions while in the lobby (not in a game)
 */
public class LobbyProtectionListener implements Listener {

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (GameManager.getInstance().getPlayerSession(player) != null) return;
        if (!player.hasPermission("cashclash.admin")) event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        if (GameManager.getInstance().getPlayerSession(player) != null) return;
        if (!player.hasPermission("cashclash.admin")) event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) {
            return;
        }

        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        // Allow if both players are in the same game
        var damagerSession = GameManager.getInstance().getPlayerSession(damager);
        var victimSession = GameManager.getInstance().getPlayerSession(victim);

        if (damagerSession != null && damagerSession.equals(victimSession)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (GameManager.getInstance().getPlayerSession(player) == null) event.setCancelled(true);

    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        if (GameManager.getInstance().getPlayerSession(player) != null) return;
        event.setCancelled(true);
    }
}

