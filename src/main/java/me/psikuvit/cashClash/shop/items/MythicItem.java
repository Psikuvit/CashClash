package me.psikuvit.cashClash.shop.items;

import me.psikuvit.cashClash.shop.ShopCategory;
import org.bukkit.Material;

/**
 * Mythic (Legendary) items - one per team per game.
 * These are powerful unique weapons with special abilities.
 */
public enum MythicItem implements Purchasable {
    COIN_CLEAVER(Material.DIAMOND_AXE, 75000,
            "Coin Cleaver",
            "Diamond axe that deals +25% damage to players with more coins. " +
            "No knockback when held. Right-click for instant grenade (2 hearts, 5 blocks, costs $2000, 3s cooldown)."),

    CARLS_BATTLEAXE(Material.NETHERITE_AXE, 100000,
            "Carl's Battleaxe",
            "Fully charged hit grants Speed III + Strength I for 25s (45s cooldown). " +
            "Critical hits launch enemies into the air (10s cooldown)."),

    WIND_BOW(Material.BOW, 100000,
            "Wind Bow",
            "Right-click to boost forward (30s cooldown). " +
            "Arrow hits propel target and nearby players (3 block radius) backwards."),

    ELECTRIC_EEL_SWORD(Material.DIAMOND_SWORD, 125000,
            "Electric Eel Sword",
            "Fully charged hits chain damage to nearby enemies (0.5 hearts, 5 blocks, 1s cooldown). " +
            "Right-click to teleport 4 blocks forward (15s cooldown)."),

    GOBLIN_SPEAR(Material.TRIDENT, 125000,
            "Goblin Spear",
            "Throwable spear with Power 4 damage + Poison III for 2s. Goes through shields (15s cooldown). " +
            "Fast attack speed, +1 block range."),

    SANDSTORMER(Material.CROSSBOW, 125000,
            "Sandstormer",
            "Burst fire 3 arrows (14s cooldown). Hold charged 28s for supercharged shot: " +
            "sandstorm effect (1-3 hearts/sec) + Levitation IV for 4s."),

    WARDEN_GLOVES(Material.NETHERITE_SWORD, 150000,
            "Warden Gloves",
            "Shockwave attack with big damage/knockback (41s cooldown). " +
            "+2 block reach, Sharp III Netherite Axe damage + KB II (22s cooldown)."),

    BLAZEBITE_CROSSBOWS(Material.CROSSBOW, 150000,
            "BlazeBite Crossbows",
            "Dual crossbows with Piercing 3, Quick Charge 1. Toggle between modes (8 shots, 25s cooldown). " +
            "Glacier: Slowness I + Frostbite 3s. Volcano: Explosive fire arrows (2 hearts direct, 1 splash).");

    private final Material material;
    private final long price;
    private final String displayName;
    private final String description;

    MythicItem(Material material, long price, String displayName, String description) {
        this.material = material;
        this.price = price;
        this.displayName = displayName;
        this.description = description;
    }

    @Override
    public Material getMaterial() {
        return material;
    }

    @Override
    public ShopCategory getCategory() {
        return ShopCategory.LEGENDARIES;
    }

    @Override
    public long getPrice() {
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

    @Override
    public String getDescription() {
        return "<gold>" + description + "</gold>";
    }

    /**
     * Check if this mythic is a melee weapon.
     */
    public boolean isMelee() {
        return this == COIN_CLEAVER || this == CARLS_BATTLEAXE ||
               this == ELECTRIC_EEL_SWORD || this == GOBLIN_SPEAR || this == WARDEN_GLOVES;
    }

    /**
     * Check if this mythic is a ranged weapon.
     */
    public boolean isRanged() {
        return this == WIND_BOW || this == SANDSTORMER || this == BLAZEBITE_CROSSBOWS;
    }
}

