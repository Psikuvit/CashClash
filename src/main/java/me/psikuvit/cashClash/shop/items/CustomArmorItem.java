package me.psikuvit.cashClash.shop.items;

import me.psikuvit.cashClash.shop.ShopCategory;
import org.bukkit.Material;

/**
 * Custom armor items (Cash Clash exclusives) with special abilities.
 * This is the unified class for custom armor - handles both shop functionality and armor abilities.
 */
public enum CustomArmorItem implements Purchasable {
    // Investor's Set (progressive pricing - increases 25% per piece bought)
    INVESTORS_BOOTS(Material.IRON_BOOTS, 4250, "Investor's Boots", "Rich in luck, poor in power."),
    INVESTORS_HELMET(Material.IRON_HELMET, 4500, "Investor's Helmet", "Rich in luck, poor in power."),
    INVESTORS_LEGGINGS(Material.IRON_LEGGINGS, 4750, "Investor's Leggings", "Rich in luck, poor in power."),
    INVESTORS_CHESTPLATE(Material.IRON_CHESTPLATE, 5000, "Investor's Chestplate", "Rich in luck, poor in power."),

    // Individual pieces
    TAX_EVASION_PANTS(Material.LEATHER_LEGGINGS, 5000, "Tax Evasion Pants", "Not even the economy can catch you."),
    MAGIC_HELMET(Material.IRON_HELMET, 7500, "Magic Helmet", "Hide in plain profit."),
    BUNNY_SHOES(Material.LEATHER_BOOTS, 15000, "Bunny Shoes", "Agility is the best currency."),
    GUARDIANS_VEST(Material.DIAMOND_CHESTPLATE, 20000, "Guardian's Vest", "A second chance, bought and paid for."),

    // Flamebringer's Set
    FLAMEBRINGER_BOOTS(Material.DIAMOND_BOOTS, 15000, "Flamebringer's Boots", "Forged from the scales of a mighty dragon."),
    FLAMEBRINGER_LEGGINGS(Material.DIAMOND_LEGGINGS, 20000, "Flamebringer's Leggings", "Forged from the scales of a mighty dragon."),

    // Deathmauler's Outfit (Chestplate + Leggings as a set)
    DEATHMAULER_CHESTPLATE(Material.NETHERITE_CHESTPLATE, 25000, "Deathmauler's Chestplate", "No one waits for death to have a choice in where you may lie."),
    DEATHMAULER_LEGGINGS(Material.NETHERITE_LEGGINGS, 25000, "Deathmauler's Leggings", "No one waits for death to have a choice in where you may lie."),

    // Dragon Set (Chestplate + Boots + Helmet)
    DRAGON_CHESTPLATE(Material.DIAMOND_CHESTPLATE, 25000, "Dragon Chestplate", "The power of ancient dragons flows through this armor."),
    DRAGON_BOOTS(Material.DIAMOND_BOOTS, 25000, "Dragon Boots", "The power of ancient dragons flows through this armor."),
    DRAGON_HELMET(Material.DIAMOND_HELMET, 25000, "Dragon Helmet", "The power of ancient dragons flows through this armor.");

    private final Material material;
    private final long price;
    private final String displayName;
    private final String lore;

    CustomArmorItem(Material material, long price, String displayName, String lore) {
        this.material = material;
        this.price = price;
        this.displayName = displayName;
        this.lore = lore;
    }

    @Override
    public Material getMaterial() {
        return material;
    }

    @Override
    public ShopCategory getCategory() {
        return ShopCategory.CUSTOM_ARMOR;
    }

    @Override
    public long getPrice() {
        return price;
    }

    /**
     * Gets the base price for this armor piece.
     * Used for progressive pricing calculations (e.g., Investor's set).
     */
    public long getBasePrice() {
        return price;
    }

    @Override
    public int getInitialAmount() {
        return 1;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the short lore/flavor text for this armor piece.
     */
    public String getLore() {
        return lore;
    }

    @Override
    public String getDescription() {
        return switch (this) {
            case INVESTORS_BOOTS, INVESTORS_HELMET, INVESTORS_LEGGINGS, INVESTORS_CHESTPLATE ->
                    "<gray>Part of the Investor's set. Each piece increases money bonuses.</gray>";
            case TAX_EVASION_PANTS ->
                    "<gray>Reduces death penalty and grants a small timeout bonus for staying alive.</gray>";
            case MAGIC_HELMET ->
                    "<gray>Stand still to become invisible for a short time. Cooldown applies.</gray>";
            case BUNNY_SHOES ->
                    "<gray>Activate to gain Speed II + Jump Boost I for 15s. 25s cooldown.</gray>";
            case GUARDIANS_VEST ->
                    "<gray>Provides Resistance II when low on health (limited uses per round).</gray>";
            case FLAMEBRINGER_BOOTS, FLAMEBRINGER_LEGGINGS ->
                    "<gray>Part of the Flamebringer set: grants blue-fire effects on hit and permanent fire-resistance for boots.</gray>";
            case DEATHMAULER_CHESTPLATE, DEATHMAULER_LEGGINGS ->
                    "<gray>Chest + Leggings: Kills heal you and can grant absorption.</gray>";
            case DRAGON_BOOTS, DRAGON_CHESTPLATE, DRAGON_HELMET ->
                    "<gray>Full Dragon set: regen on hit, speed boosts and double-jump ability. Immune to knife damage/explosives.</gray>";
        };
    }

    // ==================== SET DETECTION METHODS ====================

    /**
     * Checks if this armor piece is part of the Investor's set.
     */
    public boolean isInvestorsSet() {
        return name().startsWith("INVESTORS_");
    }

    /**
     * Checks if this armor piece is part of the Flamebringer set.
     */
    public boolean isFlamebringerSet() {
        return name().startsWith("FLAMEBRINGER_");
    }

    /**
     * Checks if this armor piece is part of the Deathmauler set.
     */
    public boolean isDeathmaulerSet() {
        return name().startsWith("DEATHMAULER_");
    }

    /**
     * Checks if this armor piece is part of the Dragon set.
     */
    public boolean isDragonSet() {
        return name().startsWith("DRAGON_");
    }
}
