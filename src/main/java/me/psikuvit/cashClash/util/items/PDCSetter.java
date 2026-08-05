package me.psikuvit.cashClash.util.items;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Fluent writer for PersistentDataContainer tags on an ItemStack or an Entity. For items, tags
 * are queued and applied in a single getItemMeta/setItemMeta round-trip:
 *
 * <pre>{@code
 * PDCSetter.of(item).set(Keys.RUNE_LEVEL, PersistentDataType.INTEGER, 3).apply();
 * }</pre>
 *
 * An entity's PersistentDataContainer is already live (no meta round-trip needed), so
 * {@link #apply()} is a no-op for entities - included for API symmetry so the same
 * chain-then-apply call shape works for both:
 *
 * <pre>{@code
 * PDCSetter.of(orb).set(Keys.ITEM_ID, PersistentDataType.STRING, id).apply();
 * }</pre>
 *
 * Reads are centralized in {@link PDCDetection}; use this for writes.
 */
public final class PDCSetter {

    private final ItemStack item;
    private final ItemMeta meta;
    private final PersistentDataContainer pdc;

    private PDCSetter(ItemStack item) {
        this.item = item;
        this.meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalArgumentException("item has no meta (air?)");
        }
        this.pdc = meta.getPersistentDataContainer();
    }

    private PDCSetter(Entity entity) {
        this.item = null;
        this.meta = null;
        this.pdc = entity.getPersistentDataContainer();
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

    /**
     * @param entity the entity to tag (e.g. a tracked projectile)
     */
    public static PDCSetter of(Entity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity cannot be null");
        }
        return new PDCSetter(entity);
    }

    public <T, Z> PDCSetter set(NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
        pdc.set(key, type, value);
        return this;
    }

    public PDCSetter remove(NamespacedKey key) {
        pdc.remove(key);
        return this;
    }

    /**
     * The meta being modified, for non-PDC mutations (name, lore, enchants, flags, damage).
     * All mutations are applied together on {@link #apply}. Item-only - throws for an entity.
     */
    public ItemMeta meta() {
        if (meta == null) {
            throw new IllegalStateException("not wrapping an item");
        }
        return meta;
    }

    /**
     * Writes all queued tags back to the item. Call once after the last {@link #set}.
     * No-op when wrapping an entity, whose PersistentDataContainer is already live.
     */
    public void apply() {
        if (item != null) {
            item.setItemMeta(meta);
        }
    }
}
