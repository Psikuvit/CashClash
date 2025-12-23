package me.psikuvit.cashClash.shop.items;

import me.psikuvit.cashClash.config.ShopConfig;
import me.psikuvit.cashClash.shop.ShopCategory;
import org.bukkit.Material;

/**
 * Custom crafted items in Cash Clash with unique abilities.
 * These are special items separate from armor/weapons.
 */
public enum CustomItem implements Purchasable {
    GRENADE(Material.FIRE_CHARGE, "grenade", 1, 0,
            "Throwable Grenade",
            "Explodes after 3 seconds dealing 4 hearts in 1-4 block radius, 1 heart in 5-6 blocks."
    ),
    BOUNCE_PAD(Material.SLIME_BLOCK, "bounce-pad", 1, 8,
            "Placeable Bounce Pad",
            "Places a pad that launches players 7 blocks forward and 4 blocks up. Enemies cannot use your bounce pads."
    ),
    MEDIC_POUCH(Material.RED_DYE, "medic-pouch", 1, 0,
            "Medic Pouch",
            "Right-click ally: Heal 5 hearts. Right-click air: Heal yourself 3 hearts. Excess healing becomes absorption. 10 second cooldown."
    ),
    TABLET_OF_HACKING(Material.MAP, "tablet-of-hacking", 1, 0,
            "Tablet of Hacking",
            "Shows enemy team's coin amounts during this shopping phase."
    ),
    BAG_OF_POTATOES(Material.WOODEN_SWORD, "bag-of-potatoes", 1, 3,
            "Bag of Potatoes",
            "Knockback III wooden sword (3 durability). Hitting enemies heals you 1 heart."
    ),
    SMOKE_CLOUD_GRENADE(Material.GRAY_DYE, "smoke-grenade", 1, 0,
            "Smoke Cloud Grenade",
            "Creates a smoke cloud for 8 seconds. Applies poison & blindness for 3s. 5 block radius. Affects ALL players!"
    ),
    BOOMBOX(Material.JUKEBOX, "boombox", 1, 0,
            "Boombox",
            "Places a jukebox that pulses knockback every 3 seconds for 12 seconds. 5 block radius. Great for area denial!"
    ),
    INVIS_CLOAK(Material.PHANTOM_MEMBRANE, "invis-cloak", 1, 5,
            "Invisibility Cloak",
            "Right-click to toggle invisibility. Costs 100 coins per second while active. 15 second cooldown. 5 uses per round."
    ),
    CASH_BLASTER(Material.CROSSBOW, "cash-blaster", 1, 1,
            "Cash Blaster",
            "Multishot crossbow that shoots emeralds. Earn 500 coins per successful hit!"
    ),
    RESPAWN_ANCHOR(Material.RESPAWN_ANCHOR, "respawn-anchor", 1, 2,
            "Respawn Anchor",
            "Revive a teammate who lost all lives. Revived player gets +2 bonus hearts. Takes 10s to revive. 3s invincibility. Max 2 per round."
    );

    private final Material material;
    private final String configKey;
    private final int initialAmount;
    private final int maxPurchase;
    private final String displayName;
    private final String description;

    CustomItem(Material material, String configKey, int initialAmount, int maxPurchase, String displayName, String description) {
        this.material = material;
        this.configKey = configKey;
        this.initialAmount = initialAmount;
        this.maxPurchase = maxPurchase;
        this.displayName = displayName;
        this.description = description;
    }

    @Override
    public Material getMaterial() {
        return material;
    }

    @Override
    public ShopCategory getCategory() {
        return ShopCategory.CUSTOM_ITEMS;
    }

    @Override
    public long getPrice() {
        return ShopConfig.getInstance().getCustomItemPrice(configKey);
    }

    @Override
    public int getInitialAmount() {
        return initialAmount;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getDescription() {
        return "<gray>" + description + "</gray>";
    }

    /**
     * Gets the maximum number of this item that can be purchased per round.
     * @return Max purchase limit, or 0 if unlimited
     */
    public int getMaxPurchase() {
        return maxPurchase;
    }

    /**
     * Checks if this item has a purchase limit.
     * @return true if there's a max purchase limit
     */
    public boolean hasLimit() {
        return maxPurchase > 0;
    }
}
