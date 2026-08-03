package me.psikuvit.cashClash.util.items;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import me.psikuvit.cashClash.config.ItemsConfig;
import me.psikuvit.cashClash.shop.items.ArmorItem;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.shop.items.FoodItem;
import me.psikuvit.cashClash.shop.items.Purchasable;
import me.psikuvit.cashClash.shop.items.UtilityItem;
import me.psikuvit.cashClash.shop.items.WeaponItem;
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

    GameplayItemFactory() {
    }

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
        if (!item.hasItemMeta()) return item;

        PDCSetter tags = PDCSetter.of(item);

        // Set PDC tag for item identification
        tags.set(Keys.ITEM_ID, PersistentDataType.STRING, purchasable.name());

        // Set display name
        tags.meta().displayName(Messages.parse("<yellow>" + purchasable.getDisplayName() + "</yellow>"));

        // Try to get lore from configuration based on item category
        List<Component> lore = getConfiguredLore(purchasable);

        if (!lore.isEmpty()) {
            tags.meta().lore(lore);
        }

        // Handle special item types
        if (purchasable instanceof FoodItem foodItem) {
            // Apply armor properties is not needed for food
            // Set meta first, then apply food properties (which use DataComponentTypes directly on item)
            tags.apply();
            applyFoodProperties(item, foodItem);
            return item;
        } else {
            // Apply armor properties (unbreakable, hide flags)
            applyArmorProperties(tags.meta(), item.getType());
        }

        tags.apply();
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
        if (!item.hasItemMeta()) return item;

        PDCSetter tags = PDCSetter.of(item);

        // Set display name
        tags.meta().displayName(Messages.parse("<yellow>" + customItem.getDisplayName() + "</yellow>"));

        // Try to get lore from configuration first
        List<Component> lore = getConfiguredLore(customItem);

        if (!lore.isEmpty()) {
            tags.meta().lore(lore);
        }

        // Add PDC tags
        tags.set(Keys.ITEM_ID, PersistentDataType.STRING, customItem.name());
        tags.set(Keys.ITEM_OWNER, PersistentDataType.STRING, owner.getUniqueId().toString());

        // Apply special properties based on item type
        applyCustomItemProperties(tags, customItem, item);

        tags.meta().addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        tags.apply();

        // Apply custom model data
        CustomModelDataMapper.applyCustomModel(item, customItem);

        // Apply data-component overrides (food/consumable animation, etc.) - must run after
        // setItemMeta, since data components live outside ItemMeta and would otherwise be
        // clobbered by it (see applyFoodProperties for the same ordering requirement).
        applyCustomItemDataComponents(customItem, item);

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
        if (!item.hasItemMeta()) return;

        PDCSetter tags = PDCSetter.of(item);

        // Set PDC tag
        tags.set(Keys.ITEM_ID, PersistentDataType.STRING, armor.name());

        // Set display name
        tags.meta().displayName(Messages.parse("<gold>" + armor.getDisplayName() + "</gold>"));

        // Try to get lore from configuration first
        List<Component> lore = getConfiguredLore(armor);

        // Add empty line and special armor note if we have lore
        if (!lore.isEmpty()) {
            lore = new ArrayList<>(lore);
            lore.add(Component.empty());
            lore.add(Messages.parse("<yellow>Special Armor</yellow>"));
            tags.meta().lore(lore);
        }

        // Make unbreakable and hide flags
        tags.meta().setUnbreakable(true);
        tags.meta().addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);

        tags.apply();

        CustomModelDataMapper.applyArmorModel(item, armor);

        // Equip the armor
        ItemUtils.equipArmorOrReplace(player, item);
    }

    // ==================== PRIVATE HELPER METHODS ====================

    /**
     * Applies food properties to a food item.
     */
    private void applyFoodProperties(ItemStack item, FoodItem foodItem) {
        // Only apply custom properties to special consumables
        // Vanilla food items should keep their Minecraft default food properties
        PotionEffect potionEffect;
        switch (foodItem) {
            case SPEED_CARROT -> potionEffect = new PotionEffect(PotionEffectType.SPEED, 11 * 20, 0);
            case GOLDEN_CHICKEN -> potionEffect = new PotionEffect(PotionEffectType.ABSORPTION, 11 * 20, 1);
            case COOKIE_OF_LIFE -> potionEffect = new PotionEffect(PotionEffectType.REGENERATION, 11 * 20, 0);
            case SUNSCREEN -> potionEffect = new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 11 * 20, 0);
            case CAN_OF_SPINACH -> potionEffect = new PotionEffect(PotionEffectType.STRENGTH, 11 * 20, 0);
            default -> {
                // No custom food properties needed - they have Minecraft defaults
                return;
            }
        }

        // Create new food component for custom consumable items
        item.setData(DataComponentTypes.FOOD, FoodProperties.food()
                .canAlwaysEat(true)
                .nutrition(4)
                .saturation(2.0f)
                .build());
        item.unsetData(DataComponentTypes.CONSUMABLE); // Remove default consumable behavior

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
    private void applyCustomItemProperties(PDCSetter tags, CustomItem customItem, ItemStack item) {
        switch (customItem) {
            case BAG_OF_POTATOES -> {
                if (tags.meta() instanceof Damageable damageable) {
                    damageable.setDamage(item.getType().getMaxDurability() - 3);
                }
                tags.meta().addEnchant(Enchantment.KNOCKBACK, 3, true);
            }
            case CASH_BLASTER -> tags.meta().addEnchant(Enchantment.MULTISHOT, 1, true);
            case INVIS_CLOAK -> tags.set(Keys.ITEM_USES, PersistentDataType.INTEGER, 5);
            case ICE_FAN -> {
                // Shears' vanilla max durability doesn't match the 75-point design budget, so
                // remaining durability is tracked as a PDC counter (mirrored onto the visual
                // durability bar in CustomItemManager.setIceFanDurability) rather than relying
                // on Damageable directly, like BAG_OF_POTATOES does.
                tags.set(Keys.ITEM_USES, PersistentDataType.INTEGER, ItemsConfig.getInstance().getIceFanMaxDurability());
            }
            default -> {
                // No special properties
            }
        }
    }

    /**
     * Applies data-component overrides that must be set after {@link ItemStack#setItemMeta}
     * (food/consumable animation data lives outside ItemMeta).
     */
    private void applyCustomItemDataComponents(CustomItem customItem, ItemStack item) {
        switch (customItem) {
            case RADIATING_LOTUS -> {
                // Food-eligible so right-click raises the hand, letting us poll isHandRaised()
                // to detect "hold to charge, release to activate" - the item is never actually
                // eaten (InteractListener cancels PlayerItemConsumeEvent for this item).
                item.setData(DataComponentTypes.FOOD, FoodProperties.food()
                        .canAlwaysEat(true)
                        .nutrition(0)
                        .saturation(0f)
                        .build());
                item.unsetData(DataComponentTypes.CONSUMABLE);
                item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                        .animation(ItemUseAnimation.EAT)
                        .consumeSeconds(3.5f) // > max-charge-seconds + grace-seconds so we always release first
                        .build());
            }
            case OVERDRIVE_POTION -> {
                // Vanilla potions start a "drink" sequence on right-click; strip the consumable
                // component so the interaction fires instantly through InteractListener instead.
                item.unsetData(DataComponentTypes.CONSUMABLE);
            }
            case HUNTERS_MARK -> {
                // Food-eligible so right-click raises the hand, letting us poll isHandRaised()
                // to detect "hold to mark an enemy" - the item is never actually eaten
                // (InteractListener cancels PlayerItemConsumeEvent for this item).
                item.setData(DataComponentTypes.FOOD, FoodProperties.food()
                        .canAlwaysEat(true)
                        .nutrition(0)
                        .saturation(0f)
                        .build());
                item.unsetData(DataComponentTypes.CONSUMABLE);
                item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                        .animation(ItemUseAnimation.EAT)
                        .consumeSeconds(5f) // > charge-seconds so we always finish the mark first
                        .build());
            }
            default -> {
                // No special data components
            }
        }
    }

    // ==================== LORE CONFIGURATION HELPERS ====================

    /**
     * Get configured lore for a purchasable item.
     * Uses ItemsConfig to fetch lore based on item category and configKey.
     *
     * @param purchasable The item to get lore for
     * @return List of lore components from config, or empty list if none configured
     */
    public List<Component> getConfiguredLore(Purchasable purchasable) {
        String category = getCategoryKey(purchasable);
        String configKey = purchasable.getConfigKey();

        if (category == null || configKey == null) {
            return List.of();
        }
        List<String> loreLinesRaw = ItemsConfig.getInstance().getItemLore(category, configKey);

        if (loreLinesRaw.isEmpty()) {
            return List.of();
        }

        // Parse each line with MiniMessage formatting
        return loreLinesRaw.stream()
                .map(Messages::parse)
                .toList();
    }

    /**
     * Get the configuration category key for a purchasable item.
     * Maps item types to their config categories.
     *
     * @param purchasable The item
     * @return Category key for config lookup, or null if item type not supported
     */
    private String getCategoryKey(Purchasable purchasable) {
        if (purchasable instanceof CustomArmorItem) {
            return "custom-armor";
        } else if (purchasable instanceof CustomItem) {
            return "custom-items";
        } else if (purchasable instanceof WeaponItem) {
            return "weapons";
        } else if (purchasable instanceof ArmorItem) {
            return "armor";
        } else if (purchasable instanceof FoodItem) {
            return "food";
        } else if (purchasable instanceof UtilityItem) {
            return "utility";
        }
        return null;
    }
}
