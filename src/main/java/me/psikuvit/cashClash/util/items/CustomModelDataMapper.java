package me.psikuvit.cashClash.util.items;

import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.shop.items.FoodItem;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.util.enums.InvestmentType;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps Cash Clash items to their custom_model_data string keys for the resource pack.
 * Uses the new Paper 1.21+ custom_model_data string selector system.
 */
public final class CustomModelDataMapper {
    
    private CustomModelDataMapper() {
        throw new AssertionError("Nope.");
    }
    
    // Custom Items - Maps to the "when" string value in the resource pack
    private static final Map<CustomItem, String> CUSTOM_ITEM_KEYS = new HashMap<>();

    // Custom Armor
    private static final Map<CustomArmorItem, String> ARMOR_KEYS = new HashMap<>();

    // Mythic Items
    private static final Map<MythicItem, String> MYTHIC_KEYS = new HashMap<>();

    // Food Items with custom models
    private static final Map<FoodItem, String> FOOD_KEYS = new HashMap<>();

    // Investment Items
    private static final Map<InvestmentType, String> INVESTMENT_KEYS = new HashMap<>();

    // Legacy model paths (kept for backwards compatibility)
    private static final Map<CustomItem, NamespacedKey> CUSTOM_ITEM_MODELS = new HashMap<>();
    private static final Map<CustomArmorItem, NamespacedKey> ARMOR_MODELS = new HashMap<>();
    private static final Map<MythicItem, NamespacedKey> MYTHIC_MODELS = new HashMap<>();
    
    static {
        // ========== Custom Items - String keys matching resource pack "when" values ==========
        CUSTOM_ITEM_KEYS.put(CustomItem.GRENADE, "grenade");                      // fire_charge -> grenade
        CUSTOM_ITEM_KEYS.put(CustomItem.SMOKE_CLOUD_GRENADE, "smokegrenade");     // gray_dye -> smokegrenade
        CUSTOM_ITEM_KEYS.put(CustomItem.BAG_OF_POTATOES, "bagofpotatoes");        // wooden_sword -> bagofpotatoes
        CUSTOM_ITEM_KEYS.put(CustomItem.MEDIC_POUCH, "medicpouch");               // red_dye -> medicpouch
        CUSTOM_ITEM_KEYS.put(CustomItem.TABLET_OF_HACKING, "tabletofhacking");    // map -> tabletofhacking
        CUSTOM_ITEM_KEYS.put(CustomItem.INVIS_CLOAK, "invisibilitycloak");        // phantom_membrane -> invisibilitycloak

        // ========== Mythic Items ==========
        MYTHIC_KEYS.put(MythicItem.COIN_CLEAVER, "coincleaver");                  // diamond_axe -> coincleaver
        MYTHIC_KEYS.put(MythicItem.ELECTRIC_EEL_SWORD, "electriceelsword");       // diamond_sword -> electriceelsword
        MYTHIC_KEYS.put(MythicItem.CARLS_BATTLEAXE, "carlsbattleaxe");            // bone -> carlsbattleaxe (or netherite_axe -> carls)
        MYTHIC_KEYS.put(MythicItem.GOBLIN_SPEAR, "goblinspear");                  // trident -> goblinspear
        MYTHIC_KEYS.put(MythicItem.WARDEN_GLOVES, "wardengloves");                // netherite_sword -> wardengloves

        // ========== Food Items with custom models ==========
        FOOD_KEYS.put(FoodItem.SPEED_CARROT, "speedcarrot");                      // carrot -> speedcarrot
        FOOD_KEYS.put(FoodItem.SUNSCREEN, "sunscreen");                            // honey_bottle -> sunscreen
        FOOD_KEYS.put(FoodItem.CAN_OF_SPINACH, "spinachcan");                     // spider_eye -> spinachcan

        // ========== Investment Items ==========
        INVESTMENT_KEYS.put(InvestmentType.WALLET, "wallet");                      // paper -> wallet
        INVESTMENT_KEYS.put(InvestmentType.PURSE, "purse");                        // purple_bundle -> purse
        INVESTMENT_KEYS.put(InvestmentType.ENDER_BAG, "enderbag");                 // popped_chorus_fruit -> enderbag

        // ========== Legacy model paths (for setItemModel fallback) ==========
        CUSTOM_ITEM_MODELS.put(CustomItem.BAG_OF_POTATOES, NamespacedKey.minecraft("item/bagofpotatoes"));
        CUSTOM_ITEM_MODELS.put(CustomItem.MEDIC_POUCH, NamespacedKey.minecraft("item/medicpouch"));
        CUSTOM_ITEM_MODELS.put(CustomItem.TABLET_OF_HACKING, NamespacedKey.minecraft("item/tabletofhacking"));
        CUSTOM_ITEM_MODELS.put(CustomItem.GRENADE, NamespacedKey.minecraft("item/grenade"));
        CUSTOM_ITEM_MODELS.put(CustomItem.SMOKE_CLOUD_GRENADE, NamespacedKey.minecraft("item/smokegrenade"));
        CUSTOM_ITEM_MODELS.put(CustomItem.INVIS_CLOAK, NamespacedKey.minecraft("item/invisibilitycloak"));

        // Mythic model paths
        MYTHIC_MODELS.put(MythicItem.ELECTRIC_EEL_SWORD, NamespacedKey.minecraft("item/electriceelsword"));
        MYTHIC_MODELS.put(MythicItem.CARLS_BATTLEAXE, NamespacedKey.minecraft("item/carlsbattleaxe"));
        MYTHIC_MODELS.put(MythicItem.COIN_CLEAVER, NamespacedKey.minecraft("item/coincleaver"));
        MYTHIC_MODELS.put(MythicItem.GOBLIN_SPEAR, NamespacedKey.minecraft("item/goblinspear"));
        MYTHIC_MODELS.put(MythicItem.WARDEN_GLOVES, NamespacedKey.minecraft("item/wardengloves"));
    }
    
