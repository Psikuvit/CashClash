package me.psikuvit.cashClash.listener.lobby;

import me.psikuvit.cashClash.gui.ArenaSelectionGUI;
import me.psikuvit.cashClash.gui.LayoutKitSelectorGUI;
import me.psikuvit.cashClash.gui.StatsGUI;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.manager.lobby.LayoutManager;
import me.psikuvit.cashClash.manager.lobby.LobbyManager;
import me.psikuvit.cashClash.manager.lobby.LobbyManager.LobbyItemType;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Listener for handling lobby item interactions.
 * Handles Stats, Arena Selector, and Layout Configurator items.
 */
public class LobbyListener implements Listener {

    private final LobbyManager lobbyManager = LobbyManager.getInstance();

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null) return;

        // Only handle if player is NOT in a game
        if (GameManager.getInstance().getPlayerSession(player) != null) return;

        LobbyItemType type = lobbyManager.getLobbyItemType(item);
        if (type == null) return;

        // Cancel the event to prevent placing blocks, etc.
        event.setCancelled(true);

        // Only respond to right clicks
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        switch (type) {
            case STATS -> handleStatsItem(player);
            case ARENA_SELECTOR -> handleArenaSelectorItem(player);
            case LAYOUT_CONFIGURATOR -> handleLayoutConfiguratorItem(player);
        }
    }

    /**
     * Handle the stats item - Opens the stats GUI.
     */
    private void handleStatsItem(Player player) {
        StatsGUI.openStatsGUI(player);
    }

    /**
     * Handle the arena selector item - Opens the arena selection GUI.
     */
    private void handleArenaSelectorItem(Player player) {
        ArenaSelectionGUI.openArenaGUI(player);
    }

    /**
     * Handle the layout configurator item - Opens the layout kit selector GUI.
     */
    private void handleLayoutConfiguratorItem(Player player) {
        // Check if already editing a layout
        if (LayoutManager.getInstance().isEditing(player)) {
            Messages.send(player, "<yellow>You are currently editing a layout.</yellow>");
            Messages.send(player, "<gray>Use <yellow>/cc layout confirm</yellow> to save or <yellow>/cc layout cancel</yellow> to cancel.</gray>");
            return;
        }

        LayoutKitSelectorGUI.open(player);
    }

    // ==================== PREVENT LOBBY ITEM MANIPULATION ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Only protect if player is NOT in a game
        if (GameManager.getInstance().getPlayerSession(player) != null) return;

        // Allow clicks when editing a layout
        if (LayoutManager.getInstance().isEditing(player)) return;

        ItemStack clicked = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        // Prevent moving lobby items
        if (lobbyManager.isLobbyItem(clicked) || lobbyManager.isLobbyItem(cursor)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        // Only protect if player is NOT in a game
        if (GameManager.getInstance().getPlayerSession(player) != null) return;

        ItemStack dropped = event.getItemDrop().getItemStack();

        if (lobbyManager.isLobbyItem(dropped)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        // Only protect if player is NOT in a game
        if (GameManager.getInstance().getPlayerSession(player) != null) return;

        ItemStack mainHand = event.getMainHandItem();
        ItemStack offHand = event.getOffHandItem();

        if (lobbyManager.isLobbyItem(mainHand) || lobbyManager.isLobbyItem(offHand)) {
            event.setCancelled(true);
        }
    }
}

