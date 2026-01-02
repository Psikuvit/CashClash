package me.psikuvit.cashClash.listener.game;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.game.Team;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Prevents ender pearl usage during respawn protection or when pearls are disabled by TeamDislodgeManager.
 * Uses HIGH priority to run after protection listeners.
 */
public class EnderPearlListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.isCancelled()) return;

        if (event.getEntity() instanceof EnderPearl pearl) {
            if (pearl.getShooter() instanceof Player player) {
                GameSession session = GameManager.getInstance().getPlayerSession(player);
                if (session == null) return;

                CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
                if (ccp != null && ccp.isRespawnProtected()) {
                    event.setCancelled(true);
                    Messages.send(player, "<red>You cannot use ender pearls right after spawning.</red>");
                    return;
                }

                Team team = session.getPlayerTeam(player);
                if (team != null && team.isEnderPearlsDisabled()) {
                    event.setCancelled(true);
                    Messages.send(player, "<red>Your team's Ender Pearls are currently disabled.</red>");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.useItemInHand() == org.bukkit.event.Event.Result.DENY) return;

        // Block right-click usage of ender pearls or custom bounce pad items
        if (event.getHand() == EquipmentSlot.OFF_HAND) return; // ignore off-hand duplicate
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        if (item.getType() == Material.ENDER_PEARL) {
            GameSession session = GameManager.getInstance().getPlayerSession(player);
            if (session == null) return;
            CashClashPlayer ccp = session.getCashClashPlayer(player.getUniqueId());
            if (ccp != null && ccp.isRespawnProtected()) {
                event.setCancelled(true);
                Messages.send(player, "<red>You cannot use ender pearls right after spawning.</red>");
                return;
            }
            Team team = session.getPlayerTeam(player);
            if (team != null && team.isEnderPearlsDisabled()) {
                event.setCancelled(true);
                Messages.send(player, "<red>Your team's Ender Pearls are currently disabled.</red>");
            }
        }
    }
}

