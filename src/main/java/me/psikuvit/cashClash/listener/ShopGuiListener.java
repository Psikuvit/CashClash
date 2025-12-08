package me.psikuvit.cashClash.listener;

import me.psikuvit.cashClash.gui.ShopGUI;
import me.psikuvit.cashClash.gui.ShopHolder;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.player.CashClashPlayer;
import me.psikuvit.cashClash.player.PurchaseRecord;
import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.ShopItem;
import me.psikuvit.cashClash.game.GameSession;
import me.psikuvit.cashClash.CashClashPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import me.psikuvit.cashClash.util.Messages;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.PlayerInventory;
import java.util.Map;

/**
 * Listener dedicated to shop GUI interactions.
 */
public class ShopGuiListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof ShopHolder sh)) return;

        String type = sh.type();
        if (type == null) return;

        if (type.startsWith("categories") || type.startsWith("category:")) {
            event.setCancelled(true);
        }

        if (type.startsWith("categories")) {
            handleShopCategories(event, player); // removed unused 'sh' param
            return;
        }

        if (type.startsWith("category:")) {
            handleCategoryItems(event, player, sh);
        }
    }

    private void handleShopCategories(InventoryClickEvent event, Player player) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        var meta = clicked.getItemMeta();
        Component nameComp = meta.displayName();

        for (ShopCategory c : ShopCategory.values()) {
            Component expected = Messages.parse("<yellow>" + c.getDisplayName() + "</yellow>");
            if (expected.equals(nameComp)) {
                ShopGUI.openCategoryItems(player, c);
                return;
            }
        }

        // cancel button at slot 8
        if (event.getSlot() == 8) {
            player.closeInventory();
        }
    }

    private void handleCategoryItems(InventoryClickEvent event, Player player, ShopHolder sh) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        var itemMeta = clicked.getItemMeta();
        Component itemComp = itemMeta.displayName();

        // Cancel button in items view
        if (event.getSlot() == 45) {
            player.closeInventory();
            return;
        }

        // Undo purchase
        if (event.getSlot() == 49) {
            handleUndoPurchase(player);
            return;
        }

        String type = sh.type();
        if (type != null && type.startsWith("category:")) {
            String cat = type.substring("category:".length());
            if (cat.equalsIgnoreCase(ShopCategory.ENCHANTS.getDisplayName())) {
                if (handleEnchantPurchase(player, itemComp)) return;
            }
        }

        // Regular shop items
        handleShopItemClick(player, itemComp);
    }

    private void handleUndoPurchase(Player player) {
        GameSession sess = GameManager.getInstance().getPlayerSession(player);
        if (sess == null) {
            Messages.send(player, "<red>You're not in a game.</red>");
            player.closeInventory();
            return;
        }

        CashClashPlayer ccp = sess.getCashClashPlayer(player.getUniqueId());
        var rec = ccp.popLastPurchase();
        if (rec == null) {
            Messages.send(player, "<red>No purchase to undo.</red>");
            return;
        }

        // refund
        ccp.addCoins(rec.price());

        NamespacedKey key = new NamespacedKey(CashClashPlugin.getInstance(), "shop_bought");
        boolean removed = false;

        for (ItemStack is : player.getInventory().getContents()) {
            if (is == null) continue;

            var m = is.getItemMeta();
            if (m == null) continue;

            var val = m.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (val != null && val.equals(rec.item().name())) {
                int qty = is.getAmount();
                if (qty > 1) is.setAmount(qty - 1);
                else player.getInventory().remove(is);
                removed = true;
                break;
            }
        }
        Messages.send(player, "<green>Purchase undone. Refunded $" + rec.price() + (removed ? "" : " (could not find item to remove)") + "</green>");
    }

    private boolean handleEnchantPurchase(Player player, Component itemComp) {
        for (EnchantEntry ee : EnchantEntry.values()) {
            for (int lvl = 1; lvl <= ee.getMaxLevel(); lvl++) {
                Component expected = Messages.parse("<yellow>" + ee.getDisplayName() + " " + lvl + "</yellow>");
                if (expected.equals(itemComp)) {

                    GameSession sess = GameManager.getInstance().getPlayerSession(player);
                    if (sess == null) {
                        Messages.send(player, "<red>You must be in a game to shop.</red>");
                        player.closeInventory();
                        return true;
                    }

                    CashClashPlayer ccp = sess.getCashClashPlayer(player.getUniqueId());
                    if (ccp == null) return true;

                    long price = ee.getPriceForLevel(lvl);
                    if (!ccp.canAfford(price)) {
                        Messages.send(player, "<red>Not enough coins to buy enchant.</red>");
                        return true;
                    }

                    ccp.deductCoins(price);
                    ccp.addOwnedEnchant(ee, lvl);

                    applyEnchantToBestItem(player, ee, lvl);
                    Messages.send(player, "<green>Purchased enchant " + ee.getDisplayName() + " " + lvl + " for $" + price + "</green>");
                    return true;
                }
            }
        }
        return false;
    }

    private void handleShopItemClick(Player player, Component itemComp) {
        for (ShopItem si : ShopItem.values()) {
            Component expectedComp = Messages.parse("<yellow>" + si.name().replace('_', ' ') + "</yellow>");
            if (expectedComp.equals(itemComp)) {

                GameSession sess = GameManager.getInstance().getPlayerSession(player);
                if (sess == null) {
                    Messages.send(player, "<red>You must be in a game to shop.</red>");
                    player.closeInventory();
                    return;
                }

                CashClashPlayer ccp = sess.getCashClashPlayer(player.getUniqueId());
                if (ccp == null) return;

                long price = si.getPrice();
                if (!ccp.canAfford(price)) {
                    Messages.send(player, "<red>Not enough coins to buy " + si.name() + " ($" + price + ")</red>");
                    return;
                }
                ccp.deductCoins(price);

                // Special handling: upgrade to netherite
                if (si == ShopItem.UPGRADE_TO_NETHERITE) {
                    boolean upgraded = upgradeBestDiamondToNetherite(player);
                    if (upgraded) {
                        ccp.addPurchase(new PurchaseRecord(si, 1, price));
                        Messages.send(player, "<green>Upgraded your best diamond item to netherite.</green>");
                    } else {
                        Messages.send(player, "<yellow>No eligible diamond item found to upgrade.</yellow>");
                        // refund if nothing to upgrade
                        ccp.addCoins(price);
                    }
                    return;
                }

                ItemStack given = new ItemStack(si.getMaterial(), 1);
                var meta = given.getItemMeta();

                NamespacedKey key = new NamespacedKey(CashClashPlugin.getInstance(), "shop_bought");
                if (meta != null) meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, si.name());

                if (meta != null) given.setItemMeta(meta);

                // For armor: auto-equip
                if (si.getCategory() == ShopCategory.ARMOR) {
                    equipArmorOrReplace(player, given);
                } else if (isToolOrWeapon(si.getMaterial())) {
                    replaceBestMatchingTool(player, given);
                } else {
                    // default: add to inventory (stacks etc.)
                    player.getInventory().addItem(given);
                }

                ccp.addPurchase(new PurchaseRecord(si, 1, price));

                applyOwnedEnchantsAfterPurchase(player, si);

                Messages.send(player, "<green>Purchased " + si.name() + " for $" + price + "</green>");
                return;
            }
        }
    }

    // --- new helper methods ---
    private boolean isToolOrWeapon(Material m) {
        String name = m.name();
        return name.endsWith("SWORD") || name.endsWith("AXE") || name.endsWith("PICKAXE") || name.endsWith("SHOVEL")
                || name.endsWith("HOE") || name.equals("BOW") || name.equals("CROSSBOW") || name.equals("FISHING_ROD") || name.equals("MACE");
    }

    private void equipArmorOrReplace(Player player, ItemStack newArmor) {
        PlayerInventory inv = player.getInventory();
        Material m = newArmor.getType();

        ItemStack old;
        if (m.name().endsWith("HELMET")) {
            old = inv.getHelmet();
            // transfer enchants from old to new if present
            transferEnchants(old, newArmor);
            inv.setHelmet(newArmor);
        } else if (m.name().endsWith("CHESTPLATE")) {
            old = inv.getChestplate();
            transferEnchants(old, newArmor);
            inv.setChestplate(newArmor);
        } else if (m.name().endsWith("LEGGINGS")) {
            old = inv.getLeggings();
            transferEnchants(old, newArmor);
            inv.setLeggings(newArmor);
        } else if (m.name().endsWith("BOOTS")) {
            old = inv.getBoots();
            transferEnchants(old, newArmor);
            inv.setBoots(newArmor);
        } else {
            // fallback: add to inventory
            inv.addItem(newArmor);
            return;
        }

        if (old != null && old.getType() != Material.AIR) {
            // try to return old item to inventory, otherwise drop it
            Map<Integer, ItemStack> leftover = inv.addItem(old);
            if (!leftover.isEmpty()) player.getWorld().dropItemNaturally(player.getLocation(), old);
        }
    }

    private void transferEnchants(ItemStack from, ItemStack to) {
        if (from == null || to == null) return;
        var fromMeta = from.getItemMeta();
        var toMeta = to.getItemMeta();
        if (fromMeta == null || toMeta == null) return;
        for (var e : fromMeta.getEnchants().entrySet()) {
            toMeta.addEnchant(e.getKey(), e.getValue(), true);
        }
        to.setItemMeta(toMeta);
    }

    private void replaceBestMatchingTool(Player player, ItemStack newItem) {
        PlayerInventory inv = player.getInventory();
        ItemStack best = null;
        int bestSlot = -1;

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack is = inv.getItem(i);
            if (is == null) continue;
            if (!isToolOrWeapon(is.getType())) continue;

            // same category (sword vs sword) or any tool? We'll match by suffix
            if (!sameCategory(is.getType(), newItem.getType())) continue;

            if (best == null) {
                best = is; bestSlot = i; continue;
            }
            if (rankMaterial(is.getType()) > rankMaterial(best.getType())) {
                best = is; bestSlot = i;
            }
        }

        if (best != null) {
            // transfer enchants from old to new
            var oldMeta = best.getItemMeta();
            var newMeta = newItem.getItemMeta();
            if (oldMeta != null && newMeta != null) {
                for (var e : oldMeta.getEnchants().entrySet()) {
                    newMeta.addEnchant(e.getKey(), e.getValue(), true);
                }
                newItem.setItemMeta(newMeta);
            }
            // replace slot
            inv.setItem(bestSlot, newItem);
        } else {
            // nothing to replace, add normally
            inv.addItem(newItem);
        }
    }

    private boolean sameCategory(Material a, Material b) {
        String sa = a.name();
        String sb = b.name();
        if (sa.endsWith("SWORD") && sb.endsWith("SWORD")) return true;
        if (sa.endsWith("AXE") && sb.endsWith("AXE")) return true;
        if (sa.endsWith("PICKAXE") && sb.endsWith("PICKAXE")) return true;
        if (sa.endsWith("SHOVEL") && sb.endsWith("SHOVEL")) return true;
        if (sa.equals("BOW") && sb.equals("BOW")) return true;
        if (sa.equals("CROSSBOW") && sb.equals("CROSSBOW")) return true;
        if (sa.equals("FISHING_ROD") && sb.equals("FISHING_ROD")) return true;
        return sa.equals("MACE") && sb.equals("MACE");
    }

    private boolean upgradeBestDiamondToNetherite(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack best = null;
        int bestSlot = -1;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack is = inv.getItem(i);
            if (is == null) continue;
            if (!is.getType().name().contains("DIAMOND")) continue;
            if (best == null) { best = is; bestSlot = i; continue; }
            if (rankMaterial(is.getType()) > rankMaterial(best.getType())) { best = is; bestSlot = i; }
        }
        if (best == null) return false;

        Material target = mapDiamondToNetherite(best.getType());
        if (target == null) return false;

        ItemStack newItem = new ItemStack(target, best.getAmount());
        var newMeta = newItem.getItemMeta();
        var oldMeta = best.getItemMeta();
        if (oldMeta != null && newMeta != null) {
            for (var e : oldMeta.getEnchants().entrySet()) newMeta.addEnchant(e.getKey(), e.getValue(), true);
            newItem.setItemMeta(newMeta);
        }

        inv.setItem(bestSlot, newItem);
        return true;
    }

    private Material mapDiamondToNetherite(Material m) {
        return switch (m) {
            case DIAMOND_SWORD -> Material.NETHERITE_SWORD;
            case DIAMOND_AXE -> Material.NETHERITE_AXE;
            case DIAMOND_PICKAXE -> Material.NETHERITE_PICKAXE;
            case DIAMOND_SHOVEL -> Material.NETHERITE_SHOVEL;
            case DIAMOND_HELMET -> Material.NETHERITE_HELMET;
            case DIAMOND_CHESTPLATE -> Material.NETHERITE_CHESTPLATE;
            case DIAMOND_LEGGINGS -> Material.NETHERITE_LEGGINGS;
            case DIAMOND_BOOTS -> Material.NETHERITE_BOOTS;
            default -> null;
        };
    }

    private void applyEnchantToBestItem(Player player, EnchantEntry ee, int lvl) {
        ItemStack best = null;
        int bestSlot = -1;
        int eligibleCount = 0;

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack is = player.getInventory().getItem(i);

            if (is == null) continue;
            if (!ee.getApplicableMaterials().contains(is.getType())) continue;
            eligibleCount++;

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
        if (best != null) {
            var meta = best.getItemMeta();
            if (meta != null) {
                meta.addEnchant(ee.getEnchantment(), lvl, true);
                best.setItemMeta(meta);
                player.getInventory().setItem(bestSlot, best);
                if (eligibleCount > 1) {
                    Messages.send(player, "<yellow>Multiple eligible items found; enchant auto-applied to the strongest matching item.</yellow>");
                }
            }
        } else {
            Messages.send(player, "<red>No eligible item found in your inventory to apply that enchant. It will be saved and applied to future purchases when appropriate.</red>");
        }
    }

    private void applyOwnedEnchantsAfterPurchase(Player player, ShopItem si) {
        if (si.getCategory() == ShopCategory.UTILITY && (si.getMaterial() == Material.BOW || si.getMaterial() == Material.CROSSBOW)) return;

        var session = GameManager.getInstance().getPlayerSession(player);
        if (session == null) return;

        var ccp = session.getCashClashPlayer(player.getUniqueId());
        if (ccp == null) return;

        for (var e : ccp.getOwnedEnchants().entrySet()) {
            EnchantEntry ee = e.getKey();
            int lvl = e.getValue();
            if (ee.getApplicableMaterials().contains(si.getMaterial())) {
                applyEnchantToBestItem(player, ee, lvl);
            }
        }
    }

    private int rankMaterial(Material m) {
        // basic ranking: Netherite(5), Diamond(4), Iron(3), Stone(2), Wood/others(1)
        if (m.name().contains("NETHERITE")) return 5;
        if (m.name().contains("DIAMOND")) return 4;
        if (m.name().contains("IRON")) return 3;
        if (m.name().contains("STONE")) return 2;
        return 1;
    }
}
