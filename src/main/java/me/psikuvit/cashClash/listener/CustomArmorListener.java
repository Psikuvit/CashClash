package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.manager.CustomArmorManager;
import me.psikuvit.cashClash.manager.GameManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.block.Action;

/**
 * Hooks runtime events to custom armor logic
 */
public class CustomArmorListener implements Listener {

    private final CustomArmorManager manager = CustomArmorManager.getInstance();

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        // only run in game
        if (GameManager.getInstance().getPlayerSession(p) == null) return;
        manager.onPlayerMove(p);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        if (GameManager.getInstance().getPlayerSession(p) == null) return;

        if (manager.tryActivateLightfoot(p)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (GameManager.getInstance().getPlayerSession(p) == null) return;
        // guardian check and deathmauler damage tracker
        manager.onPlayerDamaged(p);
        manager.onPlayerTookDamageForDeathmauler(p);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity dam = event.getDamager();
        Entity vic = event.getEntity();
        if (!(dam instanceof Player attacker)) return;
        if (!(vic instanceof Player target)) return;
        if (GameManager.getInstance().getPlayerSession(attacker) == null) return;

        manager.onPlayerAttack(attacker, target);
        manager.onDragonHit(attacker);
    }
}

