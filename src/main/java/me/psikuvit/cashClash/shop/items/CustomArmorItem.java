package me.psikuvit.cashClash.shop.items;

import me.psikuvit.cashClash.config.ShopConfig;
import me.psikuvit.cashClash.shop.ShopCategory;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Custom armor items (Cash Clash exclusives) with special abilities.
 * This is the unified class for custom armor - handles both shop functionality and armor abilities.
 *
 * SET ITEMS: Deathmauler, Dragon, Flamebringer, and Investors sets MUST be bought as complete sets.
 * INDIVIDUAL ITEMS: Tax Evasion Pants, Magic Helmet, Bunny Shoes, Guardian's Vest can be bought separately.
 */
public enum CustomArmorItem implements Purchasable {
    INVESTORS_HELMET(Material.IRON_HELMET, "investors-helmet", "Investor's Helmet", null),
    INVESTORS_CHESTPLATE(Material.IRON_CHESTPLATE, "investors-chestplate", "Investor's Chestplate", null),
    INVESTORS_LEGGINGS(Material.IRON_LEGGINGS, "investors-leggings", "Investor's Leggings", null),
    INVESTORS_BOOTS(Material.IRON_BOOTS, "investors-boots", "Investor's Boots", null),

    MAGIC_HELMET(Material.IRON_HELMET, "magic-helmet", "Magic Helmet", null),
    GUARDIANS_VEST(Material.DIAMOND_CHESTPLATE, "guardians-vest", "Guardian's Vest", null),
    TAX_EVASION_PANTS(Material.GOLDEN_LEGGINGS, "tax-evasion-pants", "Tax Evasion Pants", null),
    BUNNY_SHOES(Material.LEATHER_BOOTS, "bunny-shoes", "Bunny Shoes", null),

    FLAMEBRINGER_LEGGINGS(Material.DIAMOND_LEGGINGS, "flamebringer-leggings", "Flamebringer's Leggings", ArmorSet.FLAMEBRINGER),
    FLAMEBRINGER_BOOTS(Material.DIAMOND_BOOTS, "flamebringer-boots", "Flamebringer's Boots", ArmorSet.FLAMEBRINGER),

    DEATHMAULER_CHESTPLATE(Material.NETHERITE_CHESTPLATE, "deathmauler-chestplate", "Deathmauler's Chestplate", ArmorSet.DEATHMAULER),
    DEATHMAULER_LEGGINGS(Material.NETHERITE_LEGGINGS, "deathmauler-leggings", "Deathmauler's Leggings", ArmorSet.DEATHMAULER),

    DRAGON_HELMET(Material.DIAMOND_HELMET, "dragon-head", "Dragon Helmet", ArmorSet.DRAGON),
    DRAGON_CHESTPLATE(Material.DIAMOND_CHESTPLATE, "dragon-chestplate", "Dragon Chestplate", ArmorSet.DRAGON),
    DRAGON_BOOTS(Material.DIAMOND_BOOTS, "dragon-boots", "Dragon Boots", ArmorSet.DRAGON);

    private final Material material;
    private final String configKey;
    private final String displayName;
    private final ArmorSet armorSet;

    CustomArmorItem(Material material, String configKey, String displayName , ArmorSet armorSet) {
        this.material = material;
        this.configKey = configKey;
        this.displayName = displayName;
        this.armorSet = armorSet;
    }

    @Override
    public Material getMaterial() {
        return material;
    }

    @Override
    public ShopCategory getCategory() {
        return ShopCategory.ARMOR;
    }

    @Override
    public long getPrice() {
        return ShopConfig.getInstance().getCustomArmorPrice(configKey);
    }

    /**
     * Gets the base price for this armor piece.
     * Used for progressive pricing calculations (e.g., Investor's set).
     */
    public long getBasePrice() {
        return getPrice();
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
     * Gets the configuration key for this armor piece.
     */
    @Override
    public String getConfigKey() {
        return configKey;
    }

    /**
     * Gets the armor set this piece belongs to, or null if it's an individual piece.
     */
    public ArmorSet getArmorSet() {
        return armorSet;
    }

    /**
     * Checks if this armor piece requires buying the full set.
     */
    public boolean requiresFullSet() {
        return armorSet != null;
    }

    /**
     * Checks if this piece is an individual item (not part of a set).
     */
    public boolean isIndividualPiece() {
        return armorSet == null;
    }


    /**
     * Checks if this armor piece is part of the Investor's set.
     */
    public boolean isInvestorsSet() {
        return armorSet == ArmorSet.INVESTORS;
    }

    /**
     * Checks if this armor piece is part of the Flamebringer set.
     */
    public boolean isFlamebringerSet() {
        return armorSet == ArmorSet.FLAMEBRINGER;
    }

    /**
     * Checks if this armor piece is part of any armor set (not including Investor's for this purpose).
     * Used to determine if standard armor should be discarded on upgrade.
     */
    public boolean isPartOfSet() {
        return armorSet != null;
    }

    /**
     * Checks if this armor piece is part of the Deathmauler set.
     */
    public boolean isDeathmaulerSet() {
        return armorSet == ArmorSet.DEATHMAULER;
    }

    /**
     * Checks if this armor piece is part of the Dragon set.
     */
    public boolean isDragonSet() {
        return armorSet == ArmorSet.DRAGON;
    }

    /**
     * Represents armor sets that must be purchased together.
     */
    public enum ArmorSet {
        INVESTORS("Investor's Set", "All 4 pieces grant increased coin bonuses."),
        FLAMEBRINGER("Flamebringer Set", "Boots + Leggings grant fire effects."),
        DEATHMAULER("Deathmauler Set", "Chestplate + Leggings heal on kill."),
        DRAGON("Dragon Set", "Helmet + Chestplate + Boots for full dragon power.");

        private final String displayName;
        private final String description;

        ArmorSet(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        /**
         * Gets all armor pieces that belong to this set.
         */
        public List<CustomArmorItem> getPieces() {
            return Arrays.stream(CustomArmorItem.values())
                    .filter(item -> item.getArmorSet() == this)
                    .collect(Collectors.toList());
        }

        /**
         * Calculates the total price for the entire set.
         */
        public long getTotalPrice() {
            return getPieces().stream()
                    .mapToLong(CustomArmorItem::getPrice)
                    .sum();
        }
    }
}