    /**
     * Gets the custom_model_data string key for a CustomItem.
     * @param item The custom item
     * @return The string key (e.g., "grenade"), or null if not mapped
     */
    public static String getItemKey(CustomItem item) {
        return CUSTOM_ITEM_KEYS.get(item);
    }

    /**
     * Gets the custom_model_data string key for a CustomArmorItem.
     * @param item The custom armor item
     * @return The string key, or null if not mapped
     */
    public static String getItemKey(CustomArmorItem item) {
        return ARMOR_KEYS.get(item);
    }

    /**
     * Gets the custom_model_data string key for a MythicItem.
     * @param item The mythic item
     * @return The string key, or null if not mapped
     */
    public static String getItemKey(MythicItem item) {
        return MYTHIC_KEYS.get(item);
    }

    /**
     * Gets the custom_model_data string key for a FoodItem.
     * @param item The food item
     * @return The string key, or null if not mapped
     */
    public static String getItemKey(FoodItem item) {
        return FOOD_KEYS.get(item);
    }

    /**
     * Gets the custom_model_data string key for an InvestmentType.
     * @param type The investment type
     * @return The string key, or null if not mapped
     */
    public static String getItemKey(InvestmentType type) {
        return INVESTMENT_KEYS.get(type);
    }

    /**
     * Gets the model path for a CustomItem (legacy method).
     * @param item The custom item
     * @return The model NamespacedKey, or null if not mapped
     */
    public static NamespacedKey getItemModel(CustomItem item) {
        return CUSTOM_ITEM_MODELS.get(item);
    }
    
    /**
     * Gets the model path for a CustomArmorItem (legacy method).
     * @param item The custom armor item
     * @return The model NamespacedKey, or null if not mapped
     */
    public static NamespacedKey getItemModel(CustomArmorItem item) {
        return ARMOR_MODELS.get(item);
    }
    
    /**
     * Gets the model path for a MythicItem (legacy method).
     * @param item The mythic item
     * @return The model NamespacedKey, or null if not mapped
     */
    public static NamespacedKey getItemModel(MythicItem item) {
        return MYTHIC_MODELS.get(item);
    }
    
    /**
     * Applies the custom model data string to an ItemStack for CustomItem.
     * Uses Paper 1.21+ string-based custom_model_data selector.
     * @param item The ItemStack to modify
     * @param customItem The custom item type
     */
    public static void applyCustomModel(ItemStack item, CustomItem customItem) {
        String key = getItemKey(customItem);
        if (key != null) {
            applyStringModelData(item, key);
        }
    }

    /**
     * Applies the custom model data string to an ItemStack for MythicItem.
     * @param item The ItemStack to modify
     * @param mythic The mythic item type
     */
    public static void applyCustomModel(ItemStack item, MythicItem mythic) {
        String key = getItemKey(mythic);
        if (key != null) {
            applyStringModelData(item, key);
        }
    }

    /**
     * Applies the custom model data string to an ItemStack for FoodItem.
     * @param item The ItemStack to modify
     * @param food The food item type
     */
    public static void applyCustomModel(ItemStack item, FoodItem food) {
        String key = getItemKey(food);
        if (key != null) {
            applyStringModelData(item, key);
        }
    }
    
    /**
     * Applies the custom model data string to an ItemStack for InvestmentType.
     * @param item The ItemStack to modify
     * @param investment The investment type
     */
    public static void applyCustomModel(ItemStack item, InvestmentType investment) {
        String key = getItemKey(investment);
        if (key != null) {
            applyStringModelData(item, key);
        }
    }

    /**
     * Applies a string-based custom model data value to an ItemStack.
     * This works with the Paper 1.21+ resource pack format that uses:
     * "custom_model_data": { "type": "select", "property": "custom_model_data", "cases": [...] }
     *
     * @param item The ItemStack to modify
     * @param key The string key (e.g., "grenade", "speedcarrot")
     */
    public static void applyStringModelData(ItemStack item, String key) {
        if (item == null || key == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // Use Paper's CustomModelDataComponent with strings list
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setStrings(List.of(key));
        meta.setCustomModelDataComponent(cmd);

        item.setItemMeta(meta);
    }

    /**
     * Gets the string-based custom model data from an ItemStack.
     * @param item The ItemStack to check
     * @return The first string key, or null if none set
     */
    public static String getStringModelData(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        List<String> strings = cmd.getStrings();

        if (!strings.isEmpty()) {
            return strings.getFirst();
        }
        return null;
    }

    /**
     * Special key for cash coins display item.
     */
    public static final String CASH_COINS_KEY = "cashcoins";  // sunflower -> cashcoins
}
