package me.psikuvit.cashClash.shop;

import me.psikuvit.cashClash.config.ShopConfig;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.List;

public enum EnchantEntry {
    SHARPNESS(Enchantment.SHARPNESS, "sharpness", "Sharpness", 5,
            Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
    ),
    PROTECTION(Enchantment.PROTECTION, "protection", "Protection", 3,
            Material.LEATHER_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
            Material.IRON_CHESTPLATE, Material.IRON_HELMET, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
            Material.DIAMOND_CHESTPLATE, Material.DIAMOND_HELMET, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
            Material.NETHERITE_CHESTPLATE, Material.NETHERITE_HELMET, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS
    ),
    KNOCKBACK(Enchantment.KNOCKBACK, "knockback", "Knockback", 2,
            Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
    ),
    FIRE_ASPECT(Enchantment.FIRE_ASPECT, "fire-aspect", "Fire Aspect", 2,
            Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
    ),
    POWER(Enchantment.POWER, "power", "Power", 2, // Max power 2 (legendary bows are exception)
            Material.BOW
    ),
    FLAME(Enchantment.FLAME, "flame", "Flame", 1,
            Material.BOW
    ),
    PUNCH(Enchantment.PUNCH, "punch", "Punch", 2,
            Material.BOW
    ),
    PROJECTILE_PROTECTION(Enchantment.PROJECTILE_PROTECTION, "projectile_protection", "Projectile Protection", 4,
            Material.LEATHER_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
            Material.IRON_CHESTPLATE, Material.IRON_HELMET, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
            Material.DIAMOND_CHESTPLATE, Material.DIAMOND_HELMET, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
            Material.NETHERITE_CHESTPLATE, Material.NETHERITE_HELMET, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS
    ),
    SOUL_SPEED(Enchantment.SOUL_SPEED, "soul-speed", "Soul Speed", 3,
            Material.LEATHER_BOOTS, Material.GOLDEN_BOOTS, Material.IRON_BOOTS, Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS
    ),
    PIERCING(Enchantment.PIERCING, "piercing", "Piercing", 4,
            Material.CROSSBOW
    );

    private final Enchantment enchantment;
    private final String configKey;
    private final String displayName;
    private final int maxLevel;
    private final List<Material> applicableMaterials;

    EnchantEntry(Enchantment enchantment, String configKey, String displayName, int maxLevel, Material... applicableMaterials) {
        this.enchantment = enchantment;
        this.configKey = configKey;
        this.displayName = displayName;
        this.maxLevel = maxLevel;
        this.applicableMaterials = List.of(applicableMaterials);
    }

    public Enchantment getEnchantment() {
        return enchantment;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getPriceForLevel(int level) {
        if (level < 1 || level > maxLevel) {
            return -1L;
        }
        return ShopConfig.getInstance().getEnchantPrice(configKey, level);
    }

    public List<Material> getApplicableMaterials() {
        return applicableMaterials;
    }

    public int getMaxLevel() {
        return maxLevel;
    }
}
