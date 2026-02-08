package me.psikuvit.cashClash.util.items;

import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.shop.items.Purchasable;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Utility methods that centralize ItemStack creation/modification logic.
 */
public final class ItemUtils {

    private ItemUtils() {
        throw new AssertionError("Nope.");
    }


    /**
     * Equip armor or replace existing armor piece.
     * If there was an old armor piece that was purchased (has ITEM_ID), it will be returned.
     * Starter armor (without ITEM_ID) is discarded on upgrade.
     */
    public static void equipArmorOrReplace(Player player, ItemStack newArmor) {
        if (player == null || newArmor == null) return;
        PlayerInventory inv = player.getInventory();
        Material m = newArmor.getType();

        ItemStack old = null;
        if (m.name().endsWith("HELMET")) {
            old = inv.getHelmet() != null ? inv.getHelmet().clone() : null;
            inv.setHelmet(newArmor);
        } else if (m.name().endsWith("CHESTPLATE")) {
            old = inv.getChestplate() != null ? inv.getChestplate().clone() : null;
            inv.setChestplate(newArmor);
        } else if (m.name().endsWith("LEGGINGS")) {
            old = inv.getLeggings() != null ? inv.getLeggings().clone() : null;
            inv.setLeggings(newArmor);
        } else if (m.name().endsWith("BOOTS")) {
            old = inv.getBoots() != null ? inv.getBoots().clone() : null;
            inv.setBoots(newArmor);
        } else {
            inv.addItem(newArmor);
        }

        if (old == null) return;
        ItemMeta fromMeta = old.getItemMeta();
        ItemMeta toMeta = newArmor.getItemMeta();

        if (fromMeta != null) {
            for (var e : fromMeta.getEnchants().entrySet()) {
                toMeta.addEnchant(e.getKey(), e.getValue(), true);
            }
        }
        newArmor.setItemMeta(toMeta);
    }


    /**
     * Replace the best matching tool/weapon in inventory with the new item.
     * Starter tools (stone tools without ITEM_ID) are discarded on upgrade.
     * Purchased tools (with ITEM_ID) are returned.
     */
    public static void replaceBestMatchingTool(Player player, ItemStack newItem) {
        if (player == null || newItem == null) return;
        PlayerInventory inv = player.getInventory();

        int bestSlot = ItemSelectionUtils.findBestMatchingToolSlot(inv, newItem.getType());

        if (bestSlot != -1) {
            ItemStack best = inv.getItem(bestSlot);
            if (best == null) return;

            ItemMeta oldMeta = best.getItemMeta();
            ItemMeta newMeta = newItem.getItemMeta();

            if (oldMeta != null && newMeta != null) {
                for (var e : oldMeta.getEnchants().entrySet()) {
                    newMeta.addEnchant(e.getKey(), e.getValue(), true);
                }
                newItem.setItemMeta(newMeta);
            }

            inv.setItem(bestSlot, newItem);
        } else {
            inv.addItem(newItem);
        }
    }

    public static boolean applyEnchant(Player player, EnchantEntry ee, int lvl) {
        if (player == null || ee == null) return false;
        if (ee == EnchantEntry.PROTECTION || ee == EnchantEntry.PROJECTILE_PROTECTION) {
            boolean appliedAny = false;

            PlayerInventory inv = player.getInventory();
            ItemStack[] armor = inv.getArmorContents();

            for (ItemStack is : armor) {
                if (is == null) continue;
                if (!ee.getApplicableMaterials().contains(is.getType())) continue;

                ItemMeta meta = is.getItemMeta();
                if (meta == null) continue;

                meta.addEnchant(ee.getEnchantment(), lvl, true);
                is.setItemMeta(meta);
                appliedAny = true;
            }
            if (appliedAny) {
                player.getInventory().setArmorContents(armor);
                Messages.send(player, "<green>Protection " + lvl + " applied to all armor pieces.</green>");
            } else {
                Messages.send(player, "<red>No eligible armor pieces found to apply Protection. It will be saved for future purchases.</red>");
            }
            return true;
        }

        ItemStack held = player.getInventory().getItemInMainHand();

        if (!ee.getApplicableMaterials().contains(held.getType())) {
            Messages.send(player, "<red>The item in your main hand is not eligible for this enchantment. It will be saved for future purchases.</red>");
            return false;
        }

        ItemMeta meta = held.getItemMeta();
        if (meta != null) {
            meta.addEnchant(ee.getEnchantment(), lvl, true);
            held.setItemMeta(meta);
        }
        return true;
    }

