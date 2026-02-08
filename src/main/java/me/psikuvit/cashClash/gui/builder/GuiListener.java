package me.psikuvit.cashClash.gui.builder;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Universal listener for all GUIs built with GuiBuilder.
 * Handles click events by delegating to registered GuiButton handlers.
 */
public class GuiListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof GuiHolder guiHolder)) {
            return;
        }

        // Handle clicks in player inventory
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            if (!guiHolder.isPlayerInventoryClickAllowed()) {
                event.setCancelled(true);
            }
            return;
        }

        // Always cancel clicks in GUI by default
        event.setCancelled(true);

        int slot = event.getSlot();
        GuiButton button = guiHolder.getButton(slot);

        if (button == null) {
            return;
        }

        // Execute button action
        if (!button.shouldCancelClick()) {
            event.setCancelled(false);
        }

        button.executeClick(player, event.getClick());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof GuiHolder) {
            // Cancel drag events in GUI
            boolean anyInTop = event.getRawSlots().stream()
                    .anyMatch(slot -> slot < event.getView().getTopInventory().getSize());
            if (anyInTop) {
                event.setCancelled(true);
            }
        }
    }
}

