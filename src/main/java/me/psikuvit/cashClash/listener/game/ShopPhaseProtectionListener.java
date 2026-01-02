package me.psikuvit.cashClash.listener.game;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.game.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Protects players during the shopping phase.
 * Uses NORMAL priority to run after lobby protection but before item-specific listeners.
 */
public class ShopPhaseProtectionListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.isCancelled()) return;

        if (!(event.getEntity() instanceof Player player)) return;

        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session != null && session.getState().isShopping()) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session != null && session.getState().isShopping()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoinOrRespawn(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session != null && session.getState().isShopping()) {
            player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getBaseValue());
            player.setFoodLevel(20);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        if (session != null && session.getState().isShopping()) {
            Bukkit.getScheduler().runTaskLater(CashClashPlugin.getInstance(), () -> {
                player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getBaseValue());
                player.setFoodLevel(20);
            }, 2L);
        }
    }
}