    public static void applyOwnedEnchantsAfterPurchase(Player player, Purchasable si) {
        if (player == null || si == null) return;
        if (si.getCategory() == ShopCategory.UTILITY &&
                (si.getMaterial() == Material.BOW || si.getMaterial() == Material.CROSSBOW)) return;

        var session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return;

        var ccp = session.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return;

        for (var e : ccp.getOwnedEnchants().entrySet()) {
            EnchantEntry ee = e.getKey();
            int lvl = e.getValue();

            player.getInventory().forEach(is -> {
                if (is == null) return;
                if (!ee.getApplicableMaterials().contains(is.getType())) return;

                ItemMeta meta = is.getItemMeta();

                if (meta == null) return;
                meta.addEnchant(ee.getEnchantment(), lvl, true);
                is.setItemMeta(meta);
            });
        }
    }

    public static boolean removeItemFromPlayer(Player player, String itemTag, int quantity) {
        int remaining = Math.max(1, quantity);

        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean armorModified = false;
        for (int i = 0; i < armor.length && remaining > 0; i++) {
            ItemStack is = armor[i];
            String val = PDCDetection.getAnyShopTag(is);

            if (val != null && val.equals(itemTag)) {
                armor[i] = null;
                remaining -= 1;
                armorModified = true;
            }
        }
        if (armorModified) {
            player.getInventory().setArmorContents(armor);
        }

        // Check main inventory
        for (int i = 0; i < player.getInventory().getSize() && remaining > 0; i++) {
            ItemStack is = player.getInventory().getItem(i);
            String val = PDCDetection.getAnyShopTag(is);

            if (val != null && val.equals(itemTag)) {
                int amt = is.getAmount();
                if (amt > remaining) {
                    is.setAmount(amt - remaining);
                    player.getInventory().setItem(i, is);
                    remaining = 0;
                } else {
                    player.getInventory().setItem(i, null);
                    remaining -= amt;
                }
            }
        }

        // Check off-hand
        if (remaining > 0) {
            ItemStack off = player.getInventory().getItemInOffHand();
            String val = PDCDetection.getAnyShopTag(off);

            if (val != null && val.equals(itemTag)) {
                int amt = off.getAmount();
                if (amt > remaining) {
                    off.setAmount(amt - remaining);
                    player.getInventory().setItemInOffHand(off);
                    remaining = 0;
                } else {
                    player.getInventory().setItemInOffHand(null);
                    remaining -= amt;
                }
            }
        }


        return remaining < Math.max(1, quantity);
    }


    /**
     * Checks if the player owns all pieces of an armor set.
     */
    public static boolean playerOwnsArmorSet(Player player, CustomArmorItem.ArmorSet set) {
        for (CustomArmorItem piece : set.getPieces()) {
            if (!hasCustomArmorPiece(player, piece)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if the player has a specific custom armor piece (in inventory or equipped).
     */
    public static boolean hasCustomArmorPiece(Player player, CustomArmorItem piece) {
        return Arrays.stream(player.getInventory().getArmorContents())
                .filter(Objects::nonNull)
                .filter(ItemStack::hasItemMeta)
                .map(PDCDetection::getCustomArmor)
                .filter(Objects::nonNull)
                .anyMatch(customArmorItem -> piece.name().equals(customArmorItem.name()));
    }

    /**
     * Checks if a player owns a specific shop item.
     * Uses single-pass stream for better performance.
     *
     * @param player   The player to check
     * @param shopItem The item to look for
     * @return true if the player owns the item
     */
    public static boolean isItemOwned(Player player, Purchasable shopItem) {
        // Only check ownership for categories where duplicates aren't allowed
        ShopCategory category = shopItem.getCategory();
        if (category != ShopCategory.ARMOR &&
                category != ShopCategory.WEAPONS &&
                category != ShopCategory.CUSTOM_ARMOR) {
            return false;
        }
        return Stream.concat(Arrays.stream(player.getInventory().getContents()), Arrays.stream(player.getInventory().getArmorContents()))
                .filter(Objects::nonNull)
                .map(PDCDetection::getAnyShopTag)
                .anyMatch(shopItem.name()::equals);
    }

    /**
     * Updates the item model on an ItemStack.
     * @param item The item to update
     * @param modelKey The model NamespacedKey
     */
    public static void updateItemModel(ItemStack item, NamespacedKey modelKey) {
        if (item == null || modelKey == null) return;
        item.editMeta(meta -> meta.setItemModel(modelKey));
    }
}
