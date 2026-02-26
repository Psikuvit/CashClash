package me.psikuvit.cashClash.util.items;

import me.psikuvit.cashClash.shop.items.Purchasable;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for creating shop GUI items.
 * Reduces code duplication and improves readability.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * ItemStack item = ShopItemBuilder.of(Material.DIAMOND_SWORD)
 *     .name("<yellow>Diamond Sword</yellow>")
 *     .price(5000)
 *     .description("A sharp blade")
 *     .purchasePrompt()
 *     .itemId("DIAMOND_SWORD")
 *     .build();
 * }</pre>
 */
public class ShopItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;
    private final List<Component> lore;
    private final List<Component> priceLore;

    private ShopItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, amount);
        this.meta = item.getItemMeta();
        this.lore = new ArrayList<>();
        this.priceLore = new ArrayList<>();
    }

    /**
     * Creates a new builder for the given material.
     *
     * @param material The item material
     * @return A new ShopItemBuilder
     */
    public static ShopItemBuilder of(Material material) {
        return new ShopItemBuilder(material, 1);
    }

    /**
     * Creates a new builder for the given material with a specific amount.
     *
     * @param material The item material
     * @param amount The stack size
     * @return A new ShopItemBuilder
     */
    public static ShopItemBuilder of(Material material, int amount) {
        return new ShopItemBuilder(material, amount);
    }

    /**
     * Creates a new builder from a Purchasable item.
     *
     * @param purchasable The purchasable item definition
     * @return A new ShopItemBuilder pre-configured with the item's properties
     */
    public static ShopItemBuilder from(Purchasable purchasable) {
        return new ShopItemBuilder(purchasable.getMaterial(), purchasable.getInitialAmount())
                .name("<yellow>" + purchasable.getDisplayName() + "</yellow>")
                .price(purchasable.getPrice())
                .itemId(purchasable.name());
    }

    /**
     * Sets the display name using MiniMessage format.
     *
     * @param miniMessage The name in MiniMessage format
     * @return This builder for chaining
     */
    public ShopItemBuilder name(String miniMessage) {
        meta.displayName(Messages.parse(miniMessage));
        return this;
    }

    /**
     * Adds a price line to the lore.
     *
     * @param price The price to display
     * @return This builder for chaining
     */
    public ShopItemBuilder price(long price) {
        priceLore.add(Messages.parse("<gray>Price: <gold>$" + String.format("%,d", price) + "</gold></gray>"));
        return this;
    }

    /**
     * Adds a price detail line that should appear at the bottom of the lore.
     *
     * @param miniMessage The price detail text in MiniMessage format
     * @return This builder for chaining
     */
    public ShopItemBuilder priceDetail(String miniMessage) {
        priceLore.addAll(Messages.wrapLines(miniMessage));
        return this;
    }

    /**
     * Adds a description to the lore (auto-wraps long text).
     *
     * @param description The description text
     * @return This builder for chaining
     */
    public ShopItemBuilder description(String description) {
        if (description != null && !description.isEmpty()) {
            lore.addAll(Messages.wrapLines(description));
        }
        return this;
    }

    /**
     * Adds a "max quantity" line to the lore.
     *
     * @param max The maximum quantity
     * @return This builder for chaining
     */
    public ShopItemBuilder maxQuantity(int max) {
        if (max > 1) {
            lore.add(Messages.parse("<gray>Max: <white>" + max + "</white></gray>"));
        }
        return this;
    }

    /**
     * Adds a "max level" line to the lore.
     *
     * @param maxLevel The maximum enchant level
     * @return This builder for chaining
     */
    public ShopItemBuilder maxLevel(int maxLevel) {
        lore.add(Messages.parse("<gray>Max Level: <white>" + maxLevel + "</white></gray>"));
        return this;
    }

    /**
     * Adds an upgrade path indicator.
     *
     * @param nextTier The next tier name
     * @return This builder for chaining
     */
    public ShopItemBuilder nextTier(String nextTier) {
        lore.add(Messages.parse("<aqua>Next: " + nextTier + "</aqua>"));
        return this;
    }

    /**
     * Adds a "final tier" indicator.
     *
     * @return This builder for chaining
     */
    public ShopItemBuilder finalTier() {
        lore.add(Messages.parse("<light_purple>Final tier!</light_purple>"));
        return this;
    }

    /**
     * Adds purchase limit info for custom items.
     *
     * @param limit The purchase limit per round
     * @return This builder for chaining
     */
    public ShopItemBuilder purchaseLimit(int limit) {
        if (limit > 0) {
            lore.add(Component.empty());
            lore.addAll(Messages.wrapLines("<red>Max: " + limit + " per round</red>"));
        }
        return this;
    }

    /**
     * Adds an empty line to the lore.
     *
     * @return This builder for chaining
     */
    public ShopItemBuilder emptyLine() {
        lore.add(Component.empty());
        return this;
    }

    /**
     * Adds custom lore line(s).
     *
     * @param miniMessage The lore text in MiniMessage format
     * @return This builder for chaining
     */
    public ShopItemBuilder lore(String miniMessage) {
        lore.addAll(Messages.wrapLines(miniMessage));
        return this;
    }

    /**
     * Adds the standard "Click to purchase!" prompt.
     *
     * @return This builder for chaining
     */
    public ShopItemBuilder purchasePrompt() {
        lore.add(Component.empty());
        lore.add(Messages.parse("<yellow>Click to purchase!</yellow>"));
        return this;
    }

    /**
     * Marks item as owned.
     *
     * @return This builder for chaining
     */
    public ShopItemBuilder owned() {
        lore.clear();
        priceLore.clear();
        lore.addAll(Messages.wrapLines("<gray>You already own this item</gray>"));
        meta.getPersistentDataContainer().set(Keys.ITEM_MAXED, PersistentDataType.BYTE, (byte) 1);
        return this;
    }

    /**
     * Marks item as maxed out.
     *
     * @param message The "maxed" message to display
     * @return This builder for chaining
     */
    public ShopItemBuilder maxed(String message) {
        lore.clear();
        priceLore.clear();
        lore.addAll(Messages.wrapLines(message));
        meta.getPersistentDataContainer().set(Keys.ITEM_MAXED, PersistentDataType.BYTE, (byte) 1);
        return this;
    }

    /**
     * Marks item as locked with custom reason.
     *
     * @param reason The reason for locking
     * @return This builder for chaining
     */
    public ShopItemBuilder locked(String reason) {
        lore.add(Component.empty());
        lore.addAll(Messages.wrapLines("<red>" + reason + "</red>"));
        meta.getPersistentDataContainer().set(Keys.ITEM_MAXED, PersistentDataType.BYTE, (byte) 1);
        return this;
    }

    /**
     * Sets the item identifier key in PDC.
     *
     * @param key The item identifier
     * @return This builder for chaining
     */
    public ShopItemBuilder itemId(String key) {
        meta.getPersistentDataContainer().set(Keys.ITEM_ID, PersistentDataType.STRING, key);
        return this;
    }

    /**
     * Adds an enchantment to the item.
     *
     * @param enchantment The enchantment to add
     * @param level The enchantment level
     * @return This builder for chaining
     */
    public ShopItemBuilder enchant(Enchantment enchantment, int level) {
        meta.addEnchant(enchantment, level, true);
        return this;
    }

    /**
     * Sets item durability (for tools/armor).
     *
     * @param durability The remaining durability
     * @return This builder for chaining
     */
    public ShopItemBuilder durability(int durability) {
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(item.getType().getMaxDurability() - durability);
        }
        return this;
    }

    /**
     * Hides item attributes from tooltip.
     *
     * @return This builder for chaining
     */
    public ShopItemBuilder hideAttributes() {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        return this;
    }

    /**
     * Hides enchantments from tooltip.
     *
     * @return This builder for chaining
     */
    public ShopItemBuilder hideEnchants() {
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    /**
     * Hides all item flags.
     *
     * @return This builder for chaining
     */
    public ShopItemBuilder hideAll() {
        meta.addItemFlags(ItemFlag.values());
        return this;
    }

    /**
     * Builds the final ItemStack.
     *
     * @return The constructed ItemStack
     */
    public ItemStack build() {
        List<Component> finalLore = new ArrayList<>(lore);
        if (!priceLore.isEmpty()) {
            finalLore.addAll(priceLore);
        }
        if (!finalLore.isEmpty()) {
            meta.lore(finalLore);
        }
        Messages.debug(Messages.DebugCategory.LORE, "Shop lore built: material=" + item.getType() + ", lines=" + finalLore.size() + ", priceLines=" + priceLore.size());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}
