package me.psikuvit.cashClash.listener.gui;

import me.psikuvit.cashClash.gui.ArenaSelectionGUI;
import me.psikuvit.cashClash.gui.ArenaSelectionHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Listener dedicated to arena selection GUI interactions.
 * Uses NORMAL priority for GUI interactions.
 */
public class ArenaGuiListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof ArenaSelectionHolder)) return;

        // Only cancel clicks in the top inventory (arena GUI), not player's inventory
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        event.setCancelled(true);
        handleArenaSelection(event, player);
    }

    private void handleArenaSelection(InventoryClickEvent event, Player player) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        int slot = event.getSlot();

        if (slot == 22) {
            player.closeInventory();
            return;
        }

        if (slot >= 10 && slot <= 14) {
            int arenaNumber = slot - 10;
            ArenaSelectionGUI.handleArenaClick(player, arenaNumber);
        }
    }
}

