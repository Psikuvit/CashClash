package me.psikuvit.cashClash.util.items;

import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.shop.items.MythicItem;
import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps Cash Clash items to their direct model paths for the resource pack.
 */
public final class CustomModelDataMapper {
    
    private CustomModelDataMapper() {
        throw new AssertionError("Nope.");
    }
    
    // Custom Items
    private static final Map<CustomItem, NamespacedKey> CUSTOM_ITEM_MODELS = new HashMap<>();
    
    // Custom Armor
    private static final Map<CustomArmorItem, NamespacedKey> ARMOR_MODELS = new HashMap<>();
    
    // Mythic Items
    private static final Map<MythicItem, NamespacedKey> MYTHIC_MODELS = new HashMap<>();
    
    static {
        // Custom Items
        CUSTOM_ITEM_MODELS.put(CustomItem.BAG_OF_POTATOES, NamespacedKey.minecraft("item/bagofpotatoes"));
        CUSTOM_ITEM_MODELS.put(CustomItem.MEDIC_POUCH, NamespacedKey.minecraft("item/medicpouch"));
        CUSTOM_ITEM_MODELS.put(CustomItem.TABLET_OF_HACKING, NamespacedKey.minecraft("item/tabletofhacking"));
        CUSTOM_ITEM_MODELS.put(CustomItem.GRENADE, NamespacedKey.minecraft("item/grenade"));
        CUSTOM_ITEM_MODELS.put(CustomItem.SMOKE_CLOUD_GRENADE, NamespacedKey.minecraft("item/smokegrenade"));
        CUSTOM_ITEM_MODELS.put(CustomItem.BOUNCE_PAD, NamespacedKey.minecraft("item/bouncepad"));
        
        // Custom Armor
        ARMOR_MODELS.put(CustomArmorItem.MAGIC_HELMET, NamespacedKey.minecraft("item/magichat"));
        ARMOR_MODELS.put(CustomArmorItem.GUARDIANS_VEST, NamespacedKey.minecraft("item/guardian_chestplate"));
        ARMOR_MODELS.put(CustomArmorItem.TAX_EVASION_PANTS, NamespacedKey.minecraft("item/taxevasionpants"));
        ARMOR_MODELS.put(CustomArmorItem.BUNNY_SHOES, NamespacedKey.minecraft("item/bunnyboots"));
        ARMOR_MODELS.put(CustomArmorItem.DEATHMAULER_CHESTPLATE, NamespacedKey.minecraft("item/deathmaulerssetchestplate"));
        ARMOR_MODELS.put(CustomArmorItem.DEATHMAULER_LEGGINGS, NamespacedKey.minecraft("item/deathmaulersleg"));
        ARMOR_MODELS.put(CustomArmorItem.FLAMEBRINGER_BOOTS, NamespacedKey.minecraft("item/flamebrigerboots"));
        ARMOR_MODELS.put(CustomArmorItem.FLAMEBRINGER_LEGGINGS, NamespacedKey.minecraft("item/flamebrigerleggings"));
        ARMOR_MODELS.put(CustomArmorItem.INVESTORS_HELMET, NamespacedKey.minecraft("item/investorshelmet"));
        ARMOR_MODELS.put(CustomArmorItem.INVESTORS_CHESTPLATE, NamespacedKey.minecraft("item/investorschestplate"));
        ARMOR_MODELS.put(CustomArmorItem.INVESTORS_LEGGINGS, NamespacedKey.minecraft("item/investorsleggings"));
        ARMOR_MODELS.put(CustomArmorItem.INVESTORS_BOOTS, NamespacedKey.minecraft("item/investorsboots"));
        ARMOR_MODELS.put(CustomArmorItem.DRAGON_HELMET, NamespacedKey.minecraft("item/dragonhelmet"));
        ARMOR_MODELS.put(CustomArmorItem.DRAGON_CHESTPLATE, NamespacedKey.minecraft("item/dragonchestplate"));
        ARMOR_MODELS.put(CustomArmorItem.DRAGON_BOOTS, NamespacedKey.minecraft("item/dragonboots"));
        
        // Mythic Items (base states)
        MYTHIC_MODELS.put(MythicItem.ELECTRIC_EEL_SWORD, NamespacedKey.minecraft("item/electric_eel_sword"));
        MYTHIC_MODELS.put(MythicItem.CARLS_BATTLEAXE, NamespacedKey.minecraft("item/carlsbattleaxe"));
        MYTHIC_MODELS.put(MythicItem.COIN_CLEAVER, NamespacedKey.minecraft("item/coin_cleaver"));
        MYTHIC_MODELS.put(MythicItem.GOBLIN_SPEAR, NamespacedKey.minecraft("item/goblinspear"));
        MYTHIC_MODELS.put(MythicItem.WARDEN_GLOVES, NamespacedKey.minecraft("item/wardengloves"));
        MYTHIC_MODELS.put(MythicItem.WIND_BOW, NamespacedKey.minecraft("item/windbowempty"));
        MYTHIC_MODELS.put(MythicItem.BLOODWRENCH_CROSSBOW, NamespacedKey.minecraft("item/bloodwrenchempty"));
        MYTHIC_MODELS.put(MythicItem.BLAZEBITE_CROSSBOWS, NamespacedKey.minecraft("item/glaciercrossbowempty"));
    }
    
