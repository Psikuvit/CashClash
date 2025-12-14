package me.psikuvit.cashClash.util;

import me.psikuvit.cashClash.items.CustomArmor;
import me.psikuvit.cashClash.shop.ShopItem;
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

    public static CustomArmor mapFromShopItem(ShopItem item) {
        return switch (item) {
            case DRAGON_BOOTS -> CustomArmor.DRAGON_BOOTS;
            case DRAGON_CHESTPLATE -> CustomArmor.DRAGON_CHESTPLATE;
            case DRAGON_HELMET -> CustomArmor.DRAGON_HELMET;
            case DEATHMAULER_CHESTPLATE -> CustomArmor.DEATHMAULER_CHESTPLATE;
            case DEATHMAULER_LEGGINGS -> CustomArmor.DEATHMAULER_LEGGINGS;
            case INVESTORS_BOOTS -> CustomArmor.INVESTORS_BOOTS;
            case INVESTORS_CHESTPLATE -> CustomArmor.INVESTORS_CHESTPLATE;
            case INVESTORS_LEGGINGS -> CustomArmor.INVESTORS_LEGGINGS;
            case INVESTORS_HELMET -> CustomArmor.INVESTORS_HELMET;
            case FLAMEBRINGER_BOOTS -> CustomArmor.FLAMEBRINGER_BOOTS;
            case FLAMEBRINGER_LEGGINGS -> CustomArmor.FLAMEBRINGER_LEGGINGS;
            case MAGIC_HELMET -> CustomArmor.MAGIC_HELMET;
            case GUARDIANS_VEST -> CustomArmor.GUARDIANS_VEST;
            case BUNNY_SHOES -> CustomArmor.BUNNY_SHOES;
            case TAX_EVASION_PANTS -> CustomArmor.TAX_EVASION_PANTS;
            case null, default -> null;
        };
    }
}

