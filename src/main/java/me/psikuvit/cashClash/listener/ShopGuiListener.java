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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import me.psikuvit.cashClash.util.Messages;
import me.psikuvit.cashClash.util.SoundUtils;
import me.psikuvit.cashClash.items.CustomArmor;
import org.bukkit.Sound;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
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

        if (event.getSlot() == 8) {
            player.closeInventory();
        }
    }

    private void handleCategoryItems(InventoryClickEvent event, Player player, ShopHolder sh) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        var itemMeta = clicked.getItemMeta();
        Component itemComp = itemMeta.displayName();

        if (event.getSlot() == 45) {
            player.closeInventory();
            return;
        }

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
            if (!expectedComp.equals(itemComp)) continue;

            // validate session and player data
            GameSession sess = GameManager.getInstance().getPlayerSession(player);
            if (sess == null) {
                Messages.send(player, "<red>You must be in a game to shop.</red>");
                player.closeInventory();
                return;
            }
            CashClashPlayer ccp = sess.getCashClashPlayer(player.getUniqueId());
            if (ccp == null) return;

            long price = si.getPrice();
            if (!canAffordAndDeduct(ccp, price, player)) return;

            // handle immediate special cases
            if (si == ShopItem.UPGRADE_TO_NETHERITE) {
                handleUpgradeToNetherite(player, ccp, si, price);
                return;
            }

            // build the item that will be given
            ItemStack given = createTaggedItem(si);

            // special set purchases
            if (si == ShopItem.DEATHMAULER_OUTFIT || si == ShopItem.DRAGON_SET) {
                giveSpecialSet(player, si, ccp, price);
                return;
            }

            // diamond prereq
            if (!ensureDiamondPrerequisite(si, player, ccp)) return;

            // give item (equip or add)
            giveItemToPlayer(player, si, given, ccp);
            return;
        }
    }

    private boolean canAffordAndDeduct(CashClashPlayer ccp, long price, Player player) {
        if (!ccp.canAfford(price)) {
            Messages.send(player, "<red>Not enough coins to buy (cost: $" + price + ")</red>");
            return false;
        }
        ccp.deductCoins(price);
        return true;
    }

    private void handleUpgradeToNetherite(Player player, CashClashPlayer ccp, ShopItem si, long price) {
        boolean upgraded = upgradeBestDiamondToNetherite(player);
        if (upgraded) {
            ccp.addPurchase(new PurchaseRecord(si, 1, price));
            Messages.send(player, "<green>Upgraded your best diamond item to netherite.</green>");
            SoundUtils.play(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        } else {
            Messages.send(player, "<yellow>No eligible diamond item found to upgrade.</yellow>");
            ccp.addCoins(price);
        }
    }

    private ItemStack createTaggedItem(ShopItem si) {
        ItemStack it = new ItemStack(si.getMaterial(), 1);
        var meta = it.getItemMeta();
        if (meta != null) {
            NamespacedKey key = new NamespacedKey(CashClashPlugin.getInstance(), "shop_bought");

            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, si.name());
            meta.displayName(Messages.parse("<yellow>" + si.name().replace('_', ' ') + "</yellow>"));

            String desc = si.getDescription();
            if (!desc.isEmpty()) meta.lore(List.of(Messages.parse(desc)));
            it.setItemMeta(meta);
        }
        return it;
    }

    private void giveSpecialSet(Player player, ShopItem si, CashClashPlayer ccp, long price) {
        if (si == ShopItem.DEATHMAULER_OUTFIT) giveCustomArmorSet(player, CustomArmor.DEATHMAULER_OUTFIT);
        else if (si == ShopItem.DRAGON_SET) giveCustomArmorSet(player, CustomArmor.DRAGON_SET);

        ccp.addPurchase(new PurchaseRecord(si, 1, price));
        Messages.send(player, "<green>Purchased " + si.name().replace('_', ' ') + " for $" + price + "</green>");
        SoundUtils.play(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    private boolean ensureDiamondPrerequisite(ShopItem si, Player player, CashClashPlayer ccp) {
        if (!si.getMaterial().name().contains("DIAMOND")) return true;
        String ironName = si.getMaterial().name().replace("DIAMOND", "IRON");

        boolean hasIron = false;
        for (ItemStack is : player.getInventory().getContents()) {
            if (is == null) continue;
            if (is.getType().name().equals(ironName)) { hasIron = true; break; }
        }

        if (!hasIron) {
            Messages.send(player, "<red>You must own the iron equivalent before buying diamond " + si.name().replace('_', ' ') + "</red>");
            ccp.addCoins(si.getPrice());
        }

        return hasIron;
    }

    private void giveItemToPlayer(Player player, ShopItem si, ItemStack item, CashClashPlayer ccp) {
        if (si.getCategory() == ShopCategory.ARMOR) {
            equipArmorOrReplace(player, item);
        } else if (isToolOrWeapon(si.getMaterial())) {
            replaceBestMatchingTool(player, item);
        } else {
            player.getInventory().addItem(item);
        }

        ccp.addPurchase(new PurchaseRecord(si, 1, si.getPrice()));
        // apply owned enchants centrally
        applyOwnedEnchantsAfterPurchase(player, si);
        Messages.send(player, "<green>Purchased " + si.name().replace('_', ' ') + " for $" + si.getPrice() + "</green>");
        SoundUtils.play(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    private void giveCustomArmorSet(Player player, CustomArmor setType) {
        NamespacedKey key = new NamespacedKey(CashClashPlugin.getInstance(), "shop_bought");
        if (setType == CustomArmor.DEATHMAULER_OUTFIT) {
            ItemStack chest = new ItemStack(Material.NETHERITE_CHESTPLATE);
            ItemStack legs = new ItemStack(Material.NETHERITE_LEGGINGS);

            var cm = chest.getItemMeta();
            var lm = legs.getItemMeta();

            if (cm != null) {
                cm.getPersistentDataContainer().set(key, PersistentDataType.STRING, setType.name());
                cm.displayName(Messages.parse("<gold>Deathmauler Chestplate</gold>"));
                chest.setItemMeta(cm);
            }
            if (lm != null) {
                lm.getPersistentDataContainer().set(key, PersistentDataType.STRING, setType.name());
                lm.displayName(Messages.parse("<gold>Deathmauler Leggings</gold>"));
                legs.setItemMeta(lm);
            }

            equipArmorOrReplace(player, chest);
            equipArmorOrReplace(player, legs);
        } else if (setType == CustomArmor.DRAGON_SET) {
            ItemStack chest = new ItemStack(Material.DIAMOND_CHESTPLATE);
            ItemStack boots = new ItemStack(Material.DIAMOND_BOOTS);
            ItemStack helm = new ItemStack(Material.DIAMOND_HELMET);
            var cm = chest.getItemMeta(); var bm = boots.getItemMeta(); var hm = helm.getItemMeta();
            if (cm != null) {
                cm.getPersistentDataContainer().set(key, PersistentDataType.STRING, setType.name());
                cm.displayName(Messages.parse("<dark_purple>Dragon Chestplate</dark_purple>"));
                chest.setItemMeta(cm);
            }

            if (bm != null) {
                bm.getPersistentDataContainer().set(key, PersistentDataType.STRING, setType.name());
                bm.displayName(Messages.parse("<dark_purple>Dragon Boots</dark_purple>"));
                boots.setItemMeta(bm);
            }

            if (hm != null) {
                hm.getPersistentDataContainer().set(key, PersistentDataType.STRING, setType.name());
                hm.displayName(Messages.parse("<dark_purple>Dragon Helmet</dark_purple>"));
                helm.setItemMeta(hm);
            }

            equipArmorOrReplace(player, chest);
            equipArmorOrReplace(player, helm);
            equipArmorOrReplace(player, boots);
        }
    }

    private String getDescriptionForShopItem(ShopItem si) {
        return switch (si) {
            case INVESTORS_BOOTS, INVESTORS_HELMET, INVESTORS_LEGGINGS, INVESTORS_CHESTPLATE ->
                    "<gray>Part of the Investor's set. Each piece increases money bonuses.</gray>";
            case DEATHMAULER_OUTFIT -> "<gray>Set: Kills heal you and grant absorption. (Chest+Leggings)</gray>";
            case DRAGON_SET -> "<gray>Set: Grants regens and double-jump. (Chest+Boots+Helmet)</gray>";
            default -> "";
        };
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
        // Special-case: Protection should apply to all armor pieces
        if (ee == EnchantEntry.PROTECTION) {
            boolean appliedAny = false;
            PlayerInventory inv = player.getInventory();
            ItemStack[] armor = inv.getArmorContents();

            for (ItemStack is : armor) {
                if (is == null) continue;
                if (!ee.getApplicableMaterials().contains(is.getType())) continue;
                var meta = is.getItemMeta();
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
            return;
        }

        // Default behavior: apply to best matching single item
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
            // Apply each owned enchant to applicable inventory items
            player.getInventory().forEach(is -> {
                if (is == null) return;
                if (!ee.getApplicableMaterials().contains(is.getType())) return;
                var meta = is.getItemMeta();
                if (meta == null) return;
                meta.addEnchant(ee.getEnchantment(), lvl, true);
                is.setItemMeta(meta);
            });
        }
    }

    private int rankMaterial(Material m) {
        if (m == null) return 0;
        String name = m.name();
        if (name.contains("NETHERITE")) return 5;
        if (name.contains("DIAMOND")) return 4;
        if (name.contains("IRON")) return 3;
        if (name.contains("STONE")) return 2;
        return 1;
    }
}
