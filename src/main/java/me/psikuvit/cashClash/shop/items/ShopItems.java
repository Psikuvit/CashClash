package me.psikuvit.cashClash.shop.items;

import me.psikuvit.cashClash.shop.ShopCategory;
import me.psikuvit.cashClash.util.enums.InvestmentType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Registry and utility class for all shop items.
 * Provides lookup methods across all item enums.
 */
public final class ShopItems {

    private static final Map<String, Purchasable> BY_NAME;
    private static final Map<ShopCategory, List<Purchasable>> BY_CATEGORY;
    private static final List<Purchasable> ALL_ITEMS;

    static {
        BY_NAME = new HashMap<>();
        BY_CATEGORY = new EnumMap<>(ShopCategory.class);
        ALL_ITEMS = new ArrayList<>();

        // Register all items from each enum
        registerAll(WeaponItem.values());
        registerAll(ArmorItem.values());
        registerAll(CustomArmorItem.values());
        registerAll(FoodItem.values());
        registerAll(UtilityItem.values());
        registerAll(CustomItem.values());
        registerAll(MythicItem.values());
        registerAll(InvestmentType.values());
    }

    private ShopItems() {
        throw new AssertionError("Utility class");
    }

    private static void registerAll(Purchasable[] items) {
        for (Purchasable item : items) {
            BY_NAME.put(item.name(), item);
            ALL_ITEMS.add(item);
            BY_CATEGORY.computeIfAbsent(item.getCategory(), k -> new ArrayList<>()).add(item);
        }
    }

    /**
     * Gets a purchasable item by its enum name.
     *
     * @param name The name of the item (e.g., "IRON_SWORD", "MAGIC_HELMET")
     * @return The item, or null if not found
     */
    public static Purchasable valueOf(String name) {
        return BY_NAME.get(name);
    }

    /**
     * Gets all registered shop items.
     *
     * @return List of all items (unmodifiable)
     */
    public static List<Purchasable> values() {
        return Collections.unmodifiableList(ALL_ITEMS);
    }

    /**
     * Gets a WeaponItem by name, or null if not found or not a weapon.
     */
    public static WeaponItem getWeapon(String name) {
        Purchasable item = BY_NAME.get(name);
        return item instanceof WeaponItem w ? w : null;
    }

    /**
     * Gets a CustomArmorItem by name, or null if not found or not custom armor.
     */
    public static CustomArmorItem getCustomArmor(String name) {
        Purchasable item = BY_NAME.get(name);
        return item instanceof CustomArmorItem c ? c : null;
    }

    /**
     * Gets a FoodItem by name, or null if not found or not food.
     */
    public static FoodItem getFood(String name) {
        Purchasable item = BY_NAME.get(name);
        return item instanceof FoodItem f ? f : null;
    }

    /**
     * Gets a UtilityItem by name, or null if not found or not utility.
     */
    public static UtilityItem getUtility(String name) {
        Purchasable item = BY_NAME.get(name);
        return item instanceof UtilityItem u ? u : null;
    }

    /**
     * Gets a CustomItemType by name, or null if not found or not a custom item.
     */
    public static CustomItem getCustomItem(String name) {
        Purchasable item = BY_NAME.get(name);
        return item instanceof CustomItem c ? c : null;
    }

    /**
     * Gets a MythicItem by name, or null if not found or not a mythic.
     */
    public static MythicItem getMythic(String name) {
        Purchasable item = BY_NAME.get(name);
        return item instanceof MythicItem m ? m : null;
    }

    /**
     * Gets an InvestmentType by name, or null if not found or not an investment.
     */
    public static InvestmentType getInvestment(String name) {
        Purchasable item = BY_NAME.get(name);
        return item instanceof InvestmentType i ? i : null;
    }
}

