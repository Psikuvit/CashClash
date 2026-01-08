package me.psikuvit.cashClash.listener.lobby;

import me.psikuvit.cashClash.gui.ArenaSelectionGUI;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.util.Keys;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Listener for handling arena NPC (mannequin) interactions.
 * When a player right-clicks an arena NPC, it opens the arena selection GUI.
 */
public class ArenaNPCListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Mannequin mannequin)) return;

        // Check if this is an arena NPC
        if (!mannequin.getPersistentDataContainer().has(Keys.ARENA_NPC_KEY, PersistentDataType.BYTE)) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();

        // Don't open GUI if player is already in a game
        if (GameManager.getInstance().getPlayerSession(player) != null) {
            return;
        }

        // Open the arena selection GUI
        ArenaSelectionGUI.openArenaGUI(player);
    }
}

