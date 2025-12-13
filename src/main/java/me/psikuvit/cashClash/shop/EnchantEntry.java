package me.psikuvit.cashClash.shop;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.List;
import java.util.Map;

public enum EnchantEntry {
    SHARPNESS(Enchantment.SHARPNESS,
            "Sharpness", 
            Map.of(1, 5000L, 2, 10000L, 3, 20000L, 4, 30000L),
            Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
    ),
    PROTECTION(Enchantment.PROTECTION,
            "Protection",
            Map.of(1, 7500L, 2, 15000L, 3, 30000L, 4, 50000L),
            Material.GOLDEN_CHESTPLATE, Material.GOLDEN_HELMET, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
                    Material.IRON_CHESTPLATE, Material.IRON_HELMET, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
                    Material.DIAMOND_CHESTPLATE, Material.DIAMOND_HELMET, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
                    Material.NETHERITE_CHESTPLATE, Material.NETHERITE_HELMET, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS
    ),
    PROJECTILE_PROTECTION(Enchantment.PROJECTILE_PROTECTION,
            "Projectile Protection", 
            Map.of(1, 800L, 2, 1200L, 3, 1600L, 4, 2000L),
            Material.IRON_CHESTPLATE, Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE
    ),
    SHARPNESS_AXE(Enchantment.SHARPNESS,
            "Axe Sharpness",
            Map.of(1, 10000L, 2, 15000L, 3, 20000L, 4, 30000L),
            Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE
    ),
    KNOCKBACK(Enchantment.KNOCKBACK,
            "Knockback", 
            Map.of(1, 10000L, 2, 40000L),
            Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
    ),
    FIRE_ASPECT(Enchantment.FIRE_ASPECT, 
            "Fire Aspect", 
            Map.of(1, 40000L), 
            Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
    ),
    PIERCING(Enchantment.PIERCING,
            "Piercing",
            Map.of(1, 15000L), 
            Material.CROSSBOW
    ),
    QUICK_CHARGE(Enchantment.QUICK_CHARGE,
            "Quick Charge", 
            Map.of(1, 10000L, 2, 15000L),
            Material.CROSSBOW
    ),
    POWER(Enchantment.POWER, 
            "Power", 
            Map.of(1, 10000L, 2, 20000L, 3, 30000L, 4, 40000L),
            Material.BOW
    ),
    FLAME(Enchantment.FLAME, 
            "Flame", Map.of(1, 15000L), 
            Material.BOW
    ),
    PUNCH(Enchantment.PUNCH, 
            "Punch",
            Map.of(1, 20000L, 2, 30000L),
            Material.BOW
    ),
    SOUL_SPEED(Enchantment.SOUL_SPEED,
            "Soul Speed",
            Map.of(1, 1000L, 2, 4000L, 3, 10000L),
            Material.SOUL_SAND, Material.SOUL_SOIL
    );

    private final Enchantment enchantment;
    private final String displayName;
    private final Map<Integer, Long> levelPrices;
    private final List<Material> applicableMaterials;

    EnchantEntry(Enchantment enchantment, String displayName, Map<Integer, Long> levelPrices, Material... applicableMaterials) {
        this.enchantment = enchantment;
        this.displayName = displayName;
        this.levelPrices = levelPrices;
        this.applicableMaterials = List.of(applicableMaterials);
    }

    public Enchantment getEnchantment() {
        return enchantment;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getPriceForLevel(int level) {
        return levelPrices.getOrDefault(level, -1L);
    }

    public List<Material> getApplicableMaterials() {
        return applicableMaterials;
    }

    public int getMaxLevel() {
        return levelPrices.keySet().stream().mapToInt(Integer::intValue).max().orElse(1);
    }
}
