package me.psikuvit.cashClash.util;

import me.psikuvit.cashClash.CashClashPlugin;
import org.bukkit.NamespacedKey;

public class Keys {

    /**
     * Universal item identifier key - used for:
     * - Shop GUI items (to identify which item type they represent)
     * - Bought items in player inventory (to track origin)
     * - Custom items, mythic items, custom armor, food, etc.
     */
    public static final NamespacedKey ITEM_ID = new NamespacedKey(CashClashPlugin.getInstance(), "item_id");

    /**
     * Flag to mark an item as maxed/owned (cannot be purchased again).
     * Value: byte (1 = maxed)
     */
    public static final NamespacedKey ITEM_MAXED = new NamespacedKey(CashClashPlugin.getInstance(), "item_maxed");

    /**
     * UUID of the player who owns this item (for custom items like bounce pads).
     */
    public static final NamespacedKey ITEM_OWNER = new NamespacedKey(CashClashPlugin.getInstance(), "item_owner");

    /**
     * Remaining uses for items with limited durability.
     */
    public static final NamespacedKey ITEM_USES = new NamespacedKey(CashClashPlugin.getInstance(), "item_uses");

    /**
     * Key for shop NPC entities.
     */
    public static final NamespacedKey SHOP_NPC_KEY = new NamespacedKey(CashClashPlugin.getInstance(), "shop_npc");

    /**
     * Amount value for supply drop emeralds.
     */
    public static final NamespacedKey SUPPLY_DROP_AMOUNT = new NamespacedKey(CashClashPlugin.getInstance(), "supply_amount");

    private Keys() {
        throw new AssertionError("Nope.");
    }
}
