package me.psikuvit.cashClash.items;

import org.bukkit.Material;

/**
 * Types of custom items in Cash Clash.
 * These are special crafted items with unique abilities.
 */
public enum CustomItemType {
    // Custom Crafts
    GRENADE(Material.FIRE_CHARGE, 1500, 0,
            "Throwable Grenade",
            "Explodes after 5 seconds dealing 4 hearts",
            "in 1-4 block radius, 1 heart in 5-6 blocks."),

    BOUNCE_PAD(Material.HEAVY_WEIGHTED_PRESSURE_PLATE, 3000, 8,
            "Placeable Bounce Pad",
            "Places a pad that launches players",
            "7 blocks forward and 4 blocks up.",
            "Enemies cannot use your bounce pads."),

    MEDIC_POUCH(Material.RED_DYE, 3500, 0,
            "Medic Pouch",
            "Right-click ally: Heal 5 hearts",
            "Right-click air: Heal yourself 3 hearts",
            "Excess healing becomes absorption.",
            "10 second cooldown between uses."),

    TABLET_OF_HACKING(Material.MAP, 2500, 0,
            "Tablet of Hacking",
            "Shows enemy team's coin amounts",
            "during this shopping phase."),

    BAG_OF_POTATOES(Material.WOODEN_SWORD, 3500, 3,
            "Bag of Potatoes",
            "Knockback III wooden sword (3 durability)",
            "Hitting enemies heals you 1 heart."),

    SMOKE_CLOUD_GRENADE(Material.GRAY_DYE, 3000, 0,
            "Smoke Cloud Grenade",
            "Creates a smoke cloud for 8 seconds.",
            "Applies poison & blindness for 3s.",
            "5 block radius. Affects ALL players!"),

    BOOMBOX(Material.JUKEBOX, 2500, 0,
            "Boombox",
            "Places a jukebox that pulses knockback",
            "every 3 seconds for 12 seconds.",
            "5 block radius. Great for area denial!"),

    INVIS_CLOAK(Material.PHANTOM_MEMBRANE, 1000, 5,
            "Invisibility Cloak",
            "Right-click to toggle invisibility.",
            "Costs 100 coins per second while active.",
            "15 second cooldown. 5 uses per round."),

    CASH_BLASTER(Material.CROSSBOW, 15000, 1,
            "Cash Blaster",
            "Multishot crossbow that shoots emeralds.",
            "Earn 500 coins per successful hit!"),

    RESPAWN_ANCHOR(Material.RESPAWN_ANCHOR, 5000, 2,
            "Respawn Anchor",
            "Revive a teammate who lost all lives.",
            "Revived player gets +2 bonus hearts.",
            "Takes 10s to revive. 3s invincibility.",
            "Lasts until end of next round.",
            "Max 2 per round, not on same person.");

    private final Material material;
    private final long price;
    private final int maxPurchase;
    private final String displayName;
    private final String[] loreLines;

    CustomItemType(Material material, long price, int maxPurchase, String displayName, String... loreLines) {
        this.material = material;
        this.price = price;
        this.maxPurchase = maxPurchase;
        this.displayName = displayName;
        this.loreLines = loreLines;
    }

    public Material getMaterial() {
        return material;
    }

    public long getPrice() {
        return price;
    }

    public int getMaxPurchase() {
        return maxPurchase;
    }

    public boolean hasLimit() {
        return maxPurchase > 0;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String[] getLoreLines() {
        return loreLines;
    }
}

