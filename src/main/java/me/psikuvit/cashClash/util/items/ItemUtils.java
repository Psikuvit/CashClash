package me.psikuvit.cashClash.util.items;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.FoodProperties;
import me.psikuvit.cashClash.manager.game.GameManager;
import me.psikuvit.cashClash.shop.EnchantEntry;
import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.shop.items.FoodItem;
import me.psikuvit.cashClash.shop.items.Purchasable;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

        for (var e : fromMeta.getEnchants().entrySet()) {
            toMeta.addEnchant(e.getKey(), e.getValue(), true);
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


    public static ItemStack createTaggedItem(Purchasable si) {
        if (si == null) return null;
        ItemStack it = new ItemStack(si.getMaterial(), 1);
        var meta = it.getItemMeta();

        if (meta != null) {
            meta.getPersistentDataContainer().set(Keys.ITEM_ID, PersistentDataType.STRING, si.name());
            if (!si.getDescription().isEmpty()) {
                meta.displayName(Messages.parse("<yellow>" + si.getDisplayName() + "</yellow>"));
                meta.lore(Messages.wrapLines(si.getDescription()));
            }
            it.setItemMeta(meta);

            if (si instanceof FoodItem food) {
                if (it.getType() == Material.BREAD || it.getType() == Material.COOKED_BEEF) return it;
                FoodProperties existing = it.getData(DataComponentTypes.FOOD);
                Messages.debug(String.valueOf(existing));
                if (existing != null) {
                    FoodProperties.Builder builder = existing.toBuilder();
                    builder.canAlwaysEat(true);
                    it.setData(DataComponentTypes.FOOD, builder.build());
                    Messages.debug("Updated existing food component for " + si.getDisplayName());
                } else {
                    // Item doesn't have food component by default, create one
                    it.setData(DataComponentTypes.FOOD, FoodProperties.food()
                            .canAlwaysEat(true)
                            .nutrition(4)
                            .saturation(2.0f)
                            .build());
                    Messages.debug("Created new food component for " + si.getDisplayName());
                }
                Messages.debug(String.valueOf(it.getData(DataComponentTypes.FOOD)));

                // Apply custom model data for food items with custom textures
                CustomModelDataMapper.applyCustomModel(it, food);
            } else {
                String matName = it.getType().name();
                if (matName.endsWith("HELMET") || matName.endsWith("CHESTPLATE") || matName.endsWith("LEGGINGS") || matName.endsWith("BOOTS")) {
                    meta.setUnbreakable(true);
                    meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
                }
                it.setItemMeta(meta);
            }
        }
        return it;
    }

    public static ItemStack createCustomItem(CustomItem type, Player owner) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Messages.parse("<yellow>" + type.getDisplayName() + "</yellow>"));

        List<Component> lore = new ArrayList<>(Messages.wrapLines(type.getDescription()));
        meta.lore(lore);

        // Add PDC tags
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.ITEM_ID, PersistentDataType.STRING, type.name());
        pdc.set(Keys.ITEM_OWNER, PersistentDataType.STRING, owner.getUniqueId().toString());

        // Special handling for specific items
        switch (type) {
            case BAG_OF_POTATOES -> {
                if (meta instanceof Damageable damageable) {
                    damageable.setDamage(item.getType().getMaxDurability() - 3);
                }
                meta.addEnchant(Enchantment.KNOCKBACK, 3, true);
            }
            case CASH_BLASTER -> meta.addEnchant(Enchantment.MULTISHOT, 1, true);
            case INVIS_CLOAK -> pdc.set(Keys.ITEM_USES, PersistentDataType.INTEGER, 5);
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);

        // Apply custom model data using string key for resource pack
        CustomModelDataMapper.applyCustomModel(item, type);

        return item;
    }

    public static void giveCustomArmorSet(Player player, CustomArmorItem armor) {
        if (player == null || armor == null) return;

        ItemStack item = new ItemStack(armor.getMaterial());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.getPersistentDataContainer().set(Keys.ITEM_ID, PersistentDataType.STRING, armor.name());
            meta.displayName(Messages.parse("<gold>" + armor.getDisplayName() + "</gold>"));

            List<Component> wrappedLore = Messages.wrapLines("<gray>" + armor.getLore() + "</gray>");
            wrappedLore.add(Component.empty());
            wrappedLore.add(Messages.parse("<yellow>Special Armor</yellow>"));

            meta.lore(wrappedLore);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
            
            item.setItemMeta(meta);

            // Apply custom model data using string key for resource pack
            String modelKey = CustomModelDataMapper.getItemKey(armor);
            if (modelKey != null) {
                CustomModelDataMapper.applyStringModelData(item, modelKey);
            }
        }

        equipArmorOrReplace(player, item);
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
