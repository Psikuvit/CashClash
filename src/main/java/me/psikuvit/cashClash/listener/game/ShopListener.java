package me.psikuvit.cashClash.listener.game;

import me.psikuvit.cashClash.manager.shop.ShopManager;
import me.psikuvit.cashClash.util.Keys;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles shop NPC interactions and protection.
 * Uses NORMAL priority to allow shop interactions to work correctly.
 */
public class ShopListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.isCancelled()) return;

        Entity e = event.getRightClicked();
        if (e.getType() != EntityType.VILLAGER) return;

        if (!e.getPersistentDataContainer().has(Keys.SHOP_NPC_KEY, PersistentDataType.BYTE)) return;

        // Cancel default interaction and open shop GUI
        event.setCancelled(true);
        Player p = event.getPlayer();
        ShopManager.getInstance().onPlayerInteractShop(p, e);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity e = event.getEntity();
        if (!e.getPersistentDataContainer().has(Keys.SHOP_NPC_KEY, PersistentDataType.BYTE)) return;

        // Prevent damage to shop NPCs
        event.setCancelled(true);
    }
}
