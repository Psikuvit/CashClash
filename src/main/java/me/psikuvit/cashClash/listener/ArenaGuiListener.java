package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.gui.ArenaSelectionGUI;
import me.psikuvit.cashClash.gui.ArenaSelectionHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Listener dedicated to arena selection GUI interactions.
 */
public class ArenaGuiListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof ArenaSelectionHolder)) return;

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
            int arenaNumber = slot - 9;
            ArenaSelectionGUI.handleArenaClick(player, arenaNumber);
        }
    }
}

