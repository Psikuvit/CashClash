package me.psikuvit.cashClash.manager.lobby;

import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.items.PDCSetter;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Manages lobby items and logic for Cash Clash.
 * Handles giving lobby items, clearing them, and checking for them.
 */
public class LobbyManager {

    // Namespace keys for PDC
    public static final NamespacedKey LOBBY_ITEM_KEY = new NamespacedKey("cashclash", "lobby_item");

    // Lobby item types
    public enum LobbyItemType {
        STATS("stats"),
        ARENA_SELECTOR("arena_selector"),
        LAYOUT_CONFIGURATOR("layout_configurator");

        private final String id;

        LobbyItemType(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public static LobbyItemType fromId(String id) {
            for (LobbyItemType type : values()) {
                if (type.getId().equals(id)) {
                    return type;
                }
            }
            return null;
        }
    }

    private final ItemsConfig itemsConfig;

    public LobbyManager(ItemsConfig itemsConfig) {
        this.itemsConfig = itemsConfig;
    }

    /**
     * Give all lobby items to a player.
     *
     * @param player The player to give items to
     */
    public void giveLobbyItems(Player player) {
        clearLobbyItems(player);

        ItemsConfig config = itemsConfig;

        // Stats item (slot from config, default 0)
        setLobbyItemSafely(player, config.getLobbyStatsSlot(), createStatsItem(), "stats");

        // Arena selector item (slot from config, default 4)
        setLobbyItemSafely(player, config.getLobbyArenaSelectorSlot(), createArenaSelectorItem(), "arena-selector");

        // Layout configurator item (slot from config, default 8)
        setLobbyItemSafely(player, config.getLobbyLayoutConfiguratorSlot(), createLayoutConfiguratorItem(), "layout-configurator");
    }

    /**
     * Places a lobby item at its configured slot, guarding against an out-of-range value (e.g.
     * a misconfigured items.yml) throwing ArrayIndexOutOfBoundsException and aborting the whole
     * PlayerJoinEvent handler chain - previously a single bad slot here could break join setup
     * entirely instead of just that one item.
     */
    private void setLobbyItemSafely(Player player, int slot, ItemStack item, String itemName) {
        int size = player.getInventory().getSize();
        if (slot < 0 || slot >= size) {
            Messages.debug("SYSTEM", "Lobby item '" + itemName + "' has an invalid configured slot ("
                    + slot + "), skipping - valid range is 0-" + (size - 1));
            return;
        }
        player.getInventory().setItem(slot, item);
    }

    /**
     * Clear all lobby items from a player's inventory.
     *
     * @param player The player to clear items from
     */
    public void clearLobbyItems(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isLobbyItem(item)) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    /**
     * Check if an item is a lobby item.
     *
     * @param item The item to check
     * @return true if it's a lobby item
     */
    public boolean isLobbyItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(LOBBY_ITEM_KEY, PersistentDataType.STRING);
    }

    /**
     * Get the lobby item type from an item.
     *
     * @param item The item to check
     * @return The lobby item type, or null if not a lobby item
     */
    public LobbyItemType getLobbyItemType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String typeId = item.getItemMeta().getPersistentDataContainer().get(LOBBY_ITEM_KEY, PersistentDataType.STRING);
        return typeId != null ? LobbyItemType.fromId(typeId) : null;
    }

    /**
     * Create the stats item.
     */
    private ItemStack createStatsItem() {
        ItemsConfig config = itemsConfig;

        Material material = Material.matchMaterial(config.getLobbyStatsMaterial());
        if (material == null) material = Material.PAPER;

        ItemStack item = new ItemStack(material);
        PDCSetter tags = PDCSetter.of(item);

        tags.meta().displayName(Messages.parse(config.getLobbyStatsName()));
        tags.meta().lore(config.getLobbyStatsLore().stream()
                .map(Messages::parse)
                .toList());

        tags.set(LOBBY_ITEM_KEY, PersistentDataType.STRING, LobbyItemType.STATS.getId());

        tags.apply();
        return item;
    }

    /**
     * Create the arena selector item.
     */
    private ItemStack createArenaSelectorItem() {
        ItemsConfig config = itemsConfig;

        Material material = Material.matchMaterial(config.getLobbyArenaSelectorMaterial());
        if (material == null) material = Material.COMPASS;

        ItemStack item = new ItemStack(material);
        PDCSetter tags = PDCSetter.of(item);

        tags.meta().displayName(Messages.parse(config.getLobbyArenaSelectorName()));
        tags.meta().lore(config.getLobbyArenaSelectorLore().stream()
                .map(Messages::parse)
                .toList());

        tags.set(LOBBY_ITEM_KEY, PersistentDataType.STRING, LobbyItemType.ARENA_SELECTOR.getId());

        tags.apply();
        return item;
    }

    /**
     * Create the layout configurator item.
     */
    private ItemStack createLayoutConfiguratorItem() {
        ItemsConfig config = itemsConfig;

        Material material = Material.matchMaterial(config.getLobbyLayoutConfiguratorMaterial());
        if (material == null) material = Material.ANVIL;

        ItemStack item = new ItemStack(material);
        PDCSetter tags = PDCSetter.of(item);

        tags.meta().displayName(Messages.parse(config.getLobbyLayoutConfiguratorName()));
        tags.meta().lore(config.getLobbyLayoutConfiguratorLore().stream()
                .map(Messages::parse)
                .toList());

        tags.set(LOBBY_ITEM_KEY, PersistentDataType.STRING, LobbyItemType.LAYOUT_CONFIGURATOR.getId());

        tags.apply();
        return item;
    }
}

