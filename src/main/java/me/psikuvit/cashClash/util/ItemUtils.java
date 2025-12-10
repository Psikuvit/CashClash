package me.psikuvit.cashClash.util;

import me.psikuvit.cashClash.CashClashPlugin;
import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.ShopItem;
import me.psikuvit.cashClash.items.CustomArmor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Utility methods that centralize ItemStack creation/modification logic.
 */
public final class ItemUtils {

    private ItemUtils() {
        throw new AssertionError("Nope.");
    }


    public static void equipArmorOrReplace(Player player, ItemStack newArmor) {
        if (player == null || newArmor == null) return;
        PlayerInventory inv = player.getInventory();
        Material m = newArmor.getType();

        ItemStack old;
        if (m.name().endsWith("HELMET")) {
            old = inv.getHelmet();
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
            inv.addItem(newArmor);
        }
    }

    public static void transferEnchants(ItemStack from, ItemStack to) {
        if (from == null || to == null) return;
        ItemMeta fromMeta = from.getItemMeta();
        ItemMeta toMeta = to.getItemMeta();

        if (fromMeta == null || toMeta == null) return;
        for (var e : fromMeta.getEnchants().entrySet()) {
            toMeta.addEnchant(e.getKey(), e.getValue(), true);
        }
        to.setItemMeta(toMeta);
    }

    public static void replaceBestMatchingTool(Player player, ItemStack newItem) {
        if (player == null || newItem == null) return;
        PlayerInventory inv = player.getInventory();

        int bestSlot = ItemSelectionUtils.findBestMatchingToolSlot(inv, newItem.getType());

        if (bestSlot != -1) {
            ItemStack best = inv.getItem(bestSlot);
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


    public static boolean upgradeBestDiamondToNetherite(Player player) {
        if (player == null) return false;
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

            if (ItemSelectionUtils.rankMaterial(is.getType()) > ItemSelectionUtils.rankMaterial(best.getType())) {
                best = is;
                bestSlot = i;
            }
        }

        if (best == null) return false;

        Material target = mapDiamondToNetherite(best.getType());
        if (target == null) return false;

        ItemStack newItem = new ItemStack(target, best.getAmount());

        ItemMeta newMeta = newItem.getItemMeta();
        ItemMeta oldMeta = best.getItemMeta();

        if (oldMeta != null && newMeta != null) {
            for (var e : oldMeta.getEnchants().entrySet()) {
                newMeta.addEnchant(e.getKey(), e.getValue(), true);
            }
            newItem.setItemMeta(newMeta);
        }

        inv.setItem(bestSlot, newItem);
        return true;
    }

    public static Material mapDiamondToNetherite(Material m) {
        if (m == null) return null;
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

    public static ItemStack createTaggedItem(ShopItem si) {
        if (si == null) return null;
        ItemStack it = new ItemStack(si.getMaterial(), 1);
        var meta = it.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(Keys.SHOP_BOUGHT_KEY, PersistentDataType.STRING, si.name());
            meta.displayName(Messages.parse("<yellow>" + si.name().replace('_', ' ') + "</yellow>"));
            String desc = si.getDescription();

            if (!desc.isEmpty()) meta.lore(List.of(Messages.parse(desc)));
            it.setItemMeta(meta);
        }
        return it;
    }

    public static void giveCustomArmorSet(Player player, CustomArmor setType) {
        if (player == null || setType == null) return;
        if (setType == CustomArmor.DEATHMAULER_OUTFIT) {
            ItemStack chest = new ItemStack(Material.NETHERITE_CHESTPLATE);
            ItemStack legs = new ItemStack(Material.NETHERITE_LEGGINGS);

            ItemMeta cm = chest.getItemMeta();
            ItemMeta lm = legs.getItemMeta();

            if (cm != null) {
                cm.getPersistentDataContainer().set(Keys.SHOP_BOUGHT_KEY, PersistentDataType.STRING, setType.name());
                cm.displayName(Messages.parse("<gold>Deathmauler Chestplate</gold>"));
                chest.setItemMeta(cm);
            }
            if (lm != null) {
                lm.getPersistentDataContainer().set(Keys.SHOP_BOUGHT_KEY, PersistentDataType.STRING, setType.name());
                lm.displayName(Messages.parse("<gold>Deathmauler Leggings</gold>"));
                legs.setItemMeta(lm);
            }

            equipArmorOrReplace(player, chest);
            equipArmorOrReplace(player, legs);
        } else if (setType == CustomArmor.DRAGON_SET) {
            ItemStack chest = new ItemStack(Material.DIAMOND_CHESTPLATE);
            ItemStack boots = new ItemStack(Material.DIAMOND_BOOTS);
            ItemStack helm = new ItemStack(Material.DIAMOND_HELMET);

            ItemMeta cm = chest.getItemMeta();
            ItemMeta bm = boots.getItemMeta();
            ItemMeta hm = helm.getItemMeta();
            if (cm != null) {
                cm.getPersistentDataContainer().set(Keys.SHOP_BOUGHT_KEY, PersistentDataType.STRING, setType.name());
                cm.displayName(Messages.parse("<dark_purple>Dragon Chestplate</dark_purple>"));
                chest.setItemMeta(cm);
            }

            if (bm != null) {
                bm.getPersistentDataContainer().set(Keys.SHOP_BOUGHT_KEY, PersistentDataType.STRING, setType.name());
                bm.displayName(Messages.parse("<dark_purple>Dragon Boots</dark_purple>"));
                boots.setItemMeta(bm);
            }

            if (hm != null) {
                hm.getPersistentDataContainer().set(Keys.SHOP_BOUGHT_KEY, PersistentDataType.STRING, setType.name());
                hm.displayName(Messages.parse("<dark_purple>Dragon Helmet</dark_purple>"));
                helm.setItemMeta(hm);
            }

            equipArmorOrReplace(player, chest);
            equipArmorOrReplace(player, helm);
            equipArmorOrReplace(player, boots);
        }
    }


    public static void applyEnchantToBestItem(Player player, EnchantEntry ee, int lvl) {
        if (player == null || ee == null) return;
        // Special-case: Protection should apply to all armor pieces
        if (ee == EnchantEntry.PROTECTION) {
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
            return;
        }

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

            if (ItemSelectionUtils.rankMaterial(is.getType()) > ItemSelectionUtils.rankMaterial(best.getType())) {
                best = is;
                bestSlot = i;
            }
        }
        if (best != null) {
            ItemMeta meta = best.getItemMeta();
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

    public static void applyOwnedEnchantsAfterPurchase(Player player, ShopItem si) {
        if (player == null || si == null) return;
        if (si.getCategory() == ShopCategory.UTILITY && (si.getMaterial() == Material.BOW || si.getMaterial() == Material.CROSSBOW)) return;

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
}
