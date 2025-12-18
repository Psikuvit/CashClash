package me.psikuvit.cashClash.util;

import me.psikuvit.cashClash.CashClashPlugin;
import org.bukkit.NamespacedKey;

public class Keys {

    public static final NamespacedKey SHOP_ITEM_KEY = new NamespacedKey(CashClashPlugin.getInstance(), "shop_item");
    public static final NamespacedKey SHOP_BOUGHT_KEY = new NamespacedKey(CashClashPlugin.getInstance(), "shop_bought");
    public static final NamespacedKey SHOP_NPC_KEY = new NamespacedKey(CashClashPlugin.getInstance(), "shop_npc");
    public static final NamespacedKey SUPPLY_DROP_AMOUNT = new NamespacedKey(CashClashPlugin.getInstance(), "supply_amount");

    // Custom items
    public static final NamespacedKey CUSTOM_ITEM_KEY = new NamespacedKey(CashClashPlugin.getInstance(), "custom_item");
    public static final NamespacedKey CUSTOM_ITEM_OWNER = new NamespacedKey(CashClashPlugin.getInstance(), "custom_item_owner");
    public static final NamespacedKey CUSTOM_ITEM_TEAM = new NamespacedKey(CashClashPlugin.getInstance(), "custom_item_team");
    public static final NamespacedKey CUSTOM_ITEM_USES = new NamespacedKey(CashClashPlugin.getInstance(), "custom_item_uses");
    public static final NamespacedKey BOUNCE_PAD_KEY = new NamespacedKey(CashClashPlugin.getInstance(), "bounce_pad");
    public static final NamespacedKey BOOMBOX_KEY = new NamespacedKey(CashClashPlugin.getInstance(), "boombox");

    // Mythic items
    public static final NamespacedKey MYTHIC_ITEM_KEY = new NamespacedKey(CashClashPlugin.getInstance(), "mythic_item");
    private Keys() {
        throw new AssertionError("Nope.");
    }
}
