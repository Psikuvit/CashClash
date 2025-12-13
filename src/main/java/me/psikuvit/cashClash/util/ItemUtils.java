package me.psikuvit.cashClash.util;

import me.psikuvit.cashClash.manager.GameManager;
import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.ShopItem;
import me.psikuvit.cashClash.items.CustomArmor;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Utility methods that centralize ItemStack creation/modification logic.
 */
public final class ItemUtils {

    private ItemUtils() {
        throw new AssertionError("Nope.");
    }


    /**
     * Equip armor or replace existing armor piece.
     * @return The old armor piece that was replaced (null if slot was empty)
     */
    public static ItemStack equipArmorOrReplace(Player player, ItemStack newArmor) {
        if (player == null || newArmor == null) return null;
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

        if (old == null) return null;
        ItemMeta fromMeta = old.getItemMeta();
        ItemMeta toMeta = newArmor.getItemMeta();

        for (var e : fromMeta.getEnchants().entrySet()) {
            toMeta.addEnchant(e.getKey(), e.getValue(), true);
        }
        newArmor.setItemMeta(toMeta);
        return old;
    }


    /**
     * Replace the best matching tool/weapon in inventory with the new item.
     * @return The old item that was replaced (null if nothing was replaced)
     */
    public static ItemStack replaceBestMatchingTool(Player player, ItemStack newItem) {
        if (player == null || newItem == null) return null;
        PlayerInventory inv = player.getInventory();

        int bestSlot = ItemSelectionUtils.findBestMatchingToolSlot(inv, newItem.getType());

        if (bestSlot != -1) {
            ItemStack best = inv.getItem(bestSlot);
            if (best == null) return null;

            ItemStack oldItem = best.clone(); // Save the old item before replacing

            ItemMeta oldMeta = best.getItemMeta();
            ItemMeta newMeta = newItem.getItemMeta();

            if (oldMeta != null && newMeta != null) {
                for (var e : oldMeta.getEnchants().entrySet()) {
                    newMeta.addEnchant(e.getKey(), e.getValue(), true);
                }
                newItem.setItemMeta(newMeta);
            }

            inv.setItem(bestSlot, newItem);
            return oldItem;
        } else {
            inv.addItem(newItem);
            return null;
        }
    }


    public static ItemStack createTaggedItem(ShopItem si) {
        if (si == null) return null;
        ItemStack it = new ItemStack(si.getMaterial(), 1);
        var meta = it.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(Keys.SHOP_BOUGHT_KEY, PersistentDataType.STRING, si.name());
            meta.displayName(Messages.parse("<yellow>" + si.name().replace('_', ' ') + "</yellow>"));
            String desc = si.getDescription();

            if (!desc.isEmpty()) meta.lore(Messages.wrapLines(desc));

            String matName = it.getType().name();
            if (matName.endsWith("HELMET") || matName.endsWith("CHESTPLATE") || matName.endsWith("LEGGINGS") || matName.endsWith("BOOTS")) {
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    public static void giveCustomArmorSet(Player player, CustomArmor... armorPieces) {
        if (player == null || armorPieces == null) return;

        for (CustomArmor armor : armorPieces) {
            ItemStack item = new ItemStack(armor.getMaterial());
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                meta.getPersistentDataContainer().set(Keys.SHOP_BOUGHT_KEY, PersistentDataType.STRING, armor.name());
                meta.displayName(Messages.parse("<gold>" + armor.getDisplayName() + "</gold>"));

                List<Component> wrappedLore = Messages.wrapLines("<gray>" + armor.getLore() + "</gray>");
                wrappedLore.add(Component.empty());
                wrappedLore.add(Messages.parse("<yellow>Special Armor</yellow>"));
                meta.lore(wrappedLore);
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
                item.setItemMeta(meta);
            }

            equipArmorOrReplace(player, item);
        }
    }


    public static void applyEnchantToBestItem(Player player, EnchantEntry ee, int lvl) {
        if (player == null || ee == null) return;
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
}
