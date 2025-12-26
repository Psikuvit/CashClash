package me.psikuvit.cashClash.listener.game;

import me.psikuvit.cashClash.manager.shop.ShopManager;
import me.psikuvit.cashClash.util.Keys;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.persistence.PersistentDataType;

public class ShopListener implements Listener {

    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        Entity e = event.getRightClicked();
        if (e.getType() != EntityType.VILLAGER) return;

        if (!e.getPersistentDataContainer().has(Keys.SHOP_NPC_KEY, PersistentDataType.BYTE)) return;

        // Cancel default interaction and open shop GUI
        event.setCancelled(true);
        Player p = event.getPlayer();
        ShopManager.getInstance().onPlayerInteractShop(p, e);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        Entity e = event.getEntity();
        if (!e.getPersistentDataContainer().has(Keys.SHOP_NPC_KEY, PersistentDataType.BYTE)) return;

        // Prevent damage to shop NPCs
        event.setCancelled(true);
    }
}
