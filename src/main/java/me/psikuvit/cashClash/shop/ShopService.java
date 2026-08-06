package me.psikuvit.cashClash.shop;

import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.player.PurchaseRecord;
import me.psikuvit.cashClash.player.PurchaseRecord.ArmorSlot;
import me.psikuvit.cashClash.shop.items.ArmorItem;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.shop.items.Purchasable;
import me.psikuvit.cashClash.shop.items.WeaponItem;
import me.psikuvit.cashClash.shop.items.UtilityItem;
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

        // Block fully-capped utility items before coins ever get deducted, so a blocked
        // purchase doesn't also show a "Purchase successful" receipt that just gets refunded
        if (item instanceof UtilityItem utilityItem && isUtilityCapReached(player, utilityItem)) {
            return;
        }

        // Regular totems and Totems of Haunting share one combined cap of 2 - buying either
        // counts against both.
        if ((item == UtilityItem.TOTEM || item == CustomItem.TOTEM_OF_HAUNTING) && isTotemCapReached(player)) {
            return;
        }

        deductCoins(player, totalPrice);
        giveItemToPlayer(player, ccp, item, quantity, totalPrice);
    }

    /**
     * Check (and message/sound) a hard-capped utility item that's already at its limit.
     * Items bought in bulk that straddle the cap are still handled in giveItemToPlayer.
     */
    private boolean isUtilityCapReached(Player player, UtilityItem utilityItem) {
        int max = switch (utilityItem) {
            case COBWEB -> 8;
            default -> -1;
        };
        if (max < 0) return false;

        if (countMaterial(player, utilityItem.getMaterial()) < max) return false;

        Messages.send(player, "listener.max-webs-reached");
        SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        return true;
    }

    /**
     * Regular totems (Material.TOTEM_OF_UNDYING) and Totems of Haunting (Material.NETHER_STAR)
     * share one combined cap of 2 owned at once - owning one counts against the other.
     */
    private boolean isTotemCapReached(Player player) {
        int combined = countMaterial(player, UtilityItem.TOTEM.getMaterial())
                + countMaterial(player, CustomItem.TOTEM_OF_HAUNTING.getMaterial());
        if (combined < 2) return false;

        Messages.send(player, "listener.max-totems-reached");
        SoundUtils.play(player, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        return true;
    }

    private int countMaterial(Player player, Material material) {
        int count = 0;
        for (ItemStack is : player.getInventory().getContents()) {
            if (is != null && is.getType() == material) {
                count += is.getAmount();
            }
        }
        return count;
    }


    public void refund(Player player, long amount) {
        CashClashPlayer ccp = getCashClashPlayer(player);
        if (ccp != null) {
            ccp.addCoinsSilently(amount);
            Messages.send(player, "shop.refunded",
                "amount", String.format("%,d", amount));
            SoundUtils.play(player, Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
        }
    }

    public static void transferEnchants(ItemStack newWeapon, PlayerInventory inv, int bestSlot, ItemStack existing) {
        ItemUtils.transferEnchants(existing, newWeapon);
        inv.setItem(bestSlot, newWeapon);
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
                ArmorSlot slot = ItemUtils.getArmorSlot(piece.getMaterial());
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
            Messages.send(player, "shop.set-purchase-undone",
                "amount", String.format("%,d", record.price()));
        } else {
            boolean removed = ItemUtils.removeItemFromPlayer(player, record.item().name(), record.quantity());

            if (record.replacedItem() != null) {
                // Restore the previous purchased item
                restoreReplacedItem(player, record);
                Messages.send(player, "shop.purchase-undone",
                    "amount", String.format("%,d", record.price()));
            } else {
                // No purchased item to restore - restore round 1 starter gear if applicable
                restoreStarterGear(player, record.item());
                Messages.send(player, "shop.purchase-undone",
                    "amount", String.format("%,d", record.price()));
                if (!removed) {
                    Messages.send(player, "shop.purchase-undone-item-not-found");
                }
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
                findAndRemove(inv, itemId);
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

    private void findAndRemove(PlayerInventory inv, String itemId) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack invItem = inv.getItem(i);
            if (invItem == null || invItem.getType().isAir()) continue;

            String invItemId = PDCDetection.getAnyShopTag(invItem);
            if (itemId != null && itemId.equals(invItemId)) {
                inv.setItem(i, null);
                break;
            }
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

    private void restoreReplacedItem(Player player, PurchaseRecord record) {
        Purchasable item = record.item();
        ItemStack replacedItem = record.replacedItem();

        if (item instanceof ArmorItem || item instanceof CustomArmorItem) {
            // If the replaced item was custom armor, it's in inventory - find and remove it first
            if (PDCDetection.isCustomArmorItem(replacedItem)) {
                String itemId = PDCDetection.getAnyShopTag(replacedItem);
                PlayerInventory inv = player.getInventory();

                // Find and remove from inventory
                findAndRemove(inv, itemId);
            }
            // Now equip the replaced item
            ItemUtils.equipArmorOrReplace(player, replacedItem);
        } else if (item.getCategory() == ShopCategory.WEAPONS) {
            ItemUtils.replaceBestMatchingTool(player, record.replacedItem());
        } else {
            player.getInventory().addItem(record.replacedItem());
        }
    }

    private void giveItemToPlayer(Player player, CashClashPlayer ccp, Purchasable item, int quantity, long totalPrice) {
        int giveQty = Math.max(1, quantity);

        GameSession sess = GameManager.getInstance().getPlayerSession(player);
        int round = sess != null ? sess.getCurrentRound() : 1;
        Messages.debug("Bought item: " + item);

        switch (item) {
            case UtilityItem utilityItem when utilityItem == UtilityItem.COBWEB -> {
                // The fully-capped case is blocked earlier in processPurchase(); this only
                // handles a bulk purchase that straddles the 8-web cap.
                int currentWebs = countMaterial(player, Material.COBWEB);
                int canGive = Math.min(giveQty, 8 - currentWebs);
                ItemStack stack = ItemFactory.getInstance().createGameplayItem(item);
                stack.setAmount(canGive);
                player.getInventory().addItem(stack);
                ccp.addPurchase(new PurchaseRecord(item, canGive, totalPrice, round));

                if (canGive < giveQty) {
                    long refundAmount = (totalPrice / giveQty) * (giveQty - canGive);
                    refund(player, refundAmount);
                }
            }
            case CustomArmorItem customArmor -> {
                Messages.debug(player, Messages.DebugCategory.SHOP, "Custom armor purchase: " + customArmor.name()
                        + " (material=" + customArmor.getMaterial() + ", partOfSet=" + customArmor.isPartOfSet() + ")");
                // Check if this is part of a set (Deathmauler, Dragon, Flamebringer, Investor)
                if (customArmor.isPartOfSet()) {
                    // Handle full set purchase - track all replaced items
                    List<CustomArmorItem> setPieces = customArmor.getArmorSet().getPieces();
                    Map<ArmorSlot, ItemStack> replacedSetItems = new EnumMap<>(ArmorSlot.class);

                    for (CustomArmorItem piece : setPieces) {
                        // Get replaced item before equipping
                        ArmorSlot slot = ItemUtils.getArmorSlot(piece.getMaterial());
                        ItemStack currentArmor = ItemUtils.getCurrentArmorInSlot(player, slot);

                        if (currentArmor != null && currentArmor.getType() != Material.AIR) {
                            // Check if it's custom armor (purchased item with custom armor tag)
                            if (PDCDetection.isCustomArmorItem(currentArmor)) {
                                // Custom armor goes to inventory
                                replacedSetItems.put(slot, currentArmor.clone());
                                ItemUtils.returnItemToInventoryOrDrop(player, currentArmor.clone());
                            } else if (PDCDetection.getAnyShopTag(currentArmor) != null) {
                                // Purchased vanilla armor (iron/diamond) - track but don't return to inventory
                                replacedSetItems.put(slot, currentArmor.clone());
                            }
                        }

                        // Equip the set piece
                        Messages.debug(player, Messages.DebugCategory.SHOP, "Equipping set piece: " + piece.name() + " (slot=" + slot + ")");
                        ItemFactory.getInstance().createAndEquipCustomArmor(player, piece);
                    }
                    Messages.debug(player, Messages.DebugCategory.SHOP, "Set purchase complete: " + setPieces.size() + " pieces equipped");

                    // Create set purchase record with all replaced items
                    long setPrice = customArmor.getArmorSet().getTotalPrice();
                    ccp.addPurchase(new PurchaseRecord(setPieces.getFirst(), setPrice, round, replacedSetItems, setPieces));

                    // Note: runes are NOT auto-reapplied to the newly equipped pieces here -
                    // a rune only ever affects its manually linked, currently active target
                    // (see RuneManager), same as every other purchase path.

                    Messages.send(player, "shop.purchased-set",
                        "item_name", customArmor.getArmorSet().getDisplayName(),
                        "price", String.format("%,d", setPrice));
                    SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                } else {
                    // Individual custom armor piece (Bunny Shoes, Bullseye Pants, Guardian's Vest)
                    ArmorSlot slot = ItemUtils.getArmorSlot(customArmor.getMaterial());
                    ItemStack currentArmor = ItemUtils.getCurrentArmorInSlot(player, slot);
                    Messages.debug("Current armor in slot " + slot + ": " + currentArmor);
                    ItemStack replacedItem = null;

                    if (currentArmor != null && currentArmor.getType() != Material.AIR) {
                        // Check if it's custom armor - return to inventory
                        if (PDCDetection.isCustomArmorItem(currentArmor)) {
                            Messages.debug("Replacing custom armor in slot " + slot + ": " + currentArmor);
                            replacedItem = currentArmor;
                            ItemUtils.returnItemToInventoryOrDrop(player, currentArmor.clone());
                        } else if (PDCDetection.getAnyShopTag(currentArmor) != null) {
                            // Purchased vanilla armor - track but don't return (disappears)
                            Messages.debug("Replacing vanilla armor in slot " + slot + ": " + currentArmor);
                            replacedItem = currentArmor;
                        }
                    }

                    // Equip the custom armor
                    Messages.debug(player, Messages.DebugCategory.SHOP, "Equipping individual custom armor: " + customArmor.name() + " (slot=" + slot + ")");
                    ItemFactory.getInstance().createAndEquipCustomArmor(player, customArmor);
                    Messages.debug(player, Messages.DebugCategory.SHOP, "Individual custom armor equip call finished: " + customArmor.name());

                    cachePurchase(player, ccp, item, round, replacedItem);
                }
            }
            case ArmorItem ignored -> {
                // Normal/upgradable armor (Iron, Diamond)
                ItemStack armorItem = ItemFactory.getInstance().createGameplayItem(item);
                Messages.debug(player, Messages.DebugCategory.SHOP, "Buying normal armor: " + item.getDisplayName()
                        + " (material=" + armorItem.getType() + ", itemMeta=" + armorItem.hasItemMeta() + ")");

                // Get current armor before replacing
                ArmorSlot slot = ItemUtils.getArmorSlot(armorItem.getType());
                ItemStack currentArmor = ItemUtils.getCurrentArmorInSlot(player, slot);
                Messages.debug(player, Messages.DebugCategory.SHOP, "Current armor in slot " + slot + ": " + currentArmor);
                ItemStack replacedItem = null;

                // Track replaced item if it was a purchased item
                if (currentArmor != null && currentArmor.getType() != Material.AIR &&
                        PDCDetection.getAnyShopTag(currentArmor) != null) {
                    replacedItem = currentArmor.clone();

                    // If replacing custom armor piece with normal armor, return it to inventory
                    if (PDCDetection.isCustomArmorItem(currentArmor)) {
                        ItemUtils.returnItemToInventoryOrDrop(player, replacedItem);
                    }
                }

                // Equip the armor
                Messages.debug(player, Messages.DebugCategory.SHOP, "Equipping normal armor via equipArmorOrReplace: " + armorItem.getType());
                ItemUtils.equipArmorOrReplace(player, armorItem);
                Messages.debug(player, Messages.DebugCategory.SHOP, "After equip - helmet=" + player.getInventory().getHelmet()
                        + ", chest=" + player.getInventory().getChestplate());

                cachePurchase(player, ccp, item, round, replacedItem);
            }
            case WeaponItem ignored -> {
                ItemStack weaponItem = ItemFactory.getInstance().createGameplayItem(item);
                ItemStack replacedItem = replaceWeaponInInventory(player, weaponItem);

                cachePurchase(player, ccp, item, round, replacedItem);
            }
            default -> {
                // Skip mythic items - they are handled by MythicCategoryGui
                if (item instanceof me.psikuvit.cashClash.shop.items.MythicItem) {
                    ccp.addPurchase(new PurchaseRecord(item, giveQty, totalPrice, round));
                    return; // MythicCategoryGui will handle giving the actual item
                }

                // Other items - just add to inventory
                ItemStack stack = ItemFactory.getInstance().createGameplayItem(item);
                stack.setAmount(giveQty);
                player.getInventory().addItem(stack);
                ccp.addPurchase(new PurchaseRecord(item, giveQty, totalPrice, round));

                Messages.send(player, "shop.purchased-quantity",
                    "item_name", item.getDisplayName(),
                    "quantity", String.valueOf(giveQty),
                    "price", String.format("%,d", totalPrice));
                SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
            }
        }

        ItemStack[] armorContents = player.getInventory().getArmorContents();
        Messages.debug(player, Messages.DebugCategory.SHOP, "giveItemToPlayer done for " + item
                + " | armor contents -> helmet=" + armorContents[3]
                + ", chest=" + armorContents[2]
                + ", legs=" + armorContents[1]
                + ", boots=" + armorContents[0]);
    }

    private void cachePurchase(Player player, CashClashPlayer ccp, Purchasable item, int round, ItemStack replacedItem) {
        ccp.addPurchase(new PurchaseRecord(item, 1, item.getPrice(), replacedItem, round));
        // Note: runes are NOT auto-reapplied here - a rune only ever affects its manually
        // linked, currently active target (see RuneManager), regardless of what else is bought.

        Messages.send(player, "shop.purchased",
            "item_name", item.getDisplayName(),
            "price", String.format("%,d", item.getPrice()));
        SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
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
                transferEnchants(newWeapon, inv, bestSlot, existing);
                return oldItem;
            }
        }

        // No matching weapon found, just add to inventory
        inv.addItem(newWeapon);
        return null;
    }


    public void deductCoins(Player player, long cost) {
        CashClashPlayer ccp = getCashClashPlayer(player);
        if (ccp != null) {
            ccp.deductCoins(cost);
            Messages.debug(player, Messages.DebugCategory.SHOP, "Coins deducted: -" + cost + " (new balance=" + ccp.getCoins() + ")");
            Messages.send(player, "shop.purchase-successful",
                "cost", String.format("%,d", cost));
            SoundUtils.play(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
    }

    private CashClashPlayer getCashClashPlayer(Player player) {
        GameSession session = GameManager.getInstance().getPlayerSession(player);
        return session != null ? session.getCashClashPlayer(player.getUniqueId()) : null;
    }
}
