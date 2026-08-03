package me.psikuvit.cashClash.util.items;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Fluent writer for PersistentDataContainer tags on an ItemStack. Queues tags and applies them
 * in a single getItemMeta/setItemMeta round-trip:
 *
 * <pre>{@code
 * PDCSetter.of(item).set(Keys.RUNE_LEVEL, PersistentDataType.INTEGER, 3).apply();
 * }</pre>
 *
 * Reads are centralized in {@link PDCDetection}; use this for writes.
 */
public final class PDCSetter {

    private final ItemStack item;
    private final ItemMeta meta;

    private PDCSetter(ItemStack item) {
        this.item = item;
        this.meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalArgumentException("item has no meta (air?)");
        }
    }

    /**
     * @param item the item to tag; must not be air and must have meta
     */
    public static PDCSetter of(ItemStack item) {
        if (item == null) {
            throw new IllegalArgumentException("item cannot be null");
        }
        return new PDCSetter(item);
    }

    public <T, Z> PDCSetter set(NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
        meta.getPersistentDataContainer().set(key, type, value);
        return this;
    }

    public PDCSetter remove(NamespacedKey key) {
        meta.getPersistentDataContainer().remove(key);
        return this;
    }

    /**
     * Writes all queued tags back to the item. Call once after the last {@link #set}.
     */
    public void apply() {
        item.setItemMeta(meta);
    }
}
