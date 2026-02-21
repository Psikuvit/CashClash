package me.psikuvit.cashClash.util.items;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.shop.items.FoodItem;
import me.psikuvit.cashClash.shop.items.Purchasable;
import me.psikuvit.cashClash.util.Keys;
import me.psikuvit.cashClash.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating gameplay items (items used in-game, not for GUI display).
 * Handles creation of tagged items, custom items, and custom armor.
 */
public final class GameplayItemFactory {
    
    /**
     * Creates a tagged item from a Purchasable.
     * The item will have proper PDC tags for tracking and refund purposes.
     * 
     * @param purchasable The purchasable item definition
     * @return The created ItemStack with proper tags, or null if purchasable is null
     */
    public ItemStack createTaggedItem(Purchasable purchasable) {
        if (purchasable == null) return null;
        
        ItemStack item = new ItemStack(purchasable.getMaterial(), 1);
        ItemMeta meta = item.getItemMeta();
        
        if (meta == null) return item;
        
        // Set PDC tag for item identification
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.ITEM_ID, PersistentDataType.STRING, purchasable.name());
        
        // Set display name and lore if description exists
        if (!purchasable.getDescription().isEmpty()) {
            meta.displayName(Messages.parse("<yellow>" + purchasable.getDisplayName() + "</yellow>"));
            meta.lore(Messages.wrapLines(purchasable.getDescription()));
        }
        
        // Handle special item types
        if (purchasable instanceof FoodItem foodItem) {
            // Apply armor properties is not needed for food
            // Set meta first, then apply food properties (which use DataComponentTypes directly on item)
            item.setItemMeta(meta);
            applyFoodProperties(item, foodItem);
            return item;
        } else {
            // Apply armor properties (unbreakable, hide flags)
            applyArmorProperties(meta, item.getType());
        }
        
        item.setItemMeta(meta);
        return item;
    }
    
    /**
     * Creates a custom item (grenades, bounce pads, etc.) with owner tracking.
     * 
     * @param customItem The custom item type
     * @param owner The player who owns this item
     * @return The created custom item with owner tag
     */
    public ItemStack createCustomItem(CustomItem customItem, Player owner) {
        if (customItem == null || owner == null) return null;
        
        ItemStack item = new ItemStack(customItem.getMaterial());
        ItemMeta meta = item.getItemMeta();
        
        if (meta == null) return item;
        
        // Set display name and lore
        meta.displayName(Messages.parse("<yellow>" + customItem.getDisplayName() + "</yellow>"));
        List<Component> lore = new ArrayList<>(Messages.wrapLines(customItem.getDescription()));
        meta.lore(lore);
        
        // Add PDC tags
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.ITEM_ID, PersistentDataType.STRING, customItem.name());
        pdc.set(Keys.ITEM_OWNER, PersistentDataType.STRING, owner.getUniqueId().toString());
        
        // Apply special properties based on item type
        applyCustomItemProperties(meta, customItem, item);
        
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        
        // Apply custom model data
        CustomModelDataMapper.applyCustomModel(item, customItem);
        
        return item;
    }
    
    /**
     * Creates and equips a custom armor piece for a player.
     * 
     * @param player The player to equip the armor to
     * @param armor The custom armor item
     */
    public void createAndEquipCustomArmor(Player player, CustomArmorItem armor) {
        if (player == null || armor == null) return;
        
        ItemStack item = new ItemStack(armor.getMaterial());
        ItemMeta meta = item.getItemMeta();
        
        if (meta == null) return;
        
        // Set PDC tag
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(Keys.ITEM_ID, PersistentDataType.STRING, armor.name());
        
        // Set display name and lore
        meta.displayName(Messages.parse("<gold>" + armor.getDisplayName() + "</gold>"));
        
        List<Component> wrappedLore = Messages.wrapLines("<gray>" + armor.getLore() + "</gray>");
        wrappedLore.add(Component.empty());
        wrappedLore.add(Messages.parse("<yellow>Special Armor</yellow>"));
        meta.lore(wrappedLore);
        
        // Make unbreakable and hide flags
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
        
        item.setItemMeta(meta);
        
        // Apply custom model data
        String modelKey = CustomModelDataMapper.getItemKey(armor);
        if (modelKey != null) {
            CustomModelDataMapper.applyStringModelData(item, modelKey);
        }
        
        // Equip the armor
        ItemUtils.equipArmorOrReplace(player, item);
    }
    
    // ==================== PRIVATE HELPER METHODS ====================
    
    /**
     * Applies food properties to a food item.
     */
    private void applyFoodProperties(ItemStack item, FoodItem foodItem) {
        Material material = item.getType();

        // Skip special handling for bread and cooked beef (they already have good food properties)
        if (material == Material.BREAD || material == Material.COOKED_BEEF) {
            return;
        }

        // Create new food component for items that don't have one by default
        item.setData(DataComponentTypes.FOOD, FoodProperties.food()
                .canAlwaysEat(true)
                .nutrition(4)
                .saturation(2.0f)
                .build());
        item.unsetData(DataComponentTypes.CONSUMABLE); // Remove default consumable behavior

        PotionEffect potionEffect;
        switch (foodItem) {
            case SPEED_CARROT -> potionEffect = new PotionEffect(PotionEffectType.SPEED, 20 * 20, 0);
            case GOLDEN_CHICKEN -> potionEffect = new PotionEffect(PotionEffectType.ABSORPTION, 20 * 20, 1);
            case COOKIE_OF_LIFE -> potionEffect = new PotionEffect(PotionEffectType.REGENERATION, 14 * 20, 0);
            case SUNSCREEN -> potionEffect = new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 30 * 20, 0);
            case CAN_OF_SPINACH -> potionEffect = new PotionEffect(PotionEffectType.STRENGTH, 15 * 20, 0);
            default -> throw new IllegalArgumentException("Invalid food item: " + foodItem);
        }
        item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                .animation(ItemUseAnimation.EAT)
                .addEffect(ConsumeEffect.applyStatusEffects(List.of(potionEffect), 1))
                .build()
        );


        // Apply custom model data for food items with custom textures
        CustomModelDataMapper.applyCustomModel(item, foodItem);
    }

    /**
     * Applies armor properties (unbreakable, hide flags) to armor items.
     */
    private void applyArmorProperties(ItemMeta meta, Material material) {
        String materialName = material.name();
        if (materialName.endsWith("HELMET") || 
            materialName.endsWith("CHESTPLATE") || 
            materialName.endsWith("LEGGINGS") || 
            materialName.endsWith("BOOTS")) {
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }
    }
    
    /**
     * Applies special properties to custom items based on their type.
     */
    private void applyCustomItemProperties(ItemMeta meta, CustomItem customItem, ItemStack item) {
        switch (customItem) {
            case BAG_OF_POTATOES -> {
                if (meta instanceof Damageable damageable) {
                    damageable.setDamage(item.getType().getMaxDurability() - 3);
                }
                meta.addEnchant(Enchantment.KNOCKBACK, 3, true);
            }
            case CASH_BLASTER -> meta.addEnchant(Enchantment.MULTISHOT, 1, true);
            case INVIS_CLOAK -> {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                pdc.set(Keys.ITEM_USES, PersistentDataType.INTEGER, 5);
            }
            default -> {
                // No special properties
            }
        }
    }
}
