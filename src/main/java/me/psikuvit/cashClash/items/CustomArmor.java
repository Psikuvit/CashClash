package me.psikuvit.cashClash.items;

import org.bukkit.Material;

/**
 * Custom armor sets with special abilities
 */
public enum CustomArmor {
    // Investor's Set (progressive pricing - increases 25% per piece bought)
    INVESTORS_BOOTS(4250, Material.IRON_BOOTS, "Investor's Boots", "Rich in luck, poor in power."),
    INVESTORS_HELMET(4500, Material.IRON_HELMET, "Investor's Helmet", "Rich in luck, poor in power."),
    INVESTORS_LEGGINGS(4750, Material.IRON_LEGGINGS, "Investor's Leggings", "Rich in luck, poor in power."),
    INVESTORS_CHESTPLATE(5000, Material.IRON_CHESTPLATE, "Investor's Chestplate", "Rich in luck, poor in power."),

    // Individual pieces
    TAX_EVASION_PANTS(5000, Material.LEATHER_LEGGINGS, "Tax Evasion Pants", "Not even the economy can catch you."),
    MAGIC_HELMET(7500, Material.IRON_HELMET, "Magic Helmet", "Hide in plain profit."),
    BUNNY_SHOES(15000, Material.LEATHER_BOOTS, "Bunny Shoes", "Agility is the best currency."),
    GUARDIANS_VEST(20000, Material.DIAMOND_CHESTPLATE, "Guardian's Vest", "A second chance, bought and paid for."),

    // Flamebringer's Set
    FLAMEBRINGER_BOOTS(15000, Material.DIAMOND_BOOTS, "Flamebringer's Boots", "Forged from the scales of a mighty dragon."),
    FLAMEBRINGER_LEGGINGS(20000, Material.DIAMOND_LEGGINGS, "Flamebringer's Leggings", "Forged from the scales of a mighty dragon."),

    // Deathmauler's Outfit (Chestplate + Leggings as a set)
    DEATHMAULER_CHESTPLATE(25000, Material.NETHERITE_CHESTPLATE, "Deathmauler's Chestplate", "No one waits for death to have a choice in where you may lie."),
    DEATHMAULER_LEGGINGS(25000, Material.NETHERITE_LEGGINGS, "Deathmauler's Leggings", "No one waits for death to have a choice in where you may lie."),

    // Dragon Set (Chestplate + Boots + Helmet)
    DRAGON_CHESTPLATE(25000, Material.DIAMOND_CHESTPLATE, "Dragon Chestplate", "The power of ancient dragons flows through this armor."),
    DRAGON_BOOTS(25000, Material.DIAMOND_BOOTS, "Dragon Boots", "The power of ancient dragons flows through this armor."),
    DRAGON_HELMET(25000, Material.DIAMOND_HELMET, "Dragon Helmet", "The power of ancient dragons flows through this armor.");

    private final long basePrice;
    private final Material material;
    private final String displayName;
    private final String lore;

    CustomArmor(long basePrice, Material material, String displayName, String lore) {
        this.basePrice = basePrice;
        this.material = material;
        this.displayName = displayName;
        this.lore = lore;
    }

    public long getBasePrice() {
        return basePrice;
    }

    public Material getMaterial() {
        return material;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLore() {
        return lore;
    }

    public boolean isInvestorsSet() {
        return name().startsWith("INVESTORS_");
    }

    public boolean isFlamebringerSet() {
        return name().startsWith("FLAMEBRINGER_");
    }

    public boolean isDeathmaulerSet() {
        return name().startsWith("DEATHMAULER_");
    }

    public boolean isDragonSet() {
        return name().startsWith("DRAGON_");
    }
}
