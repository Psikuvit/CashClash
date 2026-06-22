package me.psikuvit.cashClash.util.items;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import me.psikuvit.cashClash.shop.items.CustomArmorItem;
import me.psikuvit.cashClash.shop.items.CustomItem;
import me.psikuvit.cashClash.shop.items.FoodItem;
import me.psikuvit.cashClash.shop.items.MythicItem;
import me.psikuvit.cashClash.util.enums.InvestmentType;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;

public final class CustomModelDataMapper {

    private CustomModelDataMapper() {
        throw new AssertionError("Nope.");
    }

    private static final Map<CustomItem, String> CUSTOM_ITEM_KEYS = new HashMap<>();
    private static final Map<MythicItem, String> MYTHIC_KEYS = new HashMap<>();
    private static final Map<FoodItem, String> FOOD_KEYS = new HashMap<>();
    private static final Map<InvestmentType, String> INVESTMENT_KEYS = new HashMap<>();

    // Armor uses setItemModel() pointing to assets/cc/items/<name>.json
    private static final Map<CustomArmorItem, NamespacedKey> ARMOR_MODELS = new HashMap<>();

    static {
        // assets/minecraft/items/fire_charge.json      when: "dynamite"
        CUSTOM_ITEM_KEYS.put(CustomItem.GRENADE,             "dynamite");
        // assets/minecraft/items/gray_dye.json          when: "smokegrenade"
        CUSTOM_ITEM_KEYS.put(CustomItem.SMOKE_CLOUD_GRENADE, "smokegrenade");
        // assets/minecraft/items/wooden_sword.json      when: "bagofpotatoes"
        CUSTOM_ITEM_KEYS.put(CustomItem.BAG_OF_POTATOES,     "bagofpotatoes");
        // assets/minecraft/items/red_dye.json           when: "medicpouch"
        CUSTOM_ITEM_KEYS.put(CustomItem.MEDIC_POUCH,         "medicpouch");
        // assets/minecraft/items/map.json               when: "tabletofhacking"
        CUSTOM_ITEM_KEYS.put(CustomItem.TABLET_OF_HACKING,   "tabletofhacking");
        // assets/minecraft/items/phantom_membrane.json  when: "invisibilitycloak"
        CUSTOM_ITEM_KEYS.put(CustomItem.INVIS_CLOAK,         "invisibilitycloak");

        // assets/minecraft/items/diamond_axe.json       when: "coincleaver"
        MYTHIC_KEYS.put(MythicItem.COIN_CLEAVER,       "coincleaver");
        // assets/minecraft/items/diamond_sword.json     when: "electriceelsword"
        MYTHIC_KEYS.put(MythicItem.ELECTRIC_EEL_SWORD, "electriceelsword");
        // assets/minecraft/items/bone.json              when: "carlsbattleaxe"
        MYTHIC_KEYS.put(MythicItem.CARLS_BATTLEAXE,    "carlsbattleaxe");
        // assets/minecraft/items/trident.json           when: "goblinspear"
        MYTHIC_KEYS.put(MythicItem.GOBLIN_SPEAR,       "goblinspear");
        // assets/minecraft/items/netherite_sword.json   when: "wardengloves"
        MYTHIC_KEYS.put(MythicItem.WARDEN_GLOVES,      "wardengloves");

        // assets/minecraft/items/carrot.json            when: "speedcarrot"
        FOOD_KEYS.put(FoodItem.SPEED_CARROT,       "speedcarrot");
        // assets/minecraft/items/honey_bottle.json      when: "sunscreen"
        FOOD_KEYS.put(FoodItem.SUNSCREEN,           "sunscreen");
        // assets/minecraft/items/spider_eye.json        when: "spinachcan"
        FOOD_KEYS.put(FoodItem.CAN_OF_SPINACH,      "spinachcan");
        // assets/minecraft/items/cooked_chicken.json    when: "golden_chicken"
        FOOD_KEYS.put(FoodItem.GOLDEN_CHICKEN,      "golden_chicken");
        // assets/minecraft/items/cookie.json            when: "cookieoflife"
        FOOD_KEYS.put(FoodItem.COOKIE_OF_LIFE,      "cookieoflife");

        // assets/minecraft/items/paper.json             when: "wallet"
        INVESTMENT_KEYS.put(InvestmentType.WALLET,    "wallet");
        // assets/minecraft/items/purple_bundle.json     when: "purse"
        INVESTMENT_KEYS.put(InvestmentType.PURSE,     "purse");
        // assets/minecraft/items/popped_chorus_fruit.json when: "enderbag"
        INVESTMENT_KEYS.put(InvestmentType.ENDER_BAG, "enderbag");

        // Armor: setItemModel() → assets/cc/items/<name>.json (all verified present in pack)
        ARMOR_MODELS.put(CustomArmorItem.BUNNY_SHOES,            new NamespacedKey("cc", "item/bunny_boots"));
        ARMOR_MODELS.put(CustomArmorItem.DEATHMAULER_CHESTPLATE, new NamespacedKey("cc", "item/deathmaulers_chestplate"));
        ARMOR_MODELS.put(CustomArmorItem.DEATHMAULER_LEGGINGS,   new NamespacedKey("cc", "item/deathmaulers_leggings"));
        ARMOR_MODELS.put(CustomArmorItem.DRAGON_BOOTS,           new NamespacedKey("cc", "item/dragon_boots"));
        ARMOR_MODELS.put(CustomArmorItem.DRAGON_CHESTPLATE,      new NamespacedKey("cc", "item/dragon_chestplate"));
        ARMOR_MODELS.put(CustomArmorItem.DRAGON_HELMET,          new NamespacedKey("cc", "item/dragon_helmet"));
        ARMOR_MODELS.put(CustomArmorItem.FLAMEBRINGER_BOOTS,     new NamespacedKey("cc", "item/flamebridger_boots"));
        ARMOR_MODELS.put(CustomArmorItem.FLAMEBRINGER_LEGGINGS,  new NamespacedKey("cc", "item/flamebridger_leggings"));
        ARMOR_MODELS.put(CustomArmorItem.GUARDIANS_VEST,         new NamespacedKey("cc", "item/guardian_chestplate"));
        ARMOR_MODELS.put(CustomArmorItem.INVESTORS_BOOTS,        new NamespacedKey("cc", "item/investors_boots"));
        ARMOR_MODELS.put(CustomArmorItem.INVESTORS_CHESTPLATE,   new NamespacedKey("cc", "item/investors_chestplate"));
        ARMOR_MODELS.put(CustomArmorItem.INVESTORS_HELMET,       new NamespacedKey("cc", "item/investors_helmet"));
        ARMOR_MODELS.put(CustomArmorItem.INVESTORS_LEGGINGS,     new NamespacedKey("cc", "item/investors_leggings"));
    }

