package me.psikuvit.cashClash.shop.items;

import me.psikuvit.cashClash.CashClashPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;


public class CustomWeapon {

    public static final CustomWeapon CASH_BLASTER = new CustomWeapon();

    private static final NamespacedKey CASH_BLASTER_KEY =
            new NamespacedKey(CashClashPlugin.getInstance(), "cash_blaster");


    private static final NamespacedKey SOUL_KATANA_KEY =
            new NamespacedKey(CashClashPlugin.getInstance(), "soul_katana");


    public static boolean hasCashBlaster(Player player) {

        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() != Material.BOW) return false;
        if (!item.hasItemMeta()) return false;

        return item.getItemMeta()
                .getPersistentDataContainer()
                .has(CASH_BLASTER_KEY, PersistentDataType.BYTE);
    }


    public static boolean hasSoulKatana(Player player) {


        ItemStack item = player.getInventory().getItemInMainHand();


        if (item.getType() != Material.IRON_SWORD) return false;
        if (!item.hasItemMeta()) return false;


        return item.getItemMeta()
                .getPersistentDataContainer()
                .has(SOUL_KATANA_KEY, PersistentDataType.BYTE);
    }

    public static void markCashBlaster(ItemStack item) {

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(
                CASH_BLASTER_KEY,
                PersistentDataType.BYTE,
                (byte) 1
        );
        item.setItemMeta(meta);

    }
    public static void markSoulKatana(ItemStack item) {

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;


        meta.getPersistentDataContainer().set(
                SOUL_KATANA_KEY,
                PersistentDataType.BYTE,
                (byte) 1
        );


        item.setItemMeta(meta);
    }
}