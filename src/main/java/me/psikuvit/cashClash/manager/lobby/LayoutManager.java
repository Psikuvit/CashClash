package me.psikuvit.cashClash.manager.lobby;

import me.psikuvit.cashClash.kit.Kit;
import me.psikuvit.cashClash.manager.player.PlayerDataManager;
import me.psikuvit.cashClash.storage.PlayerData;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages kit layout editing for players.
 * Allows players to customize the slot positions of kit items.
 */
public class LayoutManager {

    private static LayoutManager instance;

    // Tracks which kit a player is currently editing
    private final Map<UUID, Kit> editingKit;
    // Stores the original items before editing started
    private final Map<UUID, ItemStack[]> originalInventory;

    private LayoutManager() {
        this.editingKit = new HashMap<>();
        this.originalInventory = new HashMap<>();
    }

    public static LayoutManager getInstance() {
        if (instance == null) {
            instance = new LayoutManager();
        }
        return instance;
    }

    /**
     * Start editing a kit layout for a player.
     * Saves their current inventory and gives them the kit items to arrange.
     *
     * @param player The player editing the layout
     * @param kit    The kit to edit
     */
    public void startEditing(Player player, Kit kit) {
        UUID uuid = player.getUniqueId();

        // Save original inventory
        originalInventory.put(uuid, player.getInventory().getContents().clone());

        // Clear and give kit items
        player.getInventory().clear();

        // Remove lobby items first
        LobbyManager.getInstance().clearLobbyItems(player);

        // Apply kit items (without armor for layout)
        kit.applyForLayout(player);

        // Track editing state
        editingKit.put(uuid, kit);

        Messages.send(player, "<green>Editing layout for <yellow>" + kit.getDisplayName() + "</yellow>.</green>");
        Messages.send(player, "<gray>Arrange items as you like, then use <yellow>/cc layout confirm</yellow> to save.</gray>");
        Messages.send(player, "<gray>Use <yellow>/cc layout cancel</yellow> to cancel without saving.</gray>");
    }

    /**
     * Confirm and save the current layout.
     * Saves a mapping of slot -> item identifier for each item.
     *
     * @param player The player confirming their layout
     * @return true if layout was saved, false if not editing
     */
    public boolean confirmLayout(Player player) {
        UUID uuid = player.getUniqueId();
        Kit kit = editingKit.get(uuid);

        if (kit == null) {
            Messages.send(player, "<red>You are not editing any kit layout!</red>");
            return false;
        }

        // Capture slot -> item identifier mapping
        PlayerInventory inv = player.getInventory();
        Map<Integer, String> slotItemMap = new HashMap<>();

        // Record slots 0-35 (hotbar + main inventory, not armor)
        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && !item.getType().isAir()) {
                String itemId = getItemIdentifier(item);
                slotItemMap.put(i, itemId);
            }
        }

        // Save to player data
        PlayerData data = PlayerDataManager.getInstance().getData(uuid);
        if (data != null) {
            data.setKitLayout(kit.name(), slotItemMap);
        }

        // Restore original inventory
        restoreInventory(player);

        // Clear editing state
        editingKit.remove(uuid);
        originalInventory.remove(uuid);

        Messages.send(player, "<green>Layout for <yellow>" + kit.getDisplayName() + "</yellow> saved!</green>");
        return true;
    }

    /**
     * Get a unique identifier for an item.
     * Uses ITEM_ID from PDC if available, otherwise uses material name.
     */
    private String getItemIdentifier(ItemStack item) {
        if (item == null) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(Keys.ITEM_ID, PersistentDataType.STRING)) {
            // Custom item - use ITEM_ID
            return "CUSTOM:" + meta.getPersistentDataContainer().get(Keys.ITEM_ID, PersistentDataType.STRING);
        }

        // Standard item - use material name
        return "MATERIAL:" + item.getType().name();
    }

    /**
     * Cancel layout editing without saving.
     *
     * @param player The player canceling
     * @return true if was editing, false otherwise
     */
    public boolean cancelEditing(Player player) {
        UUID uuid = player.getUniqueId();

        if (!editingKit.containsKey(uuid)) {
            Messages.send(player, "<red>You are not editing any kit layout!</red>");
            return false;
        }

        Kit kit = editingKit.get(uuid);

        // Restore original inventory
        restoreInventory(player);

        // Clear editing state
        editingKit.remove(uuid);
        originalInventory.remove(uuid);

        Messages.send(player, "<yellow>Layout editing cancelled for " + kit.getDisplayName() + ".</yellow>");
        return true;
    }

    /**
     * Restore the player's original inventory.
     */
    private void restoreInventory(Player player) {
        UUID uuid = player.getUniqueId();
        ItemStack[] original = originalInventory.get(uuid);

        player.getInventory().clear();
        if (original != null) {
            player.getInventory().setContents(original);
        }

        // Give lobby items back
        LobbyManager.getInstance().giveLobbyItems(player);
    }

    /**
     * Check if a player is currently editing a layout.
     */
    public boolean isEditing(Player player) {
        return editingKit.containsKey(player.getUniqueId());
    }

    /**
     * Get the kit a player is currently editing.
     */
    public Kit getEditingKit(Player player) {
        return editingKit.get(player.getUniqueId());
    }

    /**
     * Clean up when a player disconnects.
     */
    public void handleDisconnect(UUID uuid) {
        editingKit.remove(uuid);
        originalInventory.remove(uuid);
    }
}

