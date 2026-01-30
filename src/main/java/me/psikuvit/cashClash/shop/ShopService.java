package me.psikuvit.cashClash.shop;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.player.PurchaseRecord;
import me.psikuvit.cashClash.player.PurchaseRecord.ArmorSlot;
import me.psikuvit.cashClash.shop.items.ArmorItem;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.shop.items.Purchasable;
import me.psikuvit.cashClash.shop.items.WeaponItem;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.effects.SoundUtils;
import me.psikuvit.cashClash.util.items.ItemFactory;
import me.psikuvit.cashClash.util.items.ItemSelectionUtils;
import me.psikuvit.cashClash.util.items.ItemUtils;
import me.psikuvit.cashClash.util.items.PDCDetection;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Service class for handling shop-related operations.
 */
public class ShopService {

    private static ShopService instance;

    private ShopService() {
    }

    public static ShopService getInstance() {
        if (instance == null) {
            instance = new ShopService();
        }
        return instance;
    }

    public long calculateTotalPrice(Purchasable item, int quantity) {
        return item.getPrice() * Math.max(1, quantity);
    }

    public void processPurchase(Player player, Purchasable item, int quantity, long totalPrice) {
        CashClashPlayer ccp = getCashClashPlayer(player);
        if (ccp == null) return;

        deductCoins(player, totalPrice);
        giveItemToPlayer(player, ccp, item, quantity, totalPrice);
    }


    public void refund(Player player, long amount) {
        CashClashPlayer ccp = getCashClashPlayer(player);
        if (ccp != null) {
            ccp.addCoins(amount);
            Messages.send(player, "<green>Refunded $" + String.format("%,d", amount) + " to your balance.</green>");
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
        }
    }

    public void processRefund(Player player, PurchaseRecord record) {
        CashClashPlayer ccp = getCashClashPlayer(player);
        if (ccp == null) return;

        // Remove the purchase record
        ccp.popLastPurchase();

        refund(player, record.price());

        // Handle set purchase refunds
        if (record.isSetPurchase()) {
            // Remove all set items from player (from armor slots)
            PlayerInventory inv = player.getInventory();
            for (CustomArmorItem piece : record.getSetItemsSafe()) {
                ArmorSlot slot = getArmorSlot(piece.getMaterial());
                if (slot != null) {
                    // Clear the armor slot
                    switch (slot) {
                        case HELMET -> inv.setHelmet(null);
                        case CHESTPLATE -> inv.setChestplate(null);
                        case LEGGINGS -> inv.setLeggings(null);
                        case BOOTS -> inv.setBoots(null);
                    }
                }
            }

            // Restore all replaced set items
            // Custom armor pieces were moved to inventory during purchase, so we need to:
            // 1. Find them in inventory
            // 2. Remove from inventory
            // 3. Re-equip them
            restoreReplacedSetItemsFromInventory(player, record.getReplacedSetItemsSafe());
            Messages.send(player, "<green>Set purchase undone. Refunded $" + String.format("%,d", record.price()) + " and restored your previous armor.</green>");
        } else {
            boolean removed = ItemUtils.removeItemFromPlayer(player, record.item().name(), record.quantity());

            if (record.replacedItem() != null) {
                // Restore the previous purchased item
                restoreReplacedItem(player, record);
                Messages.send(player, "<green>Purchase undone. Refunded $" + String.format("%,d", record.price()) + " and restored your previous item.</green>");
            } else {
                // No purchased item to restore - restore round 1 starter gear if applicable
                restoreStarterGear(player, record.item());
                Messages.send(player, "<green>Purchase undone. Refunded $" + String.format("%,d", record.price()) +
                        (removed ? "" : " (could not find item(s) to remove)") + "</green>");
            }
        }

        SoundUtils.play(player, Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.5f);
    }

