package me.psikuvit.cashClash.shop;

import org.bukkit.enchantments.Enchantment;

/**
 * Enchantments available in the shop
 */
public enum ShopEnchant {
    // Sword enchants
    SHARPNESS_1(Enchantment.SHARPNESS, 1, 5000, EnchantTarget.SWORD),
    SHARPNESS_2(Enchantment.SHARPNESS, 2, 10000, EnchantTarget.SWORD),
    SHARPNESS_3(Enchantment.SHARPNESS, 3, 20000, EnchantTarget.SWORD),
    SHARPNESS_4(Enchantment.SHARPNESS, 4, 30000, EnchantTarget.SWORD),

    // Axe enchants
    AXE_SHARPNESS_1(Enchantment.SHARPNESS, 1, 10000, EnchantTarget.AXE),
    AXE_SHARPNESS_2(Enchantment.SHARPNESS, 2, 15000, EnchantTarget.AXE),
    AXE_SHARPNESS_3(Enchantment.SHARPNESS, 3, 20000, EnchantTarget.AXE),
    AXE_SHARPNESS_4(Enchantment.SHARPNESS, 4, 30000, EnchantTarget.AXE),

    // Armor enchants (per piece)
    PROTECTION_1(Enchantment.PROTECTION, 1, 1875, EnchantTarget.ARMOR),
    PROTECTION_2(Enchantment.PROTECTION, 2, 3750, EnchantTarget.ARMOR),
    PROTECTION_3(Enchantment.PROTECTION, 3, 7500, EnchantTarget.ARMOR),
    PROTECTION_4(Enchantment.PROTECTION, 4, 12500, EnchantTarget.ARMOR),

    PROJECTILE_PROTECTION_1(Enchantment.PROJECTILE_PROTECTION, 1, 800, EnchantTarget.ARMOR),
    PROJECTILE_PROTECTION_2(Enchantment.PROJECTILE_PROTECTION, 2, 1200, EnchantTarget.ARMOR),
    PROJECTILE_PROTECTION_3(Enchantment.PROJECTILE_PROTECTION, 3, 1600, EnchantTarget.ARMOR),
    PROJECTILE_PROTECTION_4(Enchantment.PROJECTILE_PROTECTION, 4, 2000, EnchantTarget.ARMOR),

    // Boots enchants
    SOUL_SPEED_1(Enchantment.SOUL_SPEED, 1, 1000, EnchantTarget.BOOTS),
    SOUL_SPEED_2(Enchantment.SOUL_SPEED, 2, 4000, EnchantTarget.BOOTS),
    SOUL_SPEED_3(Enchantment.SOUL_SPEED, 3, 10000, EnchantTarget.BOOTS),

    // Weapon enchants
    KNOCKBACK_1(Enchantment.KNOCKBACK, 1, 10000, EnchantTarget.SWORD),
    KNOCKBACK_2(Enchantment.KNOCKBACK, 2, 40000, EnchantTarget.SWORD),
    FIRE_ASPECT_1(Enchantment.FIRE_ASPECT, 1, 40000, EnchantTarget.SWORD),

    // Bow enchants
    POWER_1(Enchantment.POWER, 1, 10000, EnchantTarget.BOW),
    POWER_2(Enchantment.POWER, 2, 20000, EnchantTarget.BOW),
    POWER_3(Enchantment.POWER, 3, 30000, EnchantTarget.BOW),
    POWER_4(Enchantment.POWER, 4, 40000, EnchantTarget.BOW),
    FLAME_1(Enchantment.FLAME, 1, 15000, EnchantTarget.BOW),
    PUNCH_1(Enchantment.PUNCH, 1, 20000, EnchantTarget.BOW),
    PUNCH_2(Enchantment.PUNCH, 2, 30000, EnchantTarget.BOW),

    // Crossbow enchants
    QUICK_CHARGE_1(Enchantment.QUICK_CHARGE, 1, 10000, EnchantTarget.CROSSBOW),
    QUICK_CHARGE_2(Enchantment.QUICK_CHARGE, 2, 15000, EnchantTarget.CROSSBOW),
    PIERCING_1(Enchantment.PIERCING, 1, 15000, EnchantTarget.CROSSBOW);

    private final Enchantment enchantment;
    private final int level;
    private final long price;
    private final EnchantTarget target;

    ShopEnchant(Enchantment enchantment, int level, long price, EnchantTarget target) {
        this.enchantment = enchantment;
        this.level = level;
        this.price = price;
        this.target = target;
    }

    public Enchantment getEnchantment() {
        return enchantment;
    }

    public int getLevel() {
        return level;
    }

    public long getPrice() {
        return price;
    }

    public EnchantTarget getTarget() {
        return target;
    }

    public String getDisplayName() {
        return name().charAt(0) + name().substring(1).toLowerCase().replace('_', ' ');
    }

    public enum EnchantTarget {
        SWORD, AXE, ARMOR, BOOTS, BOW, CROSSBOW
    }
}

