package me.psikuvit.cashClash.util;

import me.psikuvit.cashClash.CashClashPlugin;
import org.bukkit.NamespacedKey;

public class Keys {

    public static final NamespacedKey SHOP_ITEM_KEY = new NamespacedKey(CashClashPlugin.getInstance(), "shop_item");
    public static final NamespacedKey SHOP_BOUGHT_KEY = new NamespacedKey(CashClashPlugin.getInstance(), "shop_bought");
    public static final NamespacedKey SHOP_NPC_KEY = new NamespacedKey(CashClashPlugin.getInstance(), "shop_npc");
    public static final NamespacedKey SUPPLY_DROP_AMOUNT = new NamespacedKey(CashClashPlugin.getInstance(), "supply_amount");

    private Keys() {
        throw new AssertionError("Nope.");
    }
}