    /**
     * Restore replaced set items from inventory (for custom armor) or directly equip (for vanilla armor).
     * Custom armor was moved to inventory during purchase, so we find it there.
     * Vanilla armor was tracked but not in inventory, so we equip it directly.
     */
    private void restoreReplacedSetItemsFromInventory(Player player, Map<ArmorSlot, ItemStack> replacedItems) {
        PlayerInventory inv = player.getInventory();

        for (Map.Entry<ArmorSlot, ItemStack> entry : replacedItems.entrySet()) {
            ItemStack item = entry.getValue();
            if (item == null || item.getType() == Material.AIR) continue;

            ArmorSlot slot = entry.getKey();

            if (PDCDetection.isCustomArmorItem(item)) {
                // Custom armor is in player's inventory - find and remove it, then equip
                String itemId = PDCDetection.getAnyShopTag(item);

                // Find and remove from inventory
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack invItem = inv.getItem(i);
                    String invItemId = PDCDetection.getAnyShopTag(invItem);
                    if (itemId != null && itemId.equals(invItemId)) {
                        inv.setItem(i, null);
                        break;
                    }
                }
            }

            // Equip the armor (either custom from inventory or vanilla)
            switch (slot) {
                case HELMET -> inv.setHelmet(item);
                case CHESTPLATE -> inv.setChestplate(item);
                case LEGGINGS -> inv.setLeggings(item);
                case BOOTS -> inv.setBoots(item);
            }
        }
    }

    private void restoreReplacedItem(Player player, PurchaseRecord record) {
        Purchasable item = record.item();
        ItemStack replacedItem = record.replacedItem();

        if (item instanceof ArmorItem || item instanceof CustomArmorItem) {
            // If the replaced item was custom armor, it's in inventory - find and remove it first
            if (PDCDetection.isCustomArmorItem(replacedItem)) {
                String itemId = PDCDetection.getAnyShopTag(replacedItem);
                PlayerInventory inv = player.getInventory();

                // Find and remove from inventory
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack invItem = inv.getItem(i);
                    String invItemId = PDCDetection.getAnyShopTag(invItem);
                    if (itemId != null && itemId.equals(invItemId)) {
                        inv.setItem(i, null);
                        break;
                    }
                }
            }
            // Now equip the replaced item
            ItemUtils.equipArmorOrReplace(player, replacedItem);
        } else if (item.getCategory() == ShopCategory.WEAPONS) {
            ItemUtils.replaceBestMatchingTool(player, record.replacedItem());
        } else {
            player.getInventory().addItem(record.replacedItem());
        }
    }

    private void restoreStarterGear(Player player, Purchasable item) {
        Material material = item.getMaterial();
        String matName = material.name();

        // Check if this was an armor piece - restore round 1 starter armor
        if (matName.endsWith("HELMET")) {
            ItemStack starterHelmet = new ItemStack(Material.LEATHER_HELMET);
            ItemMeta meta = starterHelmet.getItemMeta();
            if (meta != null) {
                meta.setUnbreakable(true);
                starterHelmet.setItemMeta(meta);
            }
            player.getInventory().setHelmet(starterHelmet);
        } else if (matName.endsWith("CHESTPLATE")) {
            player.getInventory().setChestplate(new ItemStack(Material.GOLDEN_CHESTPLATE));
        } else if (matName.endsWith("LEGGINGS")) {
            player.getInventory().setLeggings(new ItemStack(Material.GOLDEN_LEGGINGS));
        } else if (matName.endsWith("BOOTS")) {
            player.getInventory().setBoots(new ItemStack(Material.GOLDEN_BOOTS));
        }
        // Check if this was a sword/axe - restore stone tools
        else if (matName.contains("SWORD")) {
            player.getInventory().addItem(new ItemStack(Material.STONE_SWORD));
        } else if (matName.contains("AXE") && !matName.contains("PICKAXE")) {
            player.getInventory().addItem(new ItemStack(Material.STONE_AXE));
        }
    }

    /**
     * Check if a player can afford a purchase.
     *
     * @param player The player making the purchase.
     * @param cost   The cost of the purchase.
     * @return True if the player can afford the purchase, false otherwise.
     */
    public boolean canAfford(Player player, long cost) {
        CashClashPlayer ccp = getCashClashPlayer(player);
        return ccp != null && ccp.getCoins() >= cost;
    }

    private void giveItemToPlayer(Player player, CashClashPlayer ccp, Purchasable item, int quantity, long totalPrice) {
        int giveQty = Math.max(1, quantity);

        GameSession sess = GameManager.getInstance().getPlayerSession(player);
        int round = sess != null ? sess.getCurrentRound() : 1;

        switch (item) {
            case CustomArmorItem customArmor -> {
                // Check if this is part of a set (Deathmauler, Dragon, Flamebringer, Investor)
                if (customArmor.isPartOfSet() && customArmor.getArmorSet() != null) {
                    // Handle full set purchase - track all replaced items
                    List<CustomArmorItem> setPieces = customArmor.getArmorSet().getPieces();
                    Map<ArmorSlot, ItemStack> replacedSetItems = new EnumMap<>(ArmorSlot.class);

                    for (CustomArmorItem piece : setPieces) {
                        // Get replaced item before equipping
                        ArmorSlot slot = getArmorSlot(piece.getMaterial());
                        ItemStack currentArmor = getCurrentArmorInSlot(player, slot);

                        if (currentArmor != null && currentArmor.getType() != Material.AIR) {
                            // Check if it's custom armor (purchased item with custom armor tag)
                            if (PDCDetection.isCustomArmorItem(currentArmor)) {
                                // Custom armor goes to inventory
                                replacedSetItems.put(slot, currentArmor.clone());
                                returnReplacedItemToInventory(player, currentArmor.clone());
                            } else if (PDCDetection.getAnyShopTag(currentArmor) != null) {
                                // Purchased vanilla armor (iron/diamond) - track but don't return to inventory
                                replacedSetItems.put(slot, currentArmor.clone());
                            }
                        }

                        // Equip the set piece
                        ItemFactory.getInstance().createAndEquipCustomArmor(player, piece);
                    }

                    // Create set purchase record with all replaced items
                    long setPrice = customArmor.getArmorSet().getTotalPrice();
                    ccp.addPurchase(new PurchaseRecord(setPieces.getFirst(), setPrice, round, replacedSetItems, setPieces));

                    // Apply enchants to all pieces
                    for (CustomArmorItem piece : setPieces) {
                        ItemUtils.applyOwnedEnchantsAfterPurchase(player, piece);
                    }

                    Messages.send(player, "<green>Purchased " + customArmor.getArmorSet().getDisplayName() + " for $" + String.format("%,d", setPrice) + "</green>");
                    SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                } else {
                    // Individual custom armor piece (Magic Helmet, Bunny Shoes, Tax Evasion Pants, Guardian's Vest)
                    // Get the currently equipped armor before replacing
                    ArmorSlot slot = getArmorSlot(customArmor.getMaterial());
                    ItemStack currentArmor = getCurrentArmorInSlot(player, slot);
                    ItemStack replacedItem = null;

                    if (currentArmor != null && currentArmor.getType() != Material.AIR) {
                        // Check if it's custom armor - return to inventory
                        if (PDCDetection.isCustomArmorItem(currentArmor)) {
                            replacedItem = currentArmor.clone();
                            returnReplacedItemToInventory(player, currentArmor.clone());
                        } else if (PDCDetection.getAnyShopTag(currentArmor) != null) {
                            // Purchased vanilla armor - track but don't return (disappears)
                            replacedItem = currentArmor.clone();
                        }
                        // Starter armor (no tag) just disappears
                    }

                    // Equip the custom armor
                    ItemFactory.getInstance().createAndEquipCustomArmor(player, customArmor);


                    ccp.addPurchase(new PurchaseRecord(item, 1, item.getPrice(), replacedItem, round));
                    ItemUtils.applyOwnedEnchantsAfterPurchase(player, item);

                    Messages.send(player, "<green>Purchased " + item.getDisplayName() + " for $" + String.format("%,d", item.getPrice()) + "</green>");
                    SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                }
            }
            case ArmorItem ignored -> {
                // Normal/upgradable armor (Iron, Diamond)
                ItemStack armorItem = ItemFactory.getInstance().createGameplayItem(item);

                // Get current armor before replacing
                ArmorSlot slot = getArmorSlot(armorItem.getType());
                ItemStack currentArmor = getCurrentArmorInSlot(player, slot);
                ItemStack replacedItem = null;

                // Track replaced item if it was a purchased item
                if (currentArmor != null && currentArmor.getType() != Material.AIR &&
                        PDCDetection.getAnyShopTag(currentArmor) != null) {
                    replacedItem = currentArmor.clone();

                    // If replacing custom armor piece with normal armor, return it to inventory
                    if (PDCDetection.isCustomArmorItem(currentArmor)) {
                        returnReplacedItemToInventory(player, replacedItem);
                    }
                }

                // Equip the armor
                ItemUtils.equipArmorOrReplace(player, armorItem);

                ccp.addPurchase(new PurchaseRecord(item, 1, item.getPrice(), replacedItem, round));
                ItemUtils.applyOwnedEnchantsAfterPurchase(player, item);

                Messages.send(player, "<green>Purchased " + item.getDisplayName() + " for $" + String.format("%,d", item.getPrice()) + "</green>");
                SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
            }
            case WeaponItem ignored -> {
                ItemStack weaponItem = ItemFactory.getInstance().createGameplayItem(item);
                ItemStack replacedItem = replaceWeaponInInventory(player, weaponItem);

                ccp.addPurchase(new PurchaseRecord(item, 1, item.getPrice(), replacedItem, round));
                ItemUtils.applyOwnedEnchantsAfterPurchase(player, item);

                Messages.send(player, "<green>Purchased " + item.getDisplayName() + " for $" + String.format("%,d", item.getPrice()) + "</green>");
                SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
            }
            default -> {
                // Other items - just add to inventory
                ItemStack stack = ItemFactory.getInstance().createGameplayItem(item);
                stack.setAmount(giveQty);
                player.getInventory().addItem(stack);
                ccp.addPurchase(new PurchaseRecord(item, giveQty, totalPrice, round));

                Messages.send(player, "<green>Purchased " + item.getDisplayName() + " x" + giveQty + " for $" + String.format("%,d", totalPrice) + "</green>");
                SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
            }
        }
    }

    /**
     * Return a replaced item to player's inventory, or drop if full.
     */
    private void returnReplacedItemToInventory(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(item);
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }

    /**
     * Replace a weapon in the player's inventory with a new one.
     * Finds the matching weapon category (sword/axe) and replaces it.
     * The old weapon is discarded (not returned to inventory).
     *
     * @param player The player
     * @param newWeapon The new weapon to give
     * @return The replaced item (for PurchaseRecord tracking), null if nothing was replaced
     */
    private ItemStack replaceWeaponInInventory(Player player, ItemStack newWeapon) {
        PlayerInventory inv = player.getInventory();
        Material newType = newWeapon.getType();

        // Find matching weapon slot
        int bestSlot = ItemSelectionUtils.findBestMatchingToolSlot(inv, newType);

        if (bestSlot != -1) {
            ItemStack existing = inv.getItem(bestSlot);
            if (existing != null) {
                ItemStack oldItem = existing.clone();

                // Transfer enchantments from old to new
                ItemMeta oldMeta = existing.getItemMeta();
                ItemMeta newMeta = newWeapon.getItemMeta();
                if (oldMeta != null && newMeta != null) {
                    for (var e : oldMeta.getEnchants().entrySet()) {
                        newMeta.addEnchant(e.getKey(), e.getValue(), true);
                    }
                    newWeapon.setItemMeta(newMeta);
                }

                inv.setItem(bestSlot, newWeapon);
                return oldItem;
            }
        }

        // No matching weapon found, just add to inventory
        inv.addItem(newWeapon);
        return null;
    }

    /**
     * Get the armor slot for a given material.
     */
    private ArmorSlot getArmorSlot(Material material) {
        String matName = material.name();
        if (matName.endsWith("HELMET")) return ArmorSlot.HELMET;
        if (matName.endsWith("CHESTPLATE")) return ArmorSlot.CHESTPLATE;
        if (matName.endsWith("LEGGINGS")) return ArmorSlot.LEGGINGS;
        if (matName.endsWith("BOOTS")) return ArmorSlot.BOOTS;
        return null;
    }

    /**
     * Get the current armor in a specific slot.
     */
    private ItemStack getCurrentArmorInSlot(Player player, ArmorSlot slot) {
        if (slot == null) return null;
        PlayerInventory inv = player.getInventory();
        return switch (slot) {
            case HELMET -> inv.getHelmet();
            case CHESTPLATE -> inv.getChestplate();
            case LEGGINGS -> inv.getLeggings();
            case BOOTS -> inv.getBoots();
        };
    }


    public void deductCoins(Player player, long cost) {
        CashClashPlayer ccp = getCashClashPlayer(player);
        if (ccp != null) {
            ccp.deductCoins(cost);
            Messages.send(player, "<green>Purchase successful! Deducted $" + String.format("%,d", cost) + "</green>");
            SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
    }

    private CashClashPlayer getCashClashPlayer(Player player) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        return session != null ? session.getCashClashPlayer(player.getUniqueId()) : null;
    }
}
