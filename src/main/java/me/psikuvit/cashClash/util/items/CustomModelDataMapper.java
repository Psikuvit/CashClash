package me.psikuvit.cashClash.util.items;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.Equippable;
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
    // Worn-body-model appearance, via the EQUIPPABLE component's assetId -> assets/cc/equipment/<name>.json
    private static final Map<CustomArmorItem, NamespacedKey> ARMOR_EQUIPMENT_ASSETS = new HashMap<>();
    // A handful of mythics use setItemModel() (a full item definition with charge/pull states)
    // instead of the CUSTOM_MODEL_DATA string predicate the others use.
    private static final Map<MythicItem, NamespacedKey> MYTHIC_ITEM_MODELS = new HashMap<>();
    private static final NamespacedKey PARRY_SHIELD_MODEL = new NamespacedKey("cc", "parry_shield");

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
        // assets/minecraft/items/nether_star.json       when: "totemofhaunting"
        // CUSTOM_ITEM_KEYS.put(CustomItem.TOTEM_OF_HAUNTING,   "totemofhaunting");
        // assets/minecraft/items/glow_berries.json      when: "radiatinglotus"
        // CUSTOM_ITEM_KEYS.put(CustomItem.RADIATING_LOTUS,     "radiatinglotus");
        // assets/minecraft/items/shears.json            when: "icefan"
        // CUSTOM_ITEM_KEYS.put(CustomItem.ICE_FAN,             "icefan");
        // assets/minecraft/items/potion.json            when: "overdrivepotion"
        // CUSTOM_ITEM_KEYS.put(CustomItem.OVERDRIVE_POTION,    "overdrivepotion");
        // assets/minecraft/items/feather.json           when: "huntersmark"
        // CUSTOM_ITEM_KEYS.put(CustomItem.HUNTERS_MARK,        "huntersmark");
        // assets/minecraft/items/cherry_sapling.json     when: "bloomingrose"
        // CUSTOM_ITEM_KEYS.put(CustomItem.BLOOMING_ROSE,       "bloomingrose");
        // assets/minecraft/items/magma_cream.json        when: "orbofgravitation"
        // CUSTOM_ITEM_KEYS.put(CustomItem.ORB_OF_GRAVITATION,  "orbofgravitation");
        // assets/minecraft/items/iron_sword.json         when: "soulkatana"
        // WEAPON_KEYS.put(WeaponItem.SOUL_KATANA,                "soulkatana");

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

        // Armor: setItemModel() -> assets/cc/items/<name>.json. The item_model component's key
        // is the item DEFINITION id (namespace:id -> assets/<namespace>/items/<id>.json), which is
        // flat with no "item/" segment - that segment only belongs inside a model reference
        // (assets/<namespace>/models/item/<id>.json), which is a different, later lookup.
        ARMOR_MODELS.put(CustomArmorItem.BUNNY_SHOES,            new NamespacedKey("cc", "bunny_boots"));
        ARMOR_MODELS.put(CustomArmorItem.DEATHMAULER_CHESTPLATE, new NamespacedKey("cc", "deathmaulers_chestplate"));
        ARMOR_MODELS.put(CustomArmorItem.DEATHMAULER_LEGGINGS,   new NamespacedKey("cc", "deathmaulers_leggings"));
        ARMOR_MODELS.put(CustomArmorItem.DRAGON_BOOTS,           new NamespacedKey("cc", "dragon_boots"));
        ARMOR_MODELS.put(CustomArmorItem.DRAGON_CHESTPLATE,      new NamespacedKey("cc", "dragon_chestplate"));
        ARMOR_MODELS.put(CustomArmorItem.DRAGON_HELMET,          new NamespacedKey("cc", "dragon_helmet"));
        ARMOR_MODELS.put(CustomArmorItem.FLAMEBRINGER_BOOTS,     new NamespacedKey("cc", "flamebridger_boots"));
        ARMOR_MODELS.put(CustomArmorItem.FLAMEBRINGER_LEGGINGS,  new NamespacedKey("cc", "flamebridger_leggings"));
        ARMOR_MODELS.put(CustomArmorItem.GUARDIANS_VEST,         new NamespacedKey("cc", "guardian_chestplate"));
        ARMOR_MODELS.put(CustomArmorItem.INVESTORS_BOOTS,        new NamespacedKey("cc", "investors_boots"));
        ARMOR_MODELS.put(CustomArmorItem.INVESTORS_CHESTPLATE,   new NamespacedKey("cc", "investors_chestplate"));
        ARMOR_MODELS.put(CustomArmorItem.INVESTORS_HELMET,       new NamespacedKey("cc", "investors_helmet"));
        ARMOR_MODELS.put(CustomArmorItem.INVESTORS_LEGGINGS,     new NamespacedKey("cc", "investors_leggings"));
        // Bullseye Pants is "Tax Evasion Pants" in the resource pack/lore (see CustomArmorItem's
        // class javadoc) - the code kept the old identifier name, the pack uses the new one.
        ARMOR_MODELS.put(CustomArmorItem.BULLSEYE_PANTS,         new NamespacedKey("cc", "taxevasion_leggings"));

        // Worn-armor skin: one equipment asset id per set, shared by every piece of that set
        // (assets/cc/equipment/<name>.json lists a texture per body-part layer, not per piece).
        ARMOR_EQUIPMENT_ASSETS.put(CustomArmorItem.BUNNY_SHOES,            new NamespacedKey("cc", "bunny"));
        ARMOR_EQUIPMENT_ASSETS.put(CustomArmorItem.DEATHMAULER_CHESTPLATE, new NamespacedKey("cc", "deathmaulers"));
        ARMOR_EQUIPMENT_ASSETS.put(CustomArmorItem.DEATHMAULER_LEGGINGS,   new NamespacedKey("cc", "deathmaulers"));
        ARMOR_EQUIPMENT_ASSETS.put(CustomArmorItem.DRAGON_BOOTS,           new NamespacedKey("cc", "dragon"));
        ARMOR_EQUIPMENT_ASSETS.put(CustomArmorItem.DRAGON_CHESTPLATE,      new NamespacedKey("cc", "dragon"));
        ARMOR_EQUIPMENT_ASSETS.put(CustomArmorItem.DRAGON_HELMET,          new NamespacedKey("cc", "dragon"));
        ARMOR_EQUIPMENT_ASSETS.put(CustomArmorItem.FLAMEBRINGER_BOOTS,     new NamespacedKey("cc", "flamebridger"));
        ARMOR_EQUIPMENT_ASSETS.put(CustomArmorItem.FLAMEBRINGER_LEGGINGS,  new NamespacedKey("cc", "flamebridger"));
        ARMOR_EQUIPMENT_ASSETS.put(CustomArmorItem.GUARDIANS_VEST,         new NamespacedKey("cc", "guardian"));
        ARMOR_EQUIPMENT_ASSETS.put(CustomArmorItem.INVESTORS_BOOTS,        new NamespacedKey("cc", "investors"));
        ARMOR_EQUIPMENT_ASSETS.put(CustomArmorItem.INVESTORS_CHESTPLATE,   new NamespacedKey("cc", "investors"));
        ARMOR_EQUIPMENT_ASSETS.put(CustomArmorItem.INVESTORS_HELMET,       new NamespacedKey("cc", "investors"));
        ARMOR_EQUIPMENT_ASSETS.put(CustomArmorItem.INVESTORS_LEGGINGS,     new NamespacedKey("cc", "investors"));
        ARMOR_EQUIPMENT_ASSETS.put(CustomArmorItem.BULLSEYE_PANTS,         new NamespacedKey("cc", "taxevasion"));

        // Wind Bow / BloodWrench: item definitions with pull/charge state models, not a flat
        // CUSTOM_MODEL_DATA string predicate.
        MYTHIC_ITEM_MODELS.put(MythicItem.WIND_BOW,             new NamespacedKey("cc", "wind_bow"));
        MYTHIC_ITEM_MODELS.put(MythicItem.BLOODWRENCH_CROSSBOW, new NamespacedKey("cc", "bloodwrench_standby"));
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
        if (key != null) {
            applyStringModelData(item, key);
            return;
        }
        NamespacedKey modelKey = MYTHIC_ITEM_MODELS.get(mythic);
        if (modelKey != null) applyItemModel(item, modelKey);
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
        NamespacedKey modelKey = getItemModel(armor);
        if (modelKey != null) applyItemModel(item, modelKey);

        NamespacedKey equipmentKey = ARMOR_EQUIPMENT_ASSETS.get(armor);
        if (equipmentKey != null) applyEquipmentAsset(item, equipmentKey);
    }

    public static void applyParryShieldModel(ItemStack item) {
        applyItemModel(item, PARRY_SHIELD_MODEL);
    }

    private static void applyItemModel(ItemStack item, NamespacedKey key) {
        if (item == null || key == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.setItemModel(key);
        item.setItemMeta(meta);
    }

    /**
     * Overrides just the worn-appearance asset id on the item's existing EQUIPPABLE component,
     * preserving every other vanilla-assigned field (equip sound, dispensable, swappable, ...).
     */
    private static void applyEquipmentAsset(ItemStack item, NamespacedKey assetId) {
        Equippable current = item.getData(DataComponentTypes.EQUIPPABLE);
        if (current == null) return;

        Equippable.Builder builder = Equippable.equippable(current.slot())
                .assetId(assetId)
                .dispensable(current.dispensable())
                .swappable(current.swappable())
                .damageOnHurt(current.damageOnHurt())
                .equipOnInteract(current.equipOnInteract())
                .canBeSheared(current.canBeSheared());
        if (current.equipSound() != null) builder.equipSound(current.equipSound());
        if (current.cameraOverlay() != null) builder.cameraOverlay(current.cameraOverlay());
        if (current.shearSound() != null) builder.shearSound(current.shearSound());
        if (current.allowedEntities() != null) builder.allowedEntities(current.allowedEntities());

        item.setData(DataComponentTypes.EQUIPPABLE, builder.build());
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