    public static String getItemKey(CustomItem item) {
        return CUSTOM_ITEM_KEYS.get(item);
    }


    public static String getItemKey(MythicItem item) {
        return MYTHIC_KEYS.get(item);
    }

    public static String getItemKey(FoodItem item) {
        return FOOD_KEYS.get(item);
    }

    public static String getItemKey(InvestmentType type) {
        return INVESTMENT_KEYS.get(type);
    }

    public static NamespacedKey getItemModel(CustomArmorItem item) {
        return ARMOR_MODELS.get(item);
    }

    public static void applyCustomModel(ItemStack item, CustomItem customItem) {
        String key = getItemKey(customItem);
        if (key != null) applyStringModelData(item, key);
    }

    public static void applyCustomModel(ItemStack item, MythicItem mythic) {
        String key = getItemKey(mythic);
        if (key != null) applyStringModelData(item, key);
    }

    public static void applyCustomModel(ItemStack item, FoodItem food) {
        String key = getItemKey(food);
        if (key != null) applyStringModelData(item, key);
    }

    public static void applyCustomModel(ItemStack item, InvestmentType investment) {
        String key = getItemKey(investment);
        if (key != null) applyStringModelData(item, key);
    }

    public static void applyArmorModel(ItemStack item, CustomArmorItem armor) {
        if (item == null) return;
        NamespacedKey key = getItemModel(armor);
        if (key == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.setItemModel(key);
        item.setItemMeta(meta);
    }

    public static void applyStringModelData(ItemStack item, String key) {
        if (item == null || key == null) return;
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA,
                CustomModelData.customModelData()
                        .addString(key)
                        .build()
        );
    }

    public static final String CASH_COINS_KEY = "cashcoins";
}