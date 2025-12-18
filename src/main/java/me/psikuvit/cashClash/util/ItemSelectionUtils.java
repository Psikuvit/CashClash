package me.psikuvit.cashClash.util;

import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.shop.items.Purchasable;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Small utility focused on ranking materials and selecting the best matching
 * tool/item in an inventory. Keeps selection algorithms isolated from other
 * item helpers.
 */
public final class ItemSelectionUtils {

    private ItemSelectionUtils() {
        throw new AssertionError("Nope.");
    }

    /**
     * Ranks materials by "power" for selection purposes.
     * Higher number = stronger/better material.
     */
    public static int rankMaterial(Material m) {
        if (m == null) return 0;
        String name = m.name();
        if (name.contains("NETHERITE")) return 5;
        if (name.contains("DIAMOND")) return 4;
        if (name.contains("IRON")) return 3;
        if (name.contains("STONE")) return 2;
        return 1;
    }

    public static boolean sameCategory(Material a, Material b) {
        if (a == null || b == null) return false;
        String sa = a.name();
        String sb = b.name();
        if (sa.endsWith("SWORD") && sb.endsWith("SWORD")) return true;
        if (sa.endsWith("_AXE") && sb.endsWith("_AXE")) return true;
        if (sa.endsWith("PICKAXE") && sb.endsWith("PICKAXE")) return true;
        if (sa.endsWith("SHOVEL") && sb.endsWith("SHOVEL")) return true;
        if (sa.equals("BOW") && sb.equals("BOW")) return true;
        if (sa.equals("CROSSBOW") && sb.equals("CROSSBOW")) return true;
        if (sa.equals("FISHING_ROD") && sb.equals("FISHING_ROD")) return true;
        return sa.equals("MACE") && sb.equals("MACE");
    }

    public static boolean isToolOrWeapon(Material m) {
        if (m == null) return false;
        String name = m.name();
        return name.endsWith("SWORD") || name.endsWith("AXE") || name.endsWith("PICKAXE") || name.endsWith("SHOVEL")
                || name.endsWith("HOE") || name.equals("BOW") || name.equals("CROSSBOW") || name.equals("FISHING_ROD") || name.equals("MACE");
    }

    /**
     * Finds the inventory slot of the strongest matching tool for the given
     * new item type. Returns -1 if none found.
     */
    public static int findBestMatchingToolSlot(PlayerInventory inv, Material newType) {
        if (inv == null || newType == null) return -1;
        ItemStack best = null;
        int bestSlot = -1;

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack is = inv.getItem(i);

            if (is == null) continue;
            if (!isToolOrWeapon(is.getType())) continue;
            if (!sameCategory(is.getType(), newType)) continue;

            if (best == null) {
                best = is;
                bestSlot = i;
                continue;
            }
            if (rankMaterial(is.getType()) > rankMaterial(best.getType())) {
                best = is;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    /**
     * Converts a Purchasable item to CustomArmorItem if applicable.
     * Returns the CustomArmorItem if the item is custom armor, null otherwise.
     */
    public static CustomArmorItem getCustomArmorItem(Purchasable item) {
        if (item == null) return null;

        return item instanceof CustomArmorItem ca ? ca : null;
    }
}