    /**
     * Gets the model path for a CustomItem.
     * @param item The custom item
     * @return The model NamespacedKey, or null if not mapped
     */
    public static NamespacedKey getItemModel(CustomItem item) {
        return CUSTOM_ITEM_MODELS.get(item);
    }
    
    /**
     * Gets the model path for a CustomArmorItem.
     * @param item The custom armor item
     * @return The model NamespacedKey, or null if not mapped
     */
    public static NamespacedKey getItemModel(CustomArmorItem item) {
        return ARMOR_MODELS.get(item);
    }
    
    /**
     * Gets the model path for a MythicItem (base state).
     * @param item The mythic item
     * @return The model NamespacedKey, or null if not mapped
     */
    public static NamespacedKey getItemModel(MythicItem item) {
        return MYTHIC_MODELS.get(item);
    }
    
    /**
     * Gets the model path for Wind Bow based on pull state.
     * @param pullAmount The pull amount (0.0 to 1.0)
     * @return The model NamespacedKey
     */
    public static NamespacedKey getWindBowModel(float pullAmount) {
        if (pullAmount == 0.0f) return NamespacedKey.minecraft("item/windbowempty");
        if (pullAmount < 0.33f) return NamespacedKey.minecraft("item/windbowpull1");
        if (pullAmount < 0.66f) return NamespacedKey.minecraft("item/windbowpull2");
        return NamespacedKey.minecraft("item/windbowpull3");
    }
    
    /**
     * Gets the model path for crossbows based on state.
     * @param mythic The crossbow mythic item
     * @param isCharged Whether the crossbow is charged
     * @param isLoaded Whether the crossbow is loaded
     * @param pullAmount The pull amount (0.0 to 1.0)
     * @return The model NamespacedKey
     */
    public static NamespacedKey getCrossbowModel(MythicItem mythic, boolean isCharged, boolean isLoaded, float pullAmount) {
        if (mythic == MythicItem.BLOODWRENCH_CROSSBOW) {
            if (isCharged) return NamespacedKey.minecraft("item/bloodwrenchcharged");
            if (isLoaded) return NamespacedKey.minecraft("item/bloodwrenchloaded");
            if (pullAmount == 0.0f) return NamespacedKey.minecraft("item/bloodwrenchempty");
            if (pullAmount < 0.33f) return NamespacedKey.minecraft("item/bloodwrenchpull1");
            if (pullAmount < 0.66f) return NamespacedKey.minecraft("item/bloodwrenchpull2");
            return NamespacedKey.minecraft("item/bloodwrenchpull3");
        }
        if (mythic == MythicItem.BLAZEBITE_CROSSBOWS) {
            // Default to glacier mode empty state - can be updated based on mode
            if (isLoaded) return NamespacedKey.minecraft("item/glaciercrossbowloaded");
            if (pullAmount == 0.0f) return NamespacedKey.minecraft("item/glaciercrossbowempty");
            if (pullAmount < 0.33f) return NamespacedKey.minecraft("item/glaciercrossbowpull1");
            if (pullAmount < 0.66f) return NamespacedKey.minecraft("item/glaciercrossbowpull2");
            return NamespacedKey.minecraft("item/glaciercrossbowpull3");
        }
        return null;
    }
}
