package me.psikuvit.cashClash.shop;

import me.psikuvit.cashClash.config.ShopConfig;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public enum EnchantEntry {
    SHARPNESS(Enchantment.SHARPNESS, "sharpness", "Sharpness", 3,
            Material.SHEARS,
            Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
            Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE
    ),
    FIRE_ASPECT(Enchantment.FIRE_ASPECT, "fire-aspect", "Fire Aspect", 1,
            Material.FLINT_AND_STEEL,
            Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
    ),
    KNOCKBACK(Enchantment.KNOCKBACK, "knockback", "Knockback", 1,
            Material.FISHING_ROD,
            Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
    ),
    PUNCH(Enchantment.PUNCH, "punch", "Punch", 1,
            Material.CARROT_ON_A_STICK,
            Material.BOW
    ),
    POWER(Enchantment.POWER, "power", "Power", 2,
            Material.WARPED_FUNGUS_ON_A_STICK,
            Material.BOW
    ),
    FLAME(Enchantment.FLAME, "flame", "Flame", 1,
            Material.SHIELD,
            Material.BOW
    ),
    PROTECTION(Enchantment.PROTECTION, "protection", "Protection", 3,
            Material.WOODEN_HOE,
            Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
            Material.IRON_CHESTPLATE, Material.IRON_HELMET, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
            Material.DIAMOND_CHESTPLATE, Material.DIAMOND_HELMET, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
            Material.NETHERITE_CHESTPLATE, Material.NETHERITE_HELMET, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS
    ),
    PROJECTILE_PROTECTION(Enchantment.PROJECTILE_PROTECTION, "projectile_protection", "Projectile Protection", 2,
            Material.WOODEN_PICKAXE,
            Material.LEATHER_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
            Material.IRON_CHESTPLATE, Material.IRON_HELMET, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
            Material.DIAMOND_CHESTPLATE, Material.DIAMOND_HELMET, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
            Material.NETHERITE_CHESTPLATE, Material.NETHERITE_HELMET, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS
    ),
    PIERCING(Enchantment.PIERCING, "piercing", "Piercing", 3,
            Material.DIAMOND_HOE,
            Material.CROSSBOW
    ),

    QUICK_CHARGE(Enchantment.QUICK_CHARGE, "quick-charge", "Quick Charge", 2,
            Material.DIAMOND_PICKAXE,
            Material.CROSSBOW
    );

    private final Enchantment enchantment;
    private final String configKey;
    private final String displayName;
    private final int maxLevel;
    private final Material runeMaterial;
    private final List<Material> applicableMaterials;

    EnchantEntry(Enchantment enchantment, String configKey, String displayName, int maxLevel, Material runeMaterial, Material... applicableMaterials) {
        this.enchantment = enchantment;
        this.configKey = configKey;
        this.displayName = displayName;
        this.maxLevel = maxLevel;
        this.runeMaterial = runeMaterial;
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

    public boolean canApplyTo(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        return applicableMaterials.contains(item.getType());
    }

    public List<Material> getApplicableMaterials() {
        return applicableMaterials;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public String getConfigKey() {
        return configKey;
    }

    public Material getRuneMaterial() {
        return runeMaterial;
    }

}
