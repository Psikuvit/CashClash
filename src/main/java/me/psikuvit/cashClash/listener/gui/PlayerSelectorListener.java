package me.psikuvit.cashClash.listener.gui;

import me.psikuvit.cashClash.gui.PlayerSelector;
import me.psikuvit.cashClash.manager.items.CustomItemManager;
import me.psikuvit.cashClash.util.items.GuiItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Listener for player selector GUI interactions.
 * Uses NORMAL priority for GUI interactions.
 */
public class PlayerSelectorListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof PlayerSelector.PlayerSelectorHolder)) return;

        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();

        if (!GuiItemUtils.isPlayerHead(clicked)) return;
        if (!(clicked.getItemMeta() instanceof SkullMeta meta)) return;
        if (meta.getOwningPlayer() == null) return;

        Player target = Bukkit.getPlayer(meta.getOwningPlayer().getUniqueId());
        if (target == null) return;
        player.closeInventory();
        CustomItemManager.getInstance().handleTabletOfHackingSelection(player, target);
    }
}

