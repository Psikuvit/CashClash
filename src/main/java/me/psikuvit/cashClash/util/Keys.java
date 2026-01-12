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
    public static final NamespacedKey ITEM_ID;

    /**
     * Flag to mark an item as maxed/owned (cannot be purchased again).
     * Value: byte (1 = maxed)
     */
    public static final NamespacedKey ITEM_MAXED;

    /**
     * UUID of the player who owns this item (for custom items like bounce pads).
     */
    public static final NamespacedKey ITEM_OWNER;

    /**
     * Remaining uses for items with limited durability.
     */
    public static final NamespacedKey ITEM_USES;

    /**
     * Key for shop NPC entities.
     */
    public static final NamespacedKey SHOP_NPC_KEY;

    /**
     * Amount value for supply drop emeralds.
     */
    public static final NamespacedKey SUPPLY_DROP_AMOUNT;

    /**
     * BlazeBite crossbow mode identifier.
     * "glacier" = Glacier Crossbow, "volcano" = Volcano Crossbow
     */
    public static final NamespacedKey BLAZEBITE_MODE;

    /**
     * BloodWrench crossbow mode identifier.
     * "rapid" = Rapid Fire mode, "supercharged" = Supercharged mode
     */
    public static final NamespacedKey BLOODWRENCH_MODE;

    /**
     * Key for arena NPC mannequins (opens arena selector GUI).
     */
    public static final NamespacedKey ARENA_NPC_KEY;


    static {
        ITEM_ID = new NamespacedKey(CashClashPlugin.getInstance(), "item_id");
        ITEM_MAXED = new NamespacedKey(CashClashPlugin.getInstance(), "item_maxed");
        ITEM_OWNER = new NamespacedKey(CashClashPlugin.getInstance(), "item_owner");
        ITEM_USES = new NamespacedKey(CashClashPlugin.getInstance(), "item_uses");
        SHOP_NPC_KEY = new NamespacedKey(CashClashPlugin.getInstance(), "shop_npc");
        SUPPLY_DROP_AMOUNT = new NamespacedKey(CashClashPlugin.getInstance(), "supply_amount");
        BLAZEBITE_MODE = new NamespacedKey(CashClashPlugin.getInstance(), "blazebite_mode");
        BLOODWRENCH_MODE = new NamespacedKey(CashClashPlugin.getInstance(), "bloodwrench_mode");
        ARENA_NPC_KEY = new NamespacedKey(CashClashPlugin.getInstance(), "arena_npc");
    }

    private Keys() {
        throw new AssertionError("Nope.");
    }
}
