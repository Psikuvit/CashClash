package me.psikuvit.cashClash.shop.items;

import me.psikuvit.cashClash.shop.ShopCategory;

import java.util.*;

/**
 * Registry and utility class for all shop items.
 * Provides lookup methods across all item enums.
 */
public final class ShopItems {

    private static final Map<String, Purchasable> BY_NAME = new HashMap<>();
    private static final Map<ShopCategory, List<Purchasable>> BY_CATEGORY = new EnumMap<>(ShopCategory.class);
    private static final List<Purchasable> ALL_ITEMS = new ArrayList<>();

    static {
        // Register all items from each enum
        registerAll(WeaponItem.values());
        registerAll(ArmorItem.values());
        registerAll(CustomArmorItem.values());
        registerAll(FoodItem.values());
        registerAll(UtilityItem.values());
        registerAll(CustomItemType.values());
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
     * Gets a purchasable item by its enum name, throwing if not found.
     *
     * @param name The name of the item
     * @return The item
     * @throws IllegalArgumentException if the item is not found
     */
    public static Purchasable valueOfOrThrow(String name) {
        Purchasable item = BY_NAME.get(name);
        if (item == null) {
            throw new IllegalArgumentException("Unknown shop item: " + name);
        }
        return item;
    }

    /**
     * Gets all items in a specific category.
     *
     * @param category The shop category
     * @return List of items in that category (unmodifiable)
     */
    public static List<Purchasable> getByCategory(ShopCategory category) {
        return Collections.unmodifiableList(BY_CATEGORY.getOrDefault(category, Collections.emptyList()));
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
     * Checks if an item with the given name exists.
     *
     * @param name The name to check
     * @return true if an item with that name exists
     */
    public static boolean exists(String name) {
        return BY_NAME.containsKey(name);
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
    public static CustomItemType getCustomItem(String name) {
        Purchasable item = BY_NAME.get(name);
        return item instanceof CustomItemType c ? c : null;
    }
}

