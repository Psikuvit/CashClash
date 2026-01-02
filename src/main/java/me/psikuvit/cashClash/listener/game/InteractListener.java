package me.psikuvit.cashClash.listener.game;

import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Material;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Handles basic game interactions like fire charges.
 * Uses HIGH priority to run after protection listeners.
 */
public class InteractListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.useItemInHand() == Event.Result.DENY) return;

        Player player = e.getPlayer();
        if (e.getItem() == null || e.getItem().getType() != Material.FIRE_CHARGE) return;
        if (PDCDetection.getAnyShopTag(e.getItem()) == null) {
            Fireball fireball = player.launchProjectile(Fireball.class);
            fireball.setIsIncendiary(true);
            fireball.setYield(0f);

            e.getItem().setAmount(e.getItem().getAmount() - 1);
            e.setCancelled(true);
        }
    }
}
