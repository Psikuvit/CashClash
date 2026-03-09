package me.psikuvit.cashClash.shop.items;

import me.psikuvit.cashClash.config.ShopConfig;
import me.psikuvit.cashClash.shop.ShopCategory;
import org.bukkit.Material;

/**
 * Custom crafted items in Cash Clash with unique abilities.
 * These are special items separate from armor/weapons.
 */
public enum CustomItem implements Purchasable {
    GRENADE(Material.FIRE_CHARGE, "grenade", 1, 0, "Throwable Grenade"),
    BOUNCE_PAD(Material.SLIME_BLOCK, "bounce-pad", 1, 8, "Placeable Bounce Pad"),
    MEDIC_POUCH(Material.RED_DYE, "medic-pouch", 1, 0, "Medic Pouch"),
    TABLET_OF_HACKING(Material.FILLED_MAP, "tablet-of-hacking", 1, 0, "Tablet of Hacking"),
    BAG_OF_POTATOES(Material.WOODEN_SWORD, "bag-of-potatoes", 1, 3, "Bag of Potatoes"),
    SMOKE_CLOUD_GRENADE(Material.GRAY_DYE, "smoke-grenade", 1, 0, "Smoke Cloud Grenade"),
    BOOMBOX(Material.JUKEBOX, "boombox", 1, 0, "Boombox"),
    INVIS_CLOAK(Material.PHANTOM_MEMBRANE, "invis-cloak", 1, 5, "Invisibility Cloak"),
    CASH_BLASTER(Material.CROSSBOW, "cash-blaster", 1, 1, "Cash Blaster"),
    RESPAWN_ANCHOR(Material.RESPAWN_ANCHOR, "respawn-anchor", 1, 2, "Respawn Anchor");

    private final Material material;
    private final String configKey;
    private final int initialAmount;
    private final int maxPurchase;
    private final String displayName;

    CustomItem(Material material, String configKey, int initialAmount, int maxPurchase, String displayName ) {
        this.material = material;
        this.configKey = configKey;
        this.initialAmount = initialAmount;
        this.maxPurchase = maxPurchase;
        this.displayName = displayName;
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
    public String getConfigKey() {
        return configKey;
    }

    @Override
    public int getInitialAmount() {
        return initialAmount;
    }

    @Override
    public String getDisplayName() {
        return displayName;
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
