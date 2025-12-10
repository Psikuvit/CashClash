package me.psikuvit.cashClash.shop;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

/**
 * All purchasable items in the shop with prices
 */
public enum ShopItem {
    // Weapons
    IRON_SWORD(Material.IRON_SWORD, ShopCategory.WEAPONS, 1000),
    IRON_AXE(Material.IRON_AXE, ShopCategory.WEAPONS, 2000),
    DIAMOND_SWORD(Material.DIAMOND_SWORD, ShopCategory.WEAPONS, 3000),
    DIAMOND_AXE(Material.DIAMOND_AXE, ShopCategory.WEAPONS, 4000),
    MACE(Material.MACE, ShopCategory.WEAPONS, 70000),

    // Armor
    IRON_BOOTS(Material.IRON_BOOTS, ShopCategory.ARMOR, 2250),
    IRON_HELMET(Material.IRON_HELMET, ShopCategory.ARMOR, 2500),
    IRON_LEGGINGS(Material.IRON_LEGGINGS, ShopCategory.ARMOR, 2750),
    IRON_CHESTPLATE(Material.IRON_CHESTPLATE, ShopCategory.ARMOR, 3000),

    DIAMOND_BOOTS(Material.DIAMOND_BOOTS, ShopCategory.ARMOR, 2250),
    DIAMOND_HELMET(Material.DIAMOND_HELMET, ShopCategory.ARMOR, 2500),
    DIAMOND_LEGGINGS(Material.DIAMOND_LEGGINGS, ShopCategory.ARMOR, 2750),
    DIAMOND_CHESTPLATE(Material.DIAMOND_CHESTPLATE, ShopCategory.ARMOR, 3000),

    // Custom armor (Cash Clash exclusives)
    INVESTORS_BOOTS(Material.IRON_BOOTS, ShopCategory.ARMOR, 4250),
    INVESTORS_HELMET(Material.IRON_HELMET, ShopCategory.ARMOR, 4500),
    INVESTORS_LEGGINGS(Material.IRON_LEGGINGS, ShopCategory.ARMOR, 4750),
    INVESTORS_CHESTPLATE(Material.IRON_CHESTPLATE, ShopCategory.ARMOR, 5000),

    TAX_EVASION_PANTS(Material.LEATHER_CHESTPLATE, ShopCategory.ARMOR, 5000),
    GILLIE_SUIT_HAT(Material.IRON_HELMET, ShopCategory.ARMOR, 7500),
    LIGHTFOOT_SHOES(Material.LEATHER_BOOTS, ShopCategory.ARMOR, 15000),
    GUARDIANS_VEST(Material.DIAMOND_CHESTPLATE, ShopCategory.ARMOR, 20000),
    FLAMEBRINGER_BOOTS(Material.DIAMOND_BOOTS, ShopCategory.ARMOR, 15000),
    FLAMEBRINGER_LEGGINGS(Material.DIAMOND_LEGGINGS, ShopCategory.ARMOR, 20000),
    DEATHMAULER_OUTFIT(Material.NETHERITE_CHESTPLATE, ShopCategory.ARMOR, 50000),
    DRAGON_SET(Material.DIAMOND_CHESTPLATE, ShopCategory.ARMOR, 75000),

    UPGRADE_TO_NETHERITE(Material.NETHERITE_INGOT, ShopCategory.UTILITY, 15000),

    // Food
    BREAD(Material.BREAD, ShopCategory.FOOD, 10, 64),
    COOKED_MUTTON(Material.COOKED_MUTTON, ShopCategory.FOOD, 50, 64),
    STEAK(Material.COOKED_BEEF, ShopCategory.FOOD, 75, 64),
    PORKCHOP(Material.COOKED_PORKCHOP, ShopCategory.FOOD, 75, 64),
    GOLDEN_CARROT(Material.GOLDEN_CARROT, ShopCategory.FOOD, 100, 64),
    GOLDEN_APPLE(Material.GOLDEN_APPLE, ShopCategory.FOOD, 3000, 10),
    ENCHANTED_GOLDEN_APPLE(Material.ENCHANTED_GOLDEN_APPLE, ShopCategory.FOOD, 30000, 1),

    // Utility
    LAVA_BUCKET(Material.LAVA_BUCKET, ShopCategory.UTILITY, 1500, 3),
    COBWEB(Material.COBWEB, ShopCategory.UTILITY, 625, 16),
    BLOCKS(Material.COBBLESTONE, ShopCategory.UTILITY, 10, 64),
    CROSSBOW(Material.CROSSBOW, ShopCategory.UTILITY, 4500, 3),
    BOW(Material.BOW, ShopCategory.UTILITY, 4000),
    FISHING_ROD(Material.FISHING_ROD, ShopCategory.UTILITY, 1500),
    ARROWS(Material.ARROW, ShopCategory.UTILITY, 50, 64),
    ENDER_PEARL(Material.ENDER_PEARL, ShopCategory.UTILITY, 2500, 4),
    WIND_CHARGE(Material.WIND_CHARGE, ShopCategory.UTILITY, 600, 32),
    LEAVES(Material.OAK_LEAVES, ShopCategory.UTILITY, 10, 64),
    SOUL_SPEED_BLOCK(Material.SOUL_SAND, ShopCategory.UTILITY, 80, 64);

    private final Material material;
    private final ShopCategory category;
    private final long price;
    private final int maxStack;

    ShopItem(Material material, ShopCategory category, long price) {
        this(material, category, price, 1);
    }

    ShopItem(Material material, ShopCategory category, long price, int maxStack) {
        this.material = material;
        this.category = category;
        this.price = price;
        this.maxStack = maxStack;
    }

    public Material getMaterial() {
        return material;
    }

    public ShopCategory getCategory() {
        return category;
    }

    public long getPrice() {
        return price;
    }

    public int getMaxStack() {
        return maxStack;
    }

    public long getStackPrice() {
        return price * maxStack;
    }

    public String getDisplayName() {
        return name().substring(0, 1).toUpperCase() + name().substring(1).replace("_", " ");
    }

    public String getDescription() {
        return switch (this) {
            case INVESTORS_BOOTS, INVESTORS_HELMET, INVESTORS_LEGGINGS, INVESTORS_CHESTPLATE -> "<gray>Part of the Investor's set. Each piece increases money bonuses.</gray>";
            case TAX_EVASION_PANTS -> "<gray>Reduces death penalty and grants a small timeout bonus for staying alive.</gray>";
            case GILLIE_SUIT_HAT -> "<gray>Stand still to become invisible for a short time. Cooldown applies.</gray>";
            case LIGHTFOOT_SHOES -> "<gray>Activate to gain Speed II + Jump Boost I for 15s. 25s cooldown.</gray>";
            case GUARDIANS_VEST -> "<gray>Provides Resistance II when low on health (limited uses per round).</gray>";
            case FLAMEBRINGER_BOOTS, FLAMEBRINGER_LEGGINGS -> "<gray>Part of the Flamebringer set: grants blue-fire effects on hit and permanent fire-resistance for boots.</gray>";
            case DEATHMAULER_OUTFIT -> "<gray>Chest + Leggings: Kills heal you and can grant absorption.</gray>";
            case DRAGON_SET -> "<gray>Full Dragon set: regen on hit, speed boosts and double-jump ability. Immune to knife damage/explosives.</gray>";
            default -> "";
        };
    }
}